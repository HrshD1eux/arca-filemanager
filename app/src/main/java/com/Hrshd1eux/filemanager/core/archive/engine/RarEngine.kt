package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.verification.FileVerifier
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Pure Java RAR archive engine leveraging Junrar under the Alexander Roshal UnRAR license.
 *
 * LEGAL & ARCHITECTURAL BOUNDARY:
 * Due to the Roshal UnRAR licensing clause ("Neither the RAR algorithm nor its source code
 * may be used or reverse engineered to create RAR compression functionality"), ARCA strictly
 * supports RAR archive EXTRACTION, INSPECTION, and INTEGRITY TESTING only.
 *
 * Creation or modification of RAR archives is legally barred in open source and will throw
 * [UnsupportedOperationException].
 */
class RarEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Lists all entries inside a .rar archive with path sanitization and safety checks.
     */
    suspend fun listEntries(rarFile: File, password: CharArray? = null): List<ArchiveEntry> = withContext(ioDispatcher) {
        if (!rarFile.exists() || !rarFile.isFile) return@withContext emptyList()

        val entries = mutableListOf<ArchiveEntry>()
        val passwordStr = password?.let { String(it) }

        runCatching {
            val archive = if (passwordStr != null) Archive(rarFile, passwordStr) else Archive(rarFile)
            archive.use { arc ->
                val fileHeaders = arc.fileHeaders
                ArchiveSafetyGuard.validateArchiveHeader(fileHeaders.size)

                for (header in fileHeaders) {
                    val rawName = header.fileName.removeSuffix("/").removeSuffix("\\").replace('\\', '/')
                    val isDir = header.isDirectory || rawName.endsWith('/')

                    val pathParts = rawName.split('/')
                    val name = pathParts.lastOrNull()?.ifEmpty { rawName } ?: rawName
                    val parentPath = if (pathParts.size > 1) {
                        pathParts.dropLast(1).joinToString("/")
                    } else ""

                    val uncompressedSize = if (isDir) 0L else header.fullUnpackSize.coerceAtLeast(0L)
                    val compressedSize = if (isDir) 0L else header.fullPackSize.coerceAtLeast(0L)
                    val crcValue = header.fileCRC.toLong() and 0xFFFFFFFFL

                    entries.add(
                        ArchiveEntry(
                            path = rawName,
                            name = name,
                            parentPath = parentPath,
                            isDirectory = isDir,
                            uncompressedSize = uncompressedSize,
                            compressedSize = compressedSize,
                            lastModified = header.mTime?.time ?: rarFile.lastModified(),
                            crc = crcValue
                        )
                    )
                }
            }
        }
        entries.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Extracts all files from the .rar archive into destination directory with full safety validation.
     */
    suspend fun extractAll(
        rarFile: File,
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

            val passwordStr = password?.let { String(it) }
            val archive = if (passwordStr != null) Archive(rarFile, passwordStr) else Archive(rarFile)

            var extractedFilesCount = 0
            var cumulativeBytes = 0L

            archive.use { arc ->
                val fileHeaders = arc.fileHeaders
                ArchiveSafetyGuard.validateArchiveHeader(fileHeaders.size)

                for (header in fileHeaders) {
                    coroutineContext.ensureActive()

                    val cleanPath = header.fileName.removeSuffix("/").removeSuffix("\\").replace('\\', '/')
                    val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(cleanPath, destinationDir)

                    if (header.isDirectory) {
                        validatedTarget.mkdirs()
                    } else {
                        val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                        if (targetFile != null) {
                            targetFile.parentFile?.mkdirs()
                            var entryBytesWritten = 0L

                            try {
                                FileOutputStream(targetFile).use { fos ->
                                    val monitoredOutput = object : OutputStream() {
                                        override fun write(b: Int) {
                                            fos.write(b)
                                            entryBytesWritten += 1
                                            cumulativeBytes += 1
                                            ArchiveSafetyGuard.validateExpansionStream(
                                                entryName = header.fileName,
                                                uncompressedBytesRead = entryBytesWritten,
                                                compressedEntrySize = header.fullPackSize.coerceAtLeast(1L),
                                                cumulativeUncompressedBytes = cumulativeBytes
                                            )
                                        }

                                        override fun write(b: ByteArray, off: Int, len: Int) {
                                            fos.write(b, off, len)
                                            entryBytesWritten += len
                                            cumulativeBytes += len
                                            ArchiveSafetyGuard.validateExpansionStream(
                                                entryName = header.fileName,
                                                uncompressedBytesRead = entryBytesWritten,
                                                compressedEntrySize = header.fullPackSize.coerceAtLeast(1L),
                                                cumulativeUncompressedBytes = cumulativeBytes
                                            )
                                        }

                                        override fun flush() = fos.flush()
                                        override fun close() = fos.close()
                                    }

                                    arc.extractFile(header, monitoredOutput)
                                }

                                if (header.mTime != null) {
                                    targetFile.setLastModified(header.mTime.time)
                                }

                                if (verifyIntegrity && header.fileCRC != 0) {
                                    FileVerifier.verifyCrc(
                                        file = targetFile,
                                        expectedCrc = header.fileCRC.toLong() and 0xFFFFFFFFL,
                                        ioDispatcher = ioDispatcher
                                    )
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
                            totalFiles = fileHeaders.size,
                            currentFileName = header.fileName.substringAfterLast('/').substringAfterLast('\\')
                        )
                    )
                }
            }

            extractedFilesCount
        }
    }

    /**
     * Extracts selected entries from the .rar archive with conflict handling and safety validation.
     */
    suspend fun extractEntries(
        rarFile: File,
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

            val passwordStr = password?.let { String(it) }
            val archive = if (passwordStr != null) Archive(rarFile, passwordStr) else Archive(rarFile)

            var extractedFilesCount = 0
            var cumulativeBytes = 0L

            archive.use { arc ->
                val fileHeaders = arc.fileHeaders
                ArchiveSafetyGuard.validateArchiveHeader(fileHeaders.size)

                for (header in fileHeaders) {
                    coroutineContext.ensureActive()

                    val cleanPath = header.fileName.removeSuffix("/").removeSuffix("\\").replace('\\', '/')
                    val rawName = header.fileName

                    if (entryPaths.contains(cleanPath) || entryPaths.contains(rawName)) {
                        val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(
                            cleanPath.substringAfterLast('/'),
                            destinationDir
                        )

                        if (header.isDirectory) {
                            validatedTarget.mkdirs()
                        } else {
                            val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                            if (targetFile != null) {
                                targetFile.parentFile?.mkdirs()
                                var entryBytesWritten = 0L

                                try {
                                    FileOutputStream(targetFile).use { fos ->
                                        val monitoredOutput = object : OutputStream() {
                                            override fun write(b: Int) {
                                                fos.write(b)
                                                entryBytesWritten += 1
                                                cumulativeBytes += 1
                                                ArchiveSafetyGuard.validateExpansionStream(
                                                    entryName = header.fileName,
                                                    uncompressedBytesRead = entryBytesWritten,
                                                    compressedEntrySize = header.fullPackSize.coerceAtLeast(1L),
                                                    cumulativeUncompressedBytes = cumulativeBytes
                                                )
                                            }

                                            override fun write(b: ByteArray, off: Int, len: Int) {
                                                fos.write(b, off, len)
                                                entryBytesWritten += len
                                                cumulativeBytes += len
                                                ArchiveSafetyGuard.validateExpansionStream(
                                                    entryName = header.fileName,
                                                    uncompressedBytesRead = entryBytesWritten,
                                                    compressedEntrySize = header.fullPackSize.coerceAtLeast(1L),
                                                    cumulativeUncompressedBytes = cumulativeBytes
                                                )
                                            }

                                            override fun flush() = fos.flush()
                                            override fun close() = fos.close()
                                        }

                                        arc.extractFile(header, monitoredOutput)
                                    }

                                    if (header.mTime != null) {
                                        targetFile.setLastModified(header.mTime.time)
                                    }

                                    if (verifyIntegrity && header.fileCRC != 0) {
                                        FileVerifier.verifyCrc(
                                            file = targetFile,
                                            expectedCrc = header.fileCRC.toLong() and 0xFFFFFFFFL,
                                            ioDispatcher = ioDispatcher
                                        )
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
                                currentFileName = cleanPath.substringAfterLast('/')
                            )
                        )
                    }
                }
            }

            extractedFilesCount
        }
    }

    /**
     * Verifies the integrity of entries in a .rar archive by decompressing each file into a null sink
     * and verifying header CRC and structure without writing to disk.
     */
    suspend fun testArchive(
        rarFile: File,
        password: CharArray? = null,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<ArchiveIntegrityReport> = withContext(ioDispatcher) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val corruptedEntries = mutableListOf<String>()
            var verifiedCount = 0

            val passwordStr = password?.let { String(it) }
            val archive = if (passwordStr != null) Archive(rarFile, passwordStr) else Archive(rarFile)

            val nullOutputStream = object : OutputStream() {
                override fun write(b: Int) {}
                override fun write(b: ByteArray, off: Int, len: Int) {}
            }

            val totalEntries: Int

            archive.use { arc ->
                val fileHeaders = arc.fileHeaders
                totalEntries = fileHeaders.size
                ArchiveSafetyGuard.validateArchiveHeader(totalEntries)

                for (header in fileHeaders) {
                    coroutineContext.ensureActive()

                    try {
                        if (!header.isDirectory) {
                            arc.extractFile(header, nullOutputStream)
                        }
                        verifiedCount++
                    } catch (e: Exception) {
                        corruptedEntries.add(header.fileName)
                    }

                    onProgress?.invoke(
                        OperationProgress(
                            filesProcessed = verifiedCount + corruptedEntries.size,
                            totalFiles = totalEntries,
                            currentFileName = header.fileName.substringAfterLast('/').substringAfterLast('\\')
                        )
                    )
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            ArchiveIntegrityReport(
                archiveName = rarFile.name,
                totalEntries = totalEntries,
                verifiedEntries = verifiedCount,
                corruptedEntries = corruptedEntries,
                elapsedMs = elapsed
            )
        }
    }

    /**
     * Computes metadata and metrics for the .rar file.
     */
    suspend fun getArchiveProperties(rarFile: File, password: CharArray? = null): ArchiveProperties = withContext(ioDispatcher) {
        if (!rarFile.exists() || !rarFile.isFile) {
            return@withContext ArchiveProperties(
                archiveName = rarFile.name,
                archivePath = rarFile.absolutePath,
                totalFiles = 0,
                totalFolders = 0,
                uncompressedSize = 0L,
                compressedSize = 0L,
                lastModified = rarFile.lastModified()
            )
        }

        var totalFiles = 0
        var totalFolders = 0
        var uncompressedSize = 0L

        runCatching {
            val passwordStr = password?.let { String(it) }
            val archive = if (passwordStr != null) Archive(rarFile, passwordStr) else Archive(rarFile)
            archive.use { arc ->
                for (header in arc.fileHeaders) {
                    if (header.isDirectory) {
                        totalFolders++
                    } else {
                        totalFiles++
                        uncompressedSize += header.fullUnpackSize.coerceAtLeast(0L)
                    }
                }
            }
        }

        ArchiveProperties(
            archiveName = rarFile.name,
            archivePath = rarFile.absolutePath,
            totalFiles = totalFiles,
            totalFolders = totalFolders,
            uncompressedSize = uncompressedSize,
            compressedSize = rarFile.length(),
            lastModified = rarFile.lastModified()
        )
    }

    /**
     * Explicit refusal to create RAR archives, adhering strictly to the Roshal UnRAR license.
     */
    fun createRar(sources: List<File>, destinationRar: File): Nothing {
        throw UnsupportedOperationException(
            "RAR archive creation is legally and technically unsupported. Under the Roshal UnRAR license, " +
            "creating RAR-compatible archives without proprietary WinRAR binaries is prohibited. " +
            "ARCA supports ZIP, 7Z, and TAR compression, and RAR extraction/inspection only."
        )
    }

    private fun resolveConflictFile(file: File, conflictResolution: ConflictResolution): File? {
        if (!file.exists()) return file
        return when (conflictResolution) {
            ConflictResolution.OVERWRITE -> file
            ConflictResolution.SKIP -> null
            ConflictResolution.CANCEL -> throw CancellationException("Extraction cancelled on conflict with ")
            ConflictResolution.RENAME_AUTO -> {
                val parent = file.parentFile ?: return file
                val nameWithoutExt = file.nameWithoutExtension
                val ext = file.extension
                val dotExt = if (ext.isNotEmpty()) "." else ""
                var counter = 1
                var candidate = File(parent, " ()")
                while (candidate.exists()) {
                    counter++
                    candidate = File(parent, " ()")
                }
                candidate
            }
        }
    }
}
