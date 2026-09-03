package com.Hrshd1eux.filemanager.core.operations.engine

import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.model.OperationResult
import com.Hrshd1eux.filemanager.core.operations.util.ConflictHelper
import com.Hrshd1eux.filemanager.core.operations.util.StreamCopier
import com.Hrshd1eux.filemanager.core.operations.verification.FileVerifier
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Core engine for asynchronous, streamed, and cancellable file operations.
 */
class FileOperationEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Copies a list of files/directories into the target destination directory.
     */
    suspend fun copy(
        sources: List<StorageNode>,
        destinationDir: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<OperationResult> = withContext(ioDispatcher) {
        runCatching {
            val destDirFile = File(destinationDir.path)
            if (!destDirFile.exists() || !destDirFile.isDirectory) {
                throw IOException("Destination is not a valid directory: ${destinationDir.path}")
            }

            // Calculate total byte count and file count for accurate progress
            var totalFiles = 0
            var totalBytes = 0L
            sources.forEach { source ->
                val file = File(source.path)
                if (file.exists()) {
                    if (file.isDirectory) {
                        file.walkTopDown().forEach { f ->
                            if (!f.isDirectory) {
                                totalFiles++
                                totalBytes += f.length()
                            }
                        }
                    } else {
                        totalFiles++
                        totalBytes += file.length()
                    }
                }
            }

            var filesProcessed = 0
            var bytesProcessed = 0L

            for (source in sources) {
                coroutineContext.ensureActive()
                val srcFile = File(source.path)
                if (!srcFile.exists()) continue

                if (srcFile.isDirectory) {
                    val targetSubDir = File(destDirFile, srcFile.name)
                    if (!targetSubDir.exists()) targetSubDir.mkdirs()

                    srcFile.walkTopDown().forEach { child ->
                        coroutineContext.ensureActive()
                        val relativePath = child.relativeTo(srcFile).path
                        val targetChild = File(targetSubDir, relativePath)

                        if (child.isDirectory) {
                            if (!targetChild.exists()) targetChild.mkdirs()
                        } else {
                            copySingleFile(
                                source = child,
                                destination = targetChild,
                                conflictResolution = conflictResolution,
                                verifyIntegrity = verifyIntegrity,
                                onChunkCopied = { chunk ->
                                    bytesProcessed += chunk
                                    onProgress?.invoke(
                                        OperationProgress(
                                            bytesProcessed = bytesProcessed,
                                            totalBytes = totalBytes,
                                            filesProcessed = filesProcessed,
                                            totalFiles = totalFiles,
                                            currentFileName = child.name
                                        )
                                    )
                                }
                            )
                            filesProcessed++
                        }
                    }
                } else {
                    val targetFile = when (conflictResolution) {
                        ConflictResolution.RENAME_AUTO -> ConflictHelper.getNonConflictingDestination(destDirFile, srcFile.name)
                        else -> File(destDirFile, srcFile.name)
                    }

                    if (targetFile.exists() && conflictResolution == ConflictResolution.SKIP) {
                        continue
                    }
                    if (targetFile.exists() && conflictResolution == ConflictResolution.CANCEL) {
                        return@runCatching OperationResult.Cancelled(filesProcessed, bytesProcessed)
                    }

                    copySingleFile(
                        source = srcFile,
                        destination = targetFile,
                        conflictResolution = conflictResolution,
                        verifyIntegrity = verifyIntegrity,
                        onChunkCopied = { chunk ->
                            bytesProcessed += chunk
                            onProgress?.invoke(
                                OperationProgress(
                                    bytesProcessed = bytesProcessed,
                                    totalBytes = totalBytes,
                                    filesProcessed = filesProcessed,
                                    totalFiles = totalFiles,
                                    currentFileName = srcFile.name
                                )
                            )
                        }
                    )
                    filesProcessed++
                }
            }

            OperationResult.Success(
                filesProcessed = filesProcessed,
                bytesProcessed = bytesProcessed
            )
        }.recoverCatching { error ->
            if (error is CancellationException) {
                throw error
            }
            throw error
        }
    }

    /**
     * Moves a list of files/directories into the target destination directory.
     * Uses fast POSIX rename where possible, falling back to streamed copy-verify-delete.
     */
    suspend fun move(
        sources: List<StorageNode>,
        destinationDir: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<OperationResult> = withContext(ioDispatcher) {
        runCatching {
            val destDirFile = File(destinationDir.path)
            if (!destDirFile.exists() || !destDirFile.isDirectory) {
                throw IOException("Destination is not a valid directory: ${destinationDir.path}")
            }

            var filesProcessed = 0
            var bytesProcessed = 0L

            for (source in sources) {
                coroutineContext.ensureActive()
                val srcFile = File(source.path)
                if (!srcFile.exists()) continue

                val targetFile = when (conflictResolution) {
                    ConflictResolution.RENAME_AUTO -> ConflictHelper.getNonConflictingDestination(destDirFile, srcFile.name)
                    else -> File(destDirFile, srcFile.name)
                }

                if (targetFile.exists() && conflictResolution == ConflictResolution.SKIP) {
                    continue
                }
                if (targetFile.exists() && conflictResolution == ConflictResolution.CANCEL) {
                    return@runCatching OperationResult.Cancelled(filesProcessed, bytesProcessed)
                }

                // Try fast POSIX rename
                val renamed = srcFile.renameTo(targetFile)
                if (renamed) {
                    filesProcessed++
                    bytesProcessed += if (targetFile.isDirectory) 0L else targetFile.length()
                    onProgress?.invoke(
                        OperationProgress(
                            bytesProcessed = bytesProcessed,
                            totalBytes = bytesProcessed,
                            filesProcessed = filesProcessed,
                            totalFiles = sources.size,
                            currentFileName = srcFile.name
                        )
                    )
                } else {
                    // Fallback to copy -> verify -> delete
                    val copyResult = copy(
                        sources = listOf(source),
                        destinationDir = destinationDir,
                        conflictResolution = conflictResolution,
                        onProgress = onProgress
                    ).getOrThrow()

                    if (copyResult is OperationResult.Success) {
                        srcFile.deleteRecursively()
                        filesProcessed += copyResult.filesProcessed
                        bytesProcessed += copyResult.bytesProcessed
                    }
                }
            }

            OperationResult.Success(
                filesProcessed = filesProcessed,
                bytesProcessed = bytesProcessed
            )
        }
    }

    /**
     * Recursively deletes a list of files or directories.
     */
    suspend fun delete(
        targets: List<StorageNode>,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<OperationResult> = withContext(ioDispatcher) {
        runCatching {
            var filesProcessed = 0
            var bytesProcessed = 0L

            for (target in targets) {
                coroutineContext.ensureActive()
                val targetFile = File(target.path)
                if (!targetFile.exists()) continue

                val fileSize = if (targetFile.isDirectory) 0L else targetFile.length()
                val deleted = targetFile.deleteRecursively()
                if (!deleted) {
                    throw IOException("Failed to delete file/folder: ${target.path}")
                }

                filesProcessed++
                bytesProcessed += fileSize
                onProgress?.invoke(
                    OperationProgress(
                        bytesProcessed = bytesProcessed,
                        totalBytes = bytesProcessed,
                        filesProcessed = filesProcessed,
                        totalFiles = targets.size,
                        currentFileName = target.name
                    )
                )
            }

            OperationResult.Success(
                filesProcessed = filesProcessed,
                bytesProcessed = bytesProcessed
            )
        }
    }

    /**
     * Renames a file or directory within its parent directory.
     */
    suspend fun rename(
        target: StorageNode,
        newName: String
    ): Result<StorageNode> = withContext(ioDispatcher) {
        runCatching {
            val sanitized = newName.trim()
            if (sanitized.isEmpty() || sanitized.contains("/") || sanitized.contains("\\")) {
                throw IllegalArgumentException("Invalid file name: $newName")
            }

            val targetFile = File(target.path)
            if (!targetFile.exists()) {
                throw IOException("Target file does not exist: ${target.path}")
            }

            val destinationFile = File(targetFile.parentFile, sanitized)
            if (destinationFile.exists()) {
                throw IOException("A file or directory with name '$sanitized' already exists.")
            }

            val renamed = targetFile.renameTo(destinationFile)
            if (!renamed) {
                throw IOException("Failed to rename '${target.name}' to '$sanitized'")
            }

            destinationFile.toStorageNode()
        }
    }

    /**
     * Creates a new directory inside the parent directory.
     */
    suspend fun createDirectory(
        parentDir: StorageNode,
        dirName: String
    ): Result<StorageNode> = withContext(ioDispatcher) {
        runCatching {
            val sanitized = dirName.trim()
            if (sanitized.isEmpty() || sanitized.contains("/") || sanitized.contains("\\")) {
                throw IllegalArgumentException("Invalid directory name: $dirName")
            }

            val parentFile = File(parentDir.path)
            if (!parentFile.exists() || !parentFile.isDirectory) {
                throw IOException("Parent is not a valid directory: ${parentDir.path}")
            }

            val newDir = File(parentFile, sanitized)
            if (newDir.exists()) {
                throw IOException("Directory '$sanitized' already exists.")
            }

            val created = newDir.mkdir()
            if (!created) {
                throw IOException("Failed to create directory: $sanitized")
            }

            newDir.toStorageNode()
        }
    }

    /**
     * Creates a new empty file inside the parent directory.
     */
    suspend fun createFile(
        parentDir: StorageNode,
        fileName: String
    ): Result<StorageNode> = withContext(ioDispatcher) {
        runCatching {
            val sanitized = fileName.trim()
            if (sanitized.isEmpty() || sanitized.contains("/") || sanitized.contains("\\")) {
                throw IllegalArgumentException("Invalid file name: $fileName")
            }

            val parentFile = File(parentDir.path)
            if (!parentFile.exists() || !parentFile.isDirectory) {
                throw IOException("Parent is not a valid directory: ${parentDir.path}")
            }

            val newFile = File(parentFile, sanitized)
            if (newFile.exists()) {
                throw IOException("File '$sanitized' already exists.")
            }

            val created = newFile.createNewFile()
            if (!created) {
                throw IOException("Failed to create file: $sanitized")
            }

            newFile.toStorageNode()
        }
    }

    private suspend fun copySingleFile(
        source: File,
        destination: File,
        conflictResolution: ConflictResolution,
        verifyIntegrity: Boolean = false,
        onChunkCopied: (chunk: Int) -> Unit
    ) {
        if (destination.exists() && conflictResolution == ConflictResolution.OVERWRITE) {
            destination.delete()
        }

        FileInputStream(source).use { inStream ->
            FileOutputStream(destination).use { outStream ->
                StreamCopier.copyStream(
                    source = inStream,
                    destination = outStream,
                    onBytesCopied = onChunkCopied
                )
            }
        }

        if (verifyIntegrity) {
            FileVerifier.verifyFilePair(source, destination, ioDispatcher)
        }
    }

    private fun File.toStorageNode(): StorageNode {
        val isDir = isDirectory
        val ext = if (isDir) "" else extension.lowercase()
        val mime = if (ext.isNotEmpty()) MimeTypeResolver.getMimeType(ext) else null

        return StorageNode(
            id = absolutePath,
            name = name.ifEmpty { absolutePath },
            path = absolutePath,
            uriString = null,
            size = if (isDir) 0L else length(),
            lastModified = lastModified(),
            isDirectory = isDir,
            isHidden = isHidden || name.startsWith("."),
            mimeType = mime,
            isReadable = canRead(),
            isWritable = canWrite()
        )
    }
}
