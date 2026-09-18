package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.palantir.isofilereader.isofilereader.GenericInternalIsoFile
import com.palantir.isofilereader.isofilereader.IsoFileReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

private const val BUFFER_SIZE = 64 * 1024

/**
 * Pure Java ISO disc image engine supporting ISO-9660 (ECMA-119, Joliet, Rock Ridge)
 * and UDF (ECMA-167) optical media disc images.
 *
 * ARCHITECTURAL & TECHNICAL BOUNDARY:
 * User-space block loop mounting (mount -t iso9660 -o loop) requires Linux kernel loop devices
 * and root privileges (CAP_SYS_ADMIN), which are strictly prohibited under the standard Android
 * SELinux application sandbox. ARCA will never falsely promise ISO mounting; it provides robust,
 * in-place inspection, browsing, and file extraction.
 */
class IsoEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Lists all entries inside an ISO optical disc image with in-place hierarchy resolution.
     */
    suspend fun listEntries(isoFile: File): List<ArchiveEntry> = withContext(ioDispatcher) {
        if (!isoFile.exists() || !isoFile.isFile) return@withContext emptyList()

        val entries = mutableListOf<ArchiveEntry>()
        runCatching {
            IsoFileReader(isoFile).use { reader ->
                val allFiles = reader.allFiles
                val flatList = reader.convertTreeFilesToFlatList(allFiles)
                ArchiveSafetyGuard.validateArchiveHeader(flatList.size)

                for (isoItem in flatList) {
                    val rawPath = isoItem.getFullFileName('/').trimStart('/').removeSuffix("/")
                    if (rawPath.isEmpty()) continue

                    val isDir = isoItem.isDirectory
                    val pathParts = rawPath.split('/')
                    val name = pathParts.lastOrNull()?.ifEmpty { rawPath } ?: rawPath
                    val parentPath = if (pathParts.size > 1) {
                        pathParts.dropLast(1).joinToString("/")
                    } else ""

                    val size = if (isDir) 0L else isoItem.size.coerceAtLeast(0L)
                    val lastModified = isoItem.dateAsDate.orElse(null)?.time ?: isoFile.lastModified()

                    entries.add(
                        ArchiveEntry(
                            path = rawPath,
                            name = name,
                            parentPath = parentPath,
                            isDirectory = isDir,
                            uncompressedSize = size,
                            compressedSize = size, // Disc image is uncompressed raw sectors
                            lastModified = lastModified,
                            crc = 0L
                        )
                    )
                }
            }
        }
        entries.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Extracts all files from the ISO disc image into the destination directory with full safety validation.
     */
    suspend fun extractAll(
        isoFile: File,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
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

            IsoFileReader(isoFile).use { reader ->
                val allFiles = reader.allFiles
                val flatList = reader.convertTreeFilesToFlatList(allFiles)
                ArchiveSafetyGuard.validateArchiveHeader(flatList.size)

                for (isoItem in flatList) {
                    coroutineContext.ensureActive()

                    val rawPath = isoItem.getFullFileName('/').trimStart('/').removeSuffix("/")
                    if (rawPath.isEmpty()) continue

                    val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(rawPath, destinationDir)

                    if (isoItem.isDirectory) {
                        validatedTarget.mkdirs()
                    } else {
                        val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                        if (targetFile != null) {
                            targetFile.parentFile?.mkdirs()
                            var entryBytesWritten = 0L

                            try {
                                reader.getFileStream(isoItem).use { inputStream ->
                                    FileOutputStream(targetFile).use { output ->
                                        var len: Int
                                        while (inputStream.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            entryBytesWritten += len
                                            cumulativeBytes += len

                                            ArchiveSafetyGuard.validateExpansionStream(
                                                entryName = rawPath,
                                                uncompressedBytesRead = entryBytesWritten,
                                                compressedEntrySize = isoItem.size.coerceAtLeast(1L),
                                                cumulativeUncompressedBytes = cumulativeBytes
                                            )

                                            output.write(buffer, 0, len)
                                        }
                                    }
                                }

                                isoItem.dateAsDate.ifPresent { date ->
                                    targetFile.setLastModified(date.time)
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
                            totalFiles = flatList.count { !it.isDirectory },
                            currentFileName = rawPath.substringAfterLast('/')
                        )
                    )
                }
            }

            extractedFilesCount
        }
    }

    /**
     * Extracts selected entries from the ISO disc image with conflict handling and safety validation.
     */
    suspend fun extractEntries(
        isoFile: File,
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

            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            IsoFileReader(isoFile).use { reader ->
                val allFiles = reader.allFiles
                val flatList = reader.convertTreeFilesToFlatList(allFiles)
                ArchiveSafetyGuard.validateArchiveHeader(flatList.size)

                for (isoItem in flatList) {
                    coroutineContext.ensureActive()

                    val rawPath = isoItem.getFullFileName('/').trimStart('/').removeSuffix("/")
                    if (rawPath.isEmpty()) continue

                    val itemFileName = isoItem.fileName ?: rawPath.substringAfterLast('/')

                    if (entryPaths.contains(rawPath) || entryPaths.contains(itemFileName)) {
                        val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(
                            rawPath.substringAfterLast('/'),
                            destinationDir
                        )

                        if (isoItem.isDirectory) {
                            validatedTarget.mkdirs()
                        } else {
                            val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                            if (targetFile != null) {
                                targetFile.parentFile?.mkdirs()
                                var entryBytesWritten = 0L

                                try {
                                    reader.getFileStream(isoItem).use { inputStream ->
                                        FileOutputStream(targetFile).use { output ->
                                            var len: Int
                                            while (inputStream.read(buffer).also { len = it } != -1) {
                                                coroutineContext.ensureActive()
                                                entryBytesWritten += len
                                                cumulativeBytes += len

                                                ArchiveSafetyGuard.validateExpansionStream(
                                                    entryName = rawPath,
                                                    uncompressedBytesRead = entryBytesWritten,
                                                    compressedEntrySize = isoItem.size.coerceAtLeast(1L),
                                                    cumulativeUncompressedBytes = cumulativeBytes
                                                )

                                                output.write(buffer, 0, len)
                                            }
                                        }
                                    }

                                    isoItem.dateAsDate.ifPresent { date ->
                                        targetFile.setLastModified(date.time)
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
                                currentFileName = rawPath.substringAfterLast('/')
                            )
                        )
                    }
                }
            }

            extractedFilesCount
        }
    }

    /**
     * Verifies the integrity of entries in an ISO disc image by streaming sectors through to completion.
     */
    suspend fun testArchive(
        isoFile: File,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<ArchiveIntegrityReport> = withContext(ioDispatcher) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val corruptedEntries = mutableListOf<String>()
            var verifiedCount = 0
            val buffer = ByteArray(BUFFER_SIZE)

            IsoFileReader(isoFile).use { reader ->
                val allFiles = reader.allFiles
                val flatList = reader.convertTreeFilesToFlatList(allFiles)
                val totalEntries = flatList.size
                ArchiveSafetyGuard.validateArchiveHeader(totalEntries)

                for (isoItem in flatList) {
                    coroutineContext.ensureActive()

                    val rawPath = isoItem.getFullFileName('/').trimStart('/').removeSuffix("/")
                    if (rawPath.isEmpty()) continue

                    try {
                        if (!isoItem.isDirectory) {
                            reader.getFileStream(isoItem).use { inputStream ->
                                while (inputStream.read(buffer) != -1) {
                                    coroutineContext.ensureActive()
                                }
                            }
                        }
                        verifiedCount++
                    } catch (e: Exception) {
                        corruptedEntries.add(rawPath)
                    }

                    onProgress?.invoke(
                        OperationProgress(
                            filesProcessed = verifiedCount + corruptedEntries.size,
                            totalFiles = totalEntries,
                            currentFileName = rawPath.substringAfterLast('/')
                        )
                    )
                }

                val elapsed = System.currentTimeMillis() - startTime
                ArchiveIntegrityReport(
                    archiveName = isoFile.name,
                    totalEntries = totalEntries,
                    verifiedEntries = verifiedCount,
                    corruptedEntries = corruptedEntries,
                    elapsedMs = elapsed
                )
            }
        }
    }

    /**
     * Computes metadata and disc image metrics for the ISO file.
     */
    suspend fun getArchiveProperties(isoFile: File): ArchiveProperties = withContext(ioDispatcher) {
        if (!isoFile.exists() || !isoFile.isFile) {
            return@withContext ArchiveProperties(
                archiveName = isoFile.name,
                archivePath = isoFile.absolutePath,
                totalFiles = 0,
                totalFolders = 0,
                uncompressedSize = 0L,
                compressedSize = 0L,
                lastModified = isoFile.lastModified()
            )
        }

        var totalFiles = 0
        var totalFolders = 0
        var uncompressedSize = 0L

        runCatching {
            IsoFileReader(isoFile).use { reader ->
                val allFiles = reader.allFiles
                val flatList = reader.convertTreeFilesToFlatList(allFiles)
                for (item in flatList) {
                    val rawPath = item.getFullFileName('/').trimStart('/').removeSuffix("/")
                    if (rawPath.isEmpty()) continue

                    if (item.isDirectory) {
                        totalFolders++
                    } else {
                        totalFiles++
                        uncompressedSize += item.size.coerceAtLeast(0L)
                    }
                }
            }
        }

        ArchiveProperties(
            archiveName = isoFile.name,
            archivePath = isoFile.absolutePath,
            totalFiles = totalFiles,
            totalFolders = totalFolders,
            uncompressedSize = uncompressedSize,
            compressedSize = isoFile.length(),
            lastModified = isoFile.lastModified()
        )
    }

    /**
     * Explicit refusal to mount ISO disc images, adhering to truthfulness standards.
     */
    fun mountIso(isoFile: File): Nothing {
        throw UnsupportedOperationException(
            "ISO mounting is not supported on Android. Direct block loop mounting requires kernel loop device permissions " +
            "and root privileges (CAP_SYS_ADMIN), which are restricted by the Android SELinux sandbox. " +
            "ARCA provides robust in-place browsing, inspection, and extraction instead."
        )
    }

    /**
     * Explicit refusal to create ISO disc images.
     */
    fun createIso(sources: List<File>, destinationIso: File): Nothing {
        throw UnsupportedOperationException(
            "ISO disc image creation is not supported. ARCA supports ISO browsing, inspection, and extraction only."
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
}
