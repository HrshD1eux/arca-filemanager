package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

class ZipEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Lists all entries inside a .zip file without extracting.
     */
    suspend fun listEntries(zipFile: File): List<ArchiveEntry> = withContext(ioDispatcher) {
        if (!zipFile.exists() || !zipFile.isFile) return@withContext emptyList()

        val entries = mutableListOf<ArchiveEntry>()
        runCatching {
            ZipFile(zipFile).use { zf ->
                val zipEntries = zf.entries()
                while (zipEntries.hasMoreElements()) {
                    val entry = zipEntries.nextElement()
                    val rawName = entry.name.removeSuffix("/")
                    val isDir = entry.isDirectory || entry.name.endsWith("/")
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
                            compressedSize = if (isDir) 0L else entry.compressedSize.coerceAtLeast(0L),
                            lastModified = entry.time.takeIf { it > 0 } ?: zipFile.lastModified(),
                            crc = entry.crc
                        )
                    )
                }
            }
        }
        entries.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Extracts all files from the ZIP into destination directory with Zip-Slip defense and conflict resolution.
     */
    suspend fun extractAll(
        zipFile: File,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            val destCanonical = destinationDir.canonicalFile

            var extractedFilesCount = 0
            var bytesProcessed = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            ZipFile(zipFile).use { zf ->
                val totalEntries = zf.size()
                val entries = zf.entries()

                while (entries.hasMoreElements()) {
                    coroutineContext.ensureActive()
                    val entry = entries.nextElement()
                    val initialTarget = File(destinationDir, entry.name)

                    // Zip Slip Defense
                    if (!initialTarget.canonicalPath.startsWith(destCanonical.path + File.separator) &&
                        initialTarget.canonicalPath != destCanonical.path
                    ) {
                        throw SecurityException("Zip Slip security violation detected: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        initialTarget.mkdirs()
                    } else {
                        val targetFile = resolveTargetFile(initialTarget, conflictResolution)
                        if (targetFile != null) {
                            targetFile.parentFile?.mkdirs()
                            try {
                                zf.getInputStream(entry).use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        var len: Int
                                        while (input.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            output.write(buffer, 0, len)
                                            bytesProcessed += len
                                        }
                                    }
                                }
                                if (entry.time > 0) {
                                    targetFile.setLastModified(entry.time)
                                }
                                extractedFilesCount++
                            } catch (e: CancellationException) {
                                if (targetFile.exists()) {
                                    targetFile.delete()
                                }
                                throw e
                            }
                        }
                    }

                    onProgress?.invoke(
                        OperationProgress(
                            bytesProcessed = bytesProcessed,
                            totalBytes = bytesProcessed,
                            filesProcessed = extractedFilesCount,
                            totalFiles = totalEntries,
                            currentFileName = entry.name.substringAfterLast('/')
                        )
                    )
                }
            }
            extractedFilesCount
        }
    }

    /**
     * Extracts specific entries from the ZIP with conflict resolution.
     */
    suspend fun extractEntries(
        zipFile: File,
        entryPaths: Set<String>,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            val destCanonical = destinationDir.canonicalFile
            var extractedFilesCount = 0
            var bytesProcessed = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            ZipFile(zipFile).use { zf ->
                for (entryPath in entryPaths) {
                    coroutineContext.ensureActive()
                    val entry = zf.getEntry(entryPath) ?: zf.getEntry("$entryPath/") ?: continue
                    val initialTarget = File(destinationDir, entry.name.substringAfterLast('/'))

                    if (!initialTarget.canonicalPath.startsWith(destCanonical.path + File.separator) &&
                        initialTarget.canonicalPath != destCanonical.path
                    ) {
                        throw SecurityException("Zip Slip security violation: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        initialTarget.mkdirs()
                    } else {
                        val targetFile = resolveTargetFile(initialTarget, conflictResolution)
                        if (targetFile != null) {
                            targetFile.parentFile?.mkdirs()
                            try {
                                zf.getInputStream(entry).use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        var len: Int
                                        while (input.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            output.write(buffer, 0, len)
                                            bytesProcessed += len
                                        }
                                    }
                                }
                                if (entry.time > 0) {
                                    targetFile.setLastModified(entry.time)
                                }
                                extractedFilesCount++
                            } catch (e: CancellationException) {
                                if (targetFile.exists()) {
                                    targetFile.delete()
                                }
                                throw e
                            }
                        }
                    }

                    onProgress?.invoke(
                        OperationProgress(
                            bytesProcessed = bytesProcessed,
                            totalBytes = bytesProcessed,
                            filesProcessed = extractedFilesCount,
                            totalFiles = entryPaths.size,
                            currentFileName = entry.name.substringAfterLast('/')
                        )
                    )
                }
            }
            extractedFilesCount
        }
    }

    private fun resolveTargetFile(file: File, conflictResolution: ConflictResolution): File? {
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

    /**
     * Compresses a list of files or directories into a standard .zip archive.
     */
    suspend fun createZip(
        sources: List<File>,
        destinationZip: File,
        compressionLevel: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            destinationZip.parentFile?.mkdirs()

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
                ZipOutputStream(FileOutputStream(destinationZip).buffered()).use { zos ->
                    zos.setLevel(compressionLevel)
                    for (source in sources) {
                        coroutineContext.ensureActive()
                        if (source.isDirectory) {
                            val baseDir = source.parentFile ?: source
                            source.walkTopDown().forEach { file ->
                                coroutineContext.ensureActive()
                                val relativePath = file.relativeTo(baseDir).path.replace('\\', '/')
                                if (file.isDirectory) {
                                    val entryName = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
                                    zos.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
                                    zos.closeEntry()
                                } else {
                                    zos.putNextEntry(ZipEntry(relativePath).apply { time = file.lastModified() })
                                    FileInputStream(file).use { fis ->
                                        var len: Int
                                        while (fis.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            zos.write(buffer, 0, len)
                                            bytesProcessed += len
                                        }
                                    }
                                    zos.closeEntry()
                                    filesProcessed++
                                }

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
                            zos.putNextEntry(ZipEntry(source.name).apply { time = source.lastModified() })
                            FileInputStream(source).use { fis ->
                                var len: Int
                                while (fis.read(buffer).also { len = it } != -1) {
                                    coroutineContext.ensureActive()
                                    zos.write(buffer, 0, len)
                                    bytesProcessed += len
                                }
                            }
                            zos.closeEntry()
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
                if (destinationZip.exists()) {
                    destinationZip.delete()
                }
                throw e
            }

            destinationZip.length()
        }
    }

    /**
     * Verifies the CRC-32 integrity of all entries in the ZIP archive without writing to disk.
     */
    suspend fun testArchive(
        zipFile: File,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<ArchiveIntegrityReport> = withContext(ioDispatcher) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val corruptedEntries = mutableListOf<String>()
            var verifiedCount = 0
            val buffer = ByteArray(BUFFER_SIZE)

            ZipFile(zipFile).use { zf ->
                val totalEntries = zf.size()
                val entries = zf.entries()

                while (entries.hasMoreElements()) {
                    coroutineContext.ensureActive()
                    val entry = entries.nextElement()
                    val crcCalculator = CRC32()

                    try {
                        if (!entry.isDirectory) {
                            zf.getInputStream(entry).use { input ->
                                var len: Int
                                while (input.read(buffer).also { len = it } != -1) {
                                    crcCalculator.update(buffer, 0, len)
                                }
                            }
                            if (entry.crc > 0 && crcCalculator.value != entry.crc) {
                                corruptedEntries.add(entry.name)
                            } else {
                                verifiedCount++
                            }
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
                }

                val elapsed = System.currentTimeMillis() - startTime
                ArchiveIntegrityReport(
                    archiveName = zipFile.name,
                    totalEntries = totalEntries,
                    verifiedEntries = verifiedCount,
                    corruptedEntries = corruptedEntries,
                    elapsedMs = elapsed
                )
            }
        }
    }

    /**
     * Safely appends/adds new files or directories into an existing ZIP archive.
     */
    suspend fun addEntries(
        zipFile: File,
        newSources: List<File>,
        targetVirtualPath: String = "",
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val tempZip = File(zipFile.parentFile, "${zipFile.name}.tmp_${System.currentTimeMillis()}")
            val buffer = ByteArray(BUFFER_SIZE)
            val addedPaths = mutableSetOf<String>()

            // Determine relative paths for new sources
            val entriesToAdd = mutableListOf<Pair<String, File>>()
            for (source in newSources) {
                if (source.isDirectory) {
                    val baseDir = source.parentFile ?: source
                    source.walkTopDown().forEach { file ->
                        val rel = file.relativeTo(baseDir).path.replace('\\', '/')
                        val finalPath = if (targetVirtualPath.isEmpty()) rel else "$targetVirtualPath/$rel"
                        entriesToAdd.add(finalPath to file)
                    }
                } else if (source.isFile) {
                    val finalPath = if (targetVirtualPath.isEmpty()) source.name else "$targetVirtualPath/${source.name}"
                    entriesToAdd.add(finalPath to source)
                }
            }
            val newPathSet = entriesToAdd.map { it.first }.toSet()

            ZipOutputStream(FileOutputStream(tempZip).buffered()).use { zos ->
                // 1. Copy existing entries (unless overwritten)
                if (zipFile.exists() && zipFile.length() > 0) {
                    ZipFile(zipFile).use { zf ->
                        val entries = zf.entries()
                        while (entries.hasMoreElements()) {
                            coroutineContext.ensureActive()
                            val entry = entries.nextElement()
                            val cleanName = entry.name.removeSuffix("/")
                            if (!newPathSet.contains(cleanName) && !newPathSet.contains(entry.name)) {
                                zos.putNextEntry(ZipEntry(entry.name).apply {
                                    time = entry.time
                                    comment = entry.comment
                                    extra = entry.extra
                                })
                                if (!entry.isDirectory) {
                                    zf.getInputStream(entry).use { input ->
                                        var len: Int
                                        while (input.read(buffer).also { len = it } != -1) {
                                            zos.write(buffer, 0, len)
                                        }
                                    }
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }

                // 2. Add new entries
                for ((entryPath, file) in entriesToAdd) {
                    coroutineContext.ensureActive()
                    if (file.isDirectory) {
                        val dirPath = if (entryPath.endsWith("/")) entryPath else "$entryPath/"
                        zos.putNextEntry(ZipEntry(dirPath).apply { time = file.lastModified() })
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(entryPath).apply { time = file.lastModified() })
                        FileInputStream(file).use { fis ->
                            var len: Int
                            while (fis.read(buffer).also { len = it } != -1) {
                                zos.write(buffer, 0, len)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }

            // Atomic replace
            if (zipFile.exists()) {
                zipFile.delete()
            }
            tempZip.renameTo(zipFile)
            zipFile.length()
        }
    }

    /**
     * Computes metadata and compression footprint properties for the ZIP file.
     */
    suspend fun getArchiveProperties(zipFile: File): ArchiveProperties = withContext(ioDispatcher) {
        if (!zipFile.exists() || !zipFile.isFile) {
            return@withContext ArchiveProperties(
                archiveName = zipFile.name,
                archivePath = zipFile.absolutePath,
                totalFiles = 0,
                totalFolders = 0,
                uncompressedSize = 0L,
                compressedSize = 0L,
                lastModified = zipFile.lastModified()
            )
        }

        var totalFiles = 0
        var totalFolders = 0
        var uncompressedSize = 0L
        var compressedSize = 0L

        runCatching {
            ZipFile(zipFile).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || entry.name.endsWith("/")) {
                        totalFolders++
                    } else {
                        totalFiles++
                        uncompressedSize += entry.size.coerceAtLeast(0L)
                        compressedSize += entry.compressedSize.coerceAtLeast(0L)
                    }
                }
            }
        }

        ArchiveProperties(
            archiveName = zipFile.name,
            archivePath = zipFile.absolutePath,
            totalFiles = totalFiles,
            totalFolders = totalFolders,
            uncompressedSize = uncompressedSize,
            compressedSize = if (compressedSize > 0) compressedSize else zipFile.length(),
            lastModified = zipFile.lastModified()
        )
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
    }
}
