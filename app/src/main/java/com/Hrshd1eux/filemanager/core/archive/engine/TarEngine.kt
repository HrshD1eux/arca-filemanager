package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.z.ZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

enum class TarCompressionType(val extension: String, val displayName: String) {
    NONE(".tar", "TAR"),
    GZIP(".tar.gz", "TAR.GZ"),
    XZ(".tar.xz", "TAR.XZ"),
    BZIP2(".tar.bz2", "TAR.BZ2"),
    Z(".tar.Z", "TAR.Z");

    companion object {
        fun fromFileName(fileName: String): TarCompressionType {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> GZIP
                lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> XZ
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> BZIP2
                lower.endsWith(".tar.z") || lower.endsWith(".taz") -> Z
                lower.endsWith(".tar") -> NONE
                else -> NONE
            }
        }
    }
}

class TarEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Lists all entries inside a TAR / TAR.GZ / TAR.XZ / TAR.BZ2 archive.
     */
    suspend fun listEntries(tarFile: File): List<ArchiveEntry> = withContext(ioDispatcher) {
        if (!tarFile.exists() || !tarFile.isFile) return@withContext emptyList()

        val type = TarCompressionType.fromFileName(tarFile.name)
        val entries = mutableListOf<ArchiveEntry>()

        try {
            openTarInputStream(tarFile, type).use { tis ->
                var entry: TarArchiveEntry? = tis.nextEntry
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
                            compressedSize = 0L,
                            lastModified = entry.lastModifiedDate?.time ?: tarFile.lastModified()
                        )
                    )
                    entry = tis.nextEntry
                }
            }
        } catch (e: Throwable) {
            throw IOException("Corrupted or invalid TAR archive (${e.localizedMessage ?: "Unreadable stream"}).", e)
        }
        entries.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Extracts all files from the TAR archive into destination directory with full safety validation.
     */
    suspend fun extractAll(
        tarFile: File,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val type = TarCompressionType.fromFileName(tarFile.name)
            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            openTarInputStream(tarFile, type).use { tis ->
                var entry: TarArchiveEntry? = tis.nextEntry

                while (entry != null) {
                    coroutineContext.ensureActive()

                    // Security: Validate path traversal, NUL bytes, and Windows device names
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
                                    while (tis.read(buffer).also { len = it } != -1) {
                                        coroutineContext.ensureActive()
                                        entryBytesRead += len
                                        cumulativeBytes += len

                                        // Security: Live Decompression Bomb & Size Limit Guard
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

                    entry = tis.nextEntry
                }
            }
            extractedFilesCount
        }
    }

    /**
     * Extracts selected entries from the TAR archive with collision handling and safety validation.
     */
    suspend fun extractEntries(
        tarFile: File,
        entryPaths: Set<String>,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val type = TarCompressionType.fromFileName(tarFile.name)
            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            openTarInputStream(tarFile, type).use { tis ->
                var entry: TarArchiveEntry? = tis.nextEntry

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
                                        while (tis.read(buffer).also { len = it } != -1) {
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

                    entry = tis.nextEntry
                }
            }
            extractedFilesCount
        }
    }

    /**
     * Creates a TAR / TAR.GZ / TAR.XZ / TAR.BZ2 archive from sources.
     */
    suspend fun createTar(
        sources: List<File>,
        destinationTar: File,
        compressionType: TarCompressionType = TarCompressionType.fromFileName(destinationTar.name),
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            destinationTar.parentFile?.mkdirs()

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
                openTarOutputStream(destinationTar, compressionType).use { tos ->
                    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                    for (source in sources) {
                        coroutineContext.ensureActive()
                        if (!source.exists()) {
                            throw FileNotFoundException("Source file does not exist: ${source.absolutePath}")
                        }

                        if (source.isDirectory) {
                            val baseDir = source.parentFile ?: source
                            source.walkTopDown().forEach { file ->
                                coroutineContext.ensureActive()
                                val relPath = file.relativeTo(baseDir).path.replace('\\', '/')
                                val entry = TarArchiveEntry(file, relPath)
                                tos.putArchiveEntry(entry)

                                if (file.isFile) {
                                    FileInputStream(file).use { fis ->
                                        var len: Int
                                        while (fis.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            tos.write(buffer, 0, len)
                                            bytesProcessed += len
                                        }
                                    }
                                    filesProcessed++
                                }
                                tos.closeArchiveEntry()

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
                            val entry = TarArchiveEntry(source, source.name)
                            tos.putArchiveEntry(entry)
                            FileInputStream(source).use { fis ->
                                var len: Int
                                while (fis.read(buffer).also { len = it } != -1) {
                                    coroutineContext.ensureActive()
                                    tos.write(buffer, 0, len)
                                    bytesProcessed += len
                                }
                            }
                            tos.closeArchiveEntry()
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
            } catch (t: Throwable) {
                if (destinationTar.exists()) {
                    destinationTar.delete()
                }
                throw t
            }

            destinationTar.length()
        }
    }

    /**
     * Verifies the integrity of entries in a TAR archive.
     */
    suspend fun testArchive(
        tarFile: File,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<ArchiveIntegrityReport> = withContext(ioDispatcher) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val type = TarCompressionType.fromFileName(tarFile.name)
            val corruptedEntries = mutableListOf<String>()
            var verifiedCount = 0
            var totalEntries = 0
            val buffer = ByteArray(BUFFER_SIZE)

            openTarInputStream(tarFile, type).use { tis ->
                var entry: TarArchiveEntry? = tis.nextEntry

                while (entry != null) {
                    coroutineContext.ensureActive()
                    totalEntries++

                    try {
                        if (!entry.isDirectory) {
                            while (tis.read(buffer) != -1) {
                                // Consuming stream to verify decompressibility
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

                    entry = tis.nextEntry
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            ArchiveIntegrityReport(
                archiveName = tarFile.name,
                totalEntries = totalEntries,
                verifiedEntries = verifiedCount,
                corruptedEntries = corruptedEntries,
                elapsedMs = elapsed
            )
        }
    }

    /**
     * Computes metadata and compression metrics for the TAR file.
     */
    suspend fun getArchiveProperties(tarFile: File): ArchiveProperties = withContext(ioDispatcher) {
        if (!tarFile.exists() || !tarFile.isFile) {
            return@withContext ArchiveProperties(
                archiveName = tarFile.name,
                archivePath = tarFile.absolutePath,
                totalFiles = 0,
                totalFolders = 0,
                uncompressedSize = 0L,
                compressedSize = 0L,
                lastModified = tarFile.lastModified()
            )
        }

        val type = TarCompressionType.fromFileName(tarFile.name)
        var totalFiles = 0
        var totalFolders = 0
        var uncompressedSize = 0L

        runCatching {
            openTarInputStream(tarFile, type).use { tis ->
                var entry: TarArchiveEntry? = tis.nextEntry
                while (entry != null) {
                    if (entry.isDirectory) {
                        totalFolders++
                    } else {
                        totalFiles++
                        uncompressedSize += entry.size.coerceAtLeast(0L)
                    }
                    entry = tis.nextEntry
                }
            }
        }

        ArchiveProperties(
            archiveName = tarFile.name,
            archivePath = tarFile.absolutePath,
            totalFiles = totalFiles,
            totalFolders = totalFolders,
            uncompressedSize = uncompressedSize,
            compressedSize = tarFile.length(),
            lastModified = tarFile.lastModified(),
            formatDisplayName = "${type.displayName} Archive",
            compressionMethod = when (type) {
                TarCompressionType.GZIP -> "GZIP Deflate"
                TarCompressionType.BZIP2 -> "BZip2"
                TarCompressionType.XZ -> "XZ LZMA2"
                TarCompressionType.Z -> "Unix Compress"
                TarCompressionType.NONE -> "TAR (Uncompressed)"
            },
            isEncrypted = false,
            encryptionDetails = "None",
            isSolid = true
        )
    }

    private fun openTarInputStream(file: File, type: TarCompressionType): TarArchiveInputStream {
        val rawInput = BufferedInputStream(FileInputStream(file), BUFFER_SIZE)
        val compressedStream: InputStream = when (type) {
            TarCompressionType.NONE -> rawInput
            TarCompressionType.GZIP -> GzipCompressorInputStream(rawInput)
            TarCompressionType.XZ -> XZCompressorInputStream(rawInput)
            TarCompressionType.BZIP2 -> BZip2CompressorInputStream(rawInput)
            TarCompressionType.Z -> ZCompressorInputStream(rawInput)
        }
        return TarArchiveInputStream(compressedStream)
    }

    private fun openTarOutputStream(file: File, type: TarCompressionType): TarArchiveOutputStream {
        val rawOutput = BufferedOutputStream(FileOutputStream(file), BUFFER_SIZE)
        val compressedStream: OutputStream = when (type) {
            TarCompressionType.NONE -> rawOutput
            TarCompressionType.GZIP -> GzipCompressorOutputStream(rawOutput)
            TarCompressionType.XZ -> XZCompressorOutputStream(rawOutput)
            TarCompressionType.BZIP2 -> BZip2CompressorOutputStream(rawOutput)
            TarCompressionType.Z -> throw UnsupportedOperationException(
                "Creating Unix Compress (.tar.Z) archives is not supported. Use GZIP (.tar.gz), BZIP2 (.tar.bz2), or XZ (.tar.xz) instead."
            )
        }
        return TarArchiveOutputStream(compressedStream)
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
