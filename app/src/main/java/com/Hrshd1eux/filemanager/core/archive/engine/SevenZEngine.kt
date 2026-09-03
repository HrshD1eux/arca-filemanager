package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.verification.FileVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import kotlin.coroutines.coroutineContext

private const val BUFFER_SIZE = 8192

class SevenZEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Lists all entries inside a .7z archive with path sanitization and safety checks.
     */
    suspend fun listEntries(sevenZFile: File, password: CharArray? = null): List<ArchiveEntry> = withContext(ioDispatcher) {
        if (!sevenZFile.exists() || !sevenZFile.isFile) return@withContext emptyList()

        val entries = mutableListOf<ArchiveEntry>()
        runCatching {
            val builder = SevenZFile.builder().setFile(sevenZFile)
            if (password != null && password.isNotEmpty()) {
                builder.setPassword(password)
            }
            builder.get().use { szf ->
                var entry: SevenZArchiveEntry? = szf.nextEntry
                while (entry != null) {
                    val rawName = entry.name.removeSuffix("/").replace('\\', '/')
                    val isDir = entry.isDirectory || entry.name.endsWith("/") || entry.name.endsWith("\\")
                    val pathParts = rawName.split("/")
                    val name = pathParts.lastOrNull() ?: rawName
                    val parentPath = if (pathParts.size > 1) {
                        pathParts.dropLast(1).joinToString("/")
                    } else ""

                    entries.add(
                        ArchiveEntry(
                            path = rawName,
                            name = name,
                            parentPath = parentPath,
                            isDirectory = isDir,
                            uncompressedSize = if (isDir) 0L else entry.size.coerceAtLeast(0L),
                            compressedSize = if (isDir) 0L else entry.size.coerceAtLeast(0L),
                            lastModified = entry.lastModifiedDate?.time ?: sevenZFile.lastModified(),
                            crc = entry.crcValue
                        )
                    )
                    entry = szf.nextEntry
                }
            }
        }
        entries.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Extracts all files from the .7z archive into destination directory with full safety validation.
     */
    suspend fun extractAll(
        sevenZFile: File,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        password: CharArray? = null,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            val builder = SevenZFile.builder().setFile(sevenZFile)
            if (password != null && password.isNotEmpty()) {
                builder.setPassword(password)
            }
            builder.get().use { szf ->
                var entry: SevenZArchiveEntry? = szf.nextEntry

                while (entry != null) {
                    coroutineContext.ensureActive()

                    val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(entry.name, destinationDir)

                    if (entry.isDirectory) {
                        validatedTarget.mkdirs()
                    } else {
                        val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                        if (targetFile != null) {
                            targetFile.parentFile?.mkdirs()
                            var entryBytesRead = 0L

                            try {
                                FileOutputStream(targetFile).use { output ->
                                    var len: Int
                                    while (szf.read(buffer).also { len = it } != -1) {
                                        coroutineContext.ensureActive()
                                        entryBytesRead += len
                                        cumulativeBytes += len

                                        ArchiveSafetyGuard.validateExpansionStream(
                                            entryName = entry.name,
                                            uncompressedBytesRead = entryBytesRead,
                                            compressedEntrySize = entry.size.coerceAtLeast(1L),
                                            cumulativeUncompressedBytes = cumulativeBytes
                                        )

                                        output.write(buffer, 0, len)
                                    }
                                }
                                if (entry.lastModifiedDate != null) {
                                    targetFile.setLastModified(entry.lastModifiedDate.time)
                                }
                                if (verifyIntegrity && entry.hasCrc) {
                                    FileVerifier.verifyCrc(targetFile, entry.crcValue, ioDispatcher)
                                }
                                extractedFilesCount++
                            } catch (e: Throwable) {
                                if (targetFile.exists()) {
                                    targetFile.delete()
                                }
                                throw e
                            }
                        }
                    }

                    onProgress?.invoke(
                        OperationProgress(
                            bytesProcessed = cumulativeBytes,
                            totalBytes = cumulativeBytes,
                            filesProcessed = extractedFilesCount,
                            totalFiles = extractedFilesCount,
                            currentFileName = entry.name.substringAfterLast('/')
                        )
                    )

                    entry = szf.nextEntry
                }
            }
            extractedFilesCount
        }
    }

    /**
     * Extracts selected entries from the .7z archive with conflict handling and safety validation.
     */
    suspend fun extractEntries(
        sevenZFile: File,
        entryPaths: Set<String>,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        password: CharArray? = null,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            val builder = SevenZFile.builder().setFile(sevenZFile)
            if (password != null && password.isNotEmpty()) {
                builder.setPassword(password)
            }
            builder.get().use { szf ->
                var entry: SevenZArchiveEntry? = szf.nextEntry

                while (entry != null) {
                    coroutineContext.ensureActive()
                    val cleanPath = entry.name.removeSuffix("/").replace('\\', '/')

                    if (entryPaths.contains(cleanPath) || entryPaths.contains(entry.name)) {
                        val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(
                            entry.name.substringAfterLast('/'),
                            destinationDir
                        )

                        if (entry.isDirectory) {
                            validatedTarget.mkdirs()
                        } else {
                            val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                            if (targetFile != null) {
                                targetFile.parentFile?.mkdirs()
                                var entryBytesRead = 0L

                                try {
                                    FileOutputStream(targetFile).use { output ->
                                        var len: Int
                                        while (szf.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            entryBytesRead += len
                                            cumulativeBytes += len

                                            ArchiveSafetyGuard.validateExpansionStream(
                                                entryName = entry.name,
                                                uncompressedBytesRead = entryBytesRead,
                                                compressedEntrySize = entry.size.coerceAtLeast(1L),
                                                cumulativeUncompressedBytes = cumulativeBytes
                                            )

                                            output.write(buffer, 0, len)
                                        }
                                    }
                                    if (entry.lastModifiedDate != null) {
                                        targetFile.setLastModified(entry.lastModifiedDate.time)
                                    }
                                    if (verifyIntegrity && entry.hasCrc) {
                                        FileVerifier.verifyCrc(targetFile, entry.crcValue, ioDispatcher)
                                    }
                                    extractedFilesCount++
                                } catch (e: Throwable) {
                                    if (targetFile.exists()) {
                                        targetFile.delete()
                                    }
                                    throw e
                                }
                            }
                        }

                        onProgress?.invoke(
                            OperationProgress(
                                bytesProcessed = cumulativeBytes,
                                totalBytes = cumulativeBytes,
                                filesProcessed = extractedFilesCount,
                                totalFiles = entryPaths.size,
                                currentFileName = entry.name.substringAfterLast('/')
                            )
                        )
                    }

                    entry = szf.nextEntry
                }
            }
            extractedFilesCount
        }
    }

    /**
     * Compresses files/folders into a high-performance .7z archive using LZMA2 / LZMA compression.
     * Optionally encrypts with AES-256 using standard 7z password protection.
     */
    suspend fun create7z(
        sources: List<File>,
        destination7z: File,
        method: SevenZMethod = SevenZMethod.LZMA2,
        password: CharArray? = null,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            destination7z.parentFile?.mkdirs()

            var totalBytes = 0L
            var totalFiles = 0
            sources.forEach { source ->
                if (source.isDirectory) {
                    source.walkTopDown().forEach { f ->
                        if (!f.isDirectory) {
                            totalBytes += f.length()
                            totalFiles++
                        }
                    }
                } else if (source.isFile) {
                    totalBytes += source.length()
                    totalFiles++
                }
            }

            var bytesProcessed = 0L
            var filesProcessed = 0
            val buffer = ByteArray(BUFFER_SIZE)

            try {
                val out = if (password != null && password.isNotEmpty()) {
                    SevenZOutputFile(destination7z, password)
                } else {
                    SevenZOutputFile(destination7z)
                }

                out.use {
                    out.setContentMethods(listOf(SevenZMethodConfiguration(method)))

                    for (source in sources) {
                        coroutineContext.ensureActive()

                        if (source.isDirectory) {
                            val baseDir = source.parentFile ?: source
                            source.walkTopDown().forEach { file ->
                                coroutineContext.ensureActive()
                                val relPath = file.relativeTo(baseDir).path.replace('\\', '/')
                                val entry = out.createArchiveEntry(file, relPath)
                                out.putArchiveEntry(entry)

                                if (file.isFile) {
                                    FileInputStream(file).use { fis ->
                                        var len: Int
                                        while (fis.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            out.write(buffer, 0, len)
                                            bytesProcessed += len
                                        }
                                    }
                                    filesProcessed++
                                }
                                out.closeArchiveEntry()

                                onProgress?.invoke(
                                    OperationProgress(
                                        bytesProcessed = bytesProcessed,
                                        totalBytes = totalBytes,
                                        filesProcessed = filesProcessed,
                                        totalFiles = totalFiles,
                                        currentFileName = file.name
                                    )
                                )
                            }
                        } else if (source.isFile) {
                            val entry = out.createArchiveEntry(source, source.name)
                            out.putArchiveEntry(entry)
                            FileInputStream(source).use { fis ->
                                var len: Int
                                while (fis.read(buffer).also { len = it } != -1) {
                                    coroutineContext.ensureActive()
                                    out.write(buffer, 0, len)
                                    bytesProcessed += len
                                }
                            }
                            out.closeArchiveEntry()
                            filesProcessed++

                            onProgress?.invoke(
                                OperationProgress(
                                    bytesProcessed = bytesProcessed,
                                    totalBytes = totalBytes,
                                    filesProcessed = filesProcessed,
                                    totalFiles = totalFiles,
                                    currentFileName = source.name
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (destination7z.exists()) {
                    destination7z.delete()
                }
                throw e
            }

            destination7z.length()
        }
    }

    /**
     * Verifies the integrity of entries in a .7z archive without writing to disk.
     */
    suspend fun testArchive(
        sevenZFile: File,
        password: CharArray? = null,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<ArchiveIntegrityReport> = withContext(ioDispatcher) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val corruptedEntries = mutableListOf<String>()
            var verifiedCount = 0
            var totalEntries = 0
            val buffer = ByteArray(BUFFER_SIZE)

            val builder = SevenZFile.builder().setFile(sevenZFile)
            if (password != null && password.isNotEmpty()) {
                builder.setPassword(password)
            }
            builder.get().use { szf ->
                var entry: SevenZArchiveEntry? = szf.nextEntry

                while (entry != null) {
                    coroutineContext.ensureActive()
                    totalEntries++
                    val crc = CRC32()

                    try {
                        if (!entry.isDirectory) {
                            while (szf.read(buffer) != -1) {
                                // Consuming stream to verify decompressibility and CRC
                            }
                            verifiedCount++
                        } else {
                            verifiedCount++
                        }
                    } catch (e: Exception) {
                        corruptedEntries.add(entry.name)
                    }

                    onProgress?.invoke(
                        OperationProgress(
                            filesProcessed = verifiedCount + corruptedEntries.size,
                            totalFiles = totalEntries,
                            currentFileName = entry.name.substringAfterLast('/')
                        )
                    )

                    entry = szf.nextEntry
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            ArchiveIntegrityReport(
                archiveName = sevenZFile.name,
                totalEntries = totalEntries,
                verifiedEntries = verifiedCount,
                corruptedEntries = corruptedEntries,
                elapsedMs = elapsed
            )
        }
    }

    /**
     * Computes metadata and compression metrics for the .7z file.
     */
    suspend fun getArchiveProperties(sevenZFile: File): ArchiveProperties = withContext(ioDispatcher) {
        if (!sevenZFile.exists() || !sevenZFile.isFile) {
            return@withContext ArchiveProperties(
                archiveName = sevenZFile.name,
                archivePath = sevenZFile.absolutePath,
                totalFiles = 0,
                totalFolders = 0,
                uncompressedSize = 0L,
                compressedSize = 0L,
                lastModified = sevenZFile.lastModified()
            )
        }

        var totalFiles = 0
        var totalFolders = 0
        var uncompressedSize = 0L

        runCatching {
            SevenZFile.builder().setFile(sevenZFile).get().use { szf ->
                var entry: SevenZArchiveEntry? = szf.nextEntry
                while (entry != null) {
                    if (entry.isDirectory) {
                        totalFolders++
                    } else {
                        totalFiles++
                        uncompressedSize += entry.size.coerceAtLeast(0L)
                    }
                    entry = szf.nextEntry
                }
            }
        }

        ArchiveProperties(
            archiveName = sevenZFile.name,
            archivePath = sevenZFile.absolutePath,
            totalFiles = totalFiles,
            totalFolders = totalFolders,
            uncompressedSize = uncompressedSize,
            compressedSize = sevenZFile.length(),
            lastModified = sevenZFile.lastModified()
        )
    }

    private fun resolveConflictFile(file: File, conflictResolution: ConflictResolution): File? {
        if (!file.exists()) return file
        return when (conflictResolution) {
            ConflictResolution.OVERWRITE -> file
            ConflictResolution.SKIP -> null
            ConflictResolution.CANCEL -> throw CancellationException("Extraction cancelled on conflict with ${file.name}")
            ConflictResolution.RENAME_AUTO -> {
                val parent = file.parentFile ?: return file
                val nameWithoutExt = file.nameWithoutExtension
                val ext = file.extension
                val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
                var counter = 1
                var candidate = File(parent, "$nameWithoutExt ($counter)$dotExt")
                while (candidate.exists()) {
                    counter++
                    candidate = File(parent, "$nameWithoutExt ($counter)$dotExt")
                }
                candidate
            }
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}
