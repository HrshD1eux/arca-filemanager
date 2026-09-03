package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
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
     * Extracts all files from the ZIP into destination directory with Zip-Slip defense.
     */
    suspend fun extractAll(
        zipFile: File,
        destinationDir: File,
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
                    val targetFile = File(destinationDir, entry.name)

                    // Zip Slip Defense
                    if (!targetFile.canonicalPath.startsWith(destCanonical.path + File.separator) &&
                        targetFile.canonicalPath != destCanonical.path
                    ) {
                        throw SecurityException("Zip Slip security violation detected: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
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
     * Extracts specific entries from the ZIP.
     */
    suspend fun extractEntries(
        zipFile: File,
        entryPaths: Set<String>,
        destinationDir: File,
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
                    val targetFile = File(destinationDir, entry.name.substringAfterLast('/'))

                    if (!targetFile.canonicalPath.startsWith(destCanonical.path + File.separator) &&
                        targetFile.canonicalPath != destCanonical.path
                    ) {
                        throw SecurityException("Zip Slip security violation: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
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

    /**
     * Compresses a list of files or directories into a standard .zip archive.
     */
    suspend fun createZip(
        sources: List<File>,
        destinationZip: File,
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

            ZipOutputStream(FileOutputStream(destinationZip).buffered()).use { zos ->
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
            destinationZip.length()
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
    }
}
