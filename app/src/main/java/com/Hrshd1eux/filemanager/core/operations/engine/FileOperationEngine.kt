package com.Hrshd1eux.filemanager.core.operations.engine

import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.model.OperationResult
import com.Hrshd1eux.filemanager.core.operations.util.ConflictHelper
import com.Hrshd1eux.filemanager.core.operations.util.StreamCopier
import com.Hrshd1eux.filemanager.core.operations.verification.FileVerifier
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
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
 * Universal file operations engine supporting both local POSIX storage and remote network shares:
 * - Phone → NAS
 * - NAS → Phone
 * - Phone → Server
 * - Server → Phone
 * - Local → Local
 * - Remote → Remote
 *
 * ARCHITECTURAL ISOLATION:
 * The operations engine remains 100% quarantined from network protocol/socket implementations.
 * All remote storage interactions are performed via the abstract RemoteStorageOperator interface.
 *
 * All operations execute asynchronously with streamed chunking, live progress callbacks,
 * automatic conflict resolution, and deterministic cancellation.
 */
class FileOperationEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val remoteOperator: RemoteStorageOperator? = null
) {

    companion object {
        private val REMOTE_SCHEMES = setOf("sftp", "ftp", "ftps", "smb", "webdav")
    }

    private fun getOperator(): RemoteStorageOperator? = remoteOperator ?: RemoteStorageOperatorRegistry.getOperator()

    private sealed class ResolvedNode {
        data class Local(val file: File, val node: StorageNode) : ResolvedNode()
        data class Remote(
            val operator: RemoteStorageOperator,
            val node: StorageNode
        ) : ResolvedNode()
    }

    /**
     * Copies a list of files or directories into the target destination directory.
     * Works seamlessly across local and network boundaries.
     */
    suspend fun copy(
        sources: List<StorageNode>,
        destinationDir: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<OperationResult> = withContext(ioDispatcher) {
        runCatching {
            val resolvedDest = resolveNode(destinationDir)
            validateDestinationDirectory(resolvedDest)

            // Calculate total items and bytes for progress estimation
            var totalFiles = 0
            var totalBytes = 0L

            for (source in sources) {
                coroutineContext.ensureActive()
                val resolvedSrc = resolveNode(source)
                when (resolvedSrc) {
                    is ResolvedNode.Local -> {
                        val file = resolvedSrc.file
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
                    is ResolvedNode.Remote -> {
                        if (source.isDirectory) {
                            val tree = resolvedSrc.operator.listTree(source).getOrDefault(emptyList())
                            for (entry in tree) {
                                if (!entry.node.isDirectory) {
                                    totalFiles++
                                    totalBytes += entry.node.size
                                }
                            }
                        } else {
                            totalFiles++
                            totalBytes += source.size
                        }
                    }
                }
            }

            // Check available storage space for local destinations
            if (resolvedDest is ResolvedNode.Local && totalBytes > 0) {
                val usableSpace = resolvedDest.file.usableSpace
                if (usableSpace in 1 until totalBytes) {
                    throw IOException(
                        "Insufficient storage space: ${FileFormatter.formatFileSize(totalBytes)} required, but only ${FileFormatter.formatFileSize(usableSpace)} available."
                    )
                }
            }

            var filesProcessed = 0
            var bytesProcessed = 0L

            for (source in sources) {
                coroutineContext.ensureActive()
                val resolvedSrc = resolveNode(source)

                when {
                    // 1. Local -> Local (Phone -> Phone)
                    resolvedSrc is ResolvedNode.Local && resolvedDest is ResolvedNode.Local -> {
                        val srcFile = resolvedSrc.file
                        if (!srcFile.exists()) continue

                        if (srcFile.isDirectory) {
                            val targetSubDir = File(resolvedDest.file, srcFile.name)
                            if (!targetSubDir.exists()) targetSubDir.mkdirs()

                            srcFile.walkTopDown().forEach { child ->
                                coroutineContext.ensureActive()
                                val relativePath = child.relativeTo(srcFile).path
                                val targetChild = File(targetSubDir, relativePath)

                                if (child.isDirectory) {
                                    if (!targetChild.exists()) targetChild.mkdirs()
                                } else {
                                    copyLocalSingleFile(
                                        source = child,
                                        destination = targetChild,
                                        conflictResolution = conflictResolution,
                                        verifyIntegrity = verifyIntegrity,
                                        onChunkCopied = { chunk ->
                                            bytesProcessed += chunk
                                            emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, totalFiles, child.name)
                                        }
                                    )
                                    filesProcessed++
                                }
                            }
                        } else {
                            val targetFile = resolveLocalTargetFile(resolvedDest.file, srcFile.name, conflictResolution)
                            if (targetFile == null) {
                                if (conflictResolution == ConflictResolution.CANCEL) {
                                    return@runCatching OperationResult.Cancelled(filesProcessed, bytesProcessed)
                                }
                                continue
                            }

                            copyLocalSingleFile(
                                source = srcFile,
                                destination = targetFile,
                                conflictResolution = conflictResolution,
                                verifyIntegrity = verifyIntegrity,
                                onChunkCopied = { chunk ->
                                    bytesProcessed += chunk
                                    emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, totalFiles, srcFile.name)
                                }
                            )
                            filesProcessed++
                        }
                    }

                    // 2. Local -> Remote (Phone -> NAS / Server)
                    resolvedSrc is ResolvedNode.Local && resolvedDest is ResolvedNode.Remote -> {
                        val srcFile = resolvedSrc.file
                        if (!srcFile.exists()) continue

                        if (srcFile.isDirectory) {
                            val targetRemoteSubDir = resolvedDest.operator.resolveChild(resolvedDest.node, srcFile.name, isDirectory = true)
                            resolvedDest.operator.createDirectory(resolvedDest.node, srcFile.name)

                            srcFile.walkTopDown().forEach { child ->
                                coroutineContext.ensureActive()
                                val relPath = child.relativeTo(srcFile).path.replace('\\', '/')
                                val targetChildRemote = resolvedDest.operator.resolveChild(targetRemoteSubDir, relPath, child.isDirectory)

                                if (child.isDirectory) {
                                    resolvedDest.operator.createDirectory(targetRemoteSubDir, relPath)
                                } else {
                                    copyLocalToRemoteSingleFile(
                                        srcFile = child,
                                        destOperator = resolvedDest.operator,
                                        destNode = targetChildRemote,
                                        onChunkCopied = { chunk ->
                                            bytesProcessed += chunk
                                            emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, totalFiles, child.name)
                                        }
                                    )
                                    filesProcessed++
                                }
                            }
                        } else {
                            val targetNode = resolveRemoteTargetNode(
                                operator = resolvedDest.operator,
                                parentDir = resolvedDest.node,
                                desiredName = srcFile.name,
                                conflictResolution = conflictResolution
                            )
                            if (targetNode == null) {
                                if (conflictResolution == ConflictResolution.CANCEL) {
                                    return@runCatching OperationResult.Cancelled(filesProcessed, bytesProcessed)
                                }
                                continue
                            }

                            copyLocalToRemoteSingleFile(
                                srcFile = srcFile,
                                destOperator = resolvedDest.operator,
                                destNode = targetNode,
                                onChunkCopied = { chunk ->
                                    bytesProcessed += chunk
                                    emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, totalFiles, srcFile.name)
                                }
                            )
                            filesProcessed++
                        }
                    }

                    // 3. Remote -> Local (NAS / Server -> Phone)
                    resolvedSrc is ResolvedNode.Remote && resolvedDest is ResolvedNode.Local -> {
                        if (source.isDirectory) {
                            val targetLocalSubDir = File(resolvedDest.file, source.name)
                            if (!targetLocalSubDir.exists()) targetLocalSubDir.mkdirs()

                            val tree = resolvedSrc.operator.listTree(source).getOrDefault(emptyList())
                            for (entry in tree) {
                                coroutineContext.ensureActive()
                                val targetLocalChild = File(targetLocalSubDir, entry.relativeSubPath.replace('/', File.separatorChar))

                                if (entry.node.isDirectory) {
                                    if (!targetLocalChild.exists()) targetLocalChild.mkdirs()
                                } else {
                                    targetLocalChild.parentFile?.mkdirs()
                                    copyRemoteToLocalSingleFile(
                                        srcOperator = resolvedSrc.operator,
                                        srcNode = entry.node,
                                        destFile = targetLocalChild,
                                        onChunkCopied = { chunk ->
                                            bytesProcessed += chunk
                                            emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, totalFiles, targetLocalChild.name)
                                        }
                                    )
                                    filesProcessed++
                                }
                            }
                        } else {
                            val targetFile = resolveLocalTargetFile(resolvedDest.file, source.name, conflictResolution)
                            if (targetFile == null) {
                                if (conflictResolution == ConflictResolution.CANCEL) {
                                    return@runCatching OperationResult.Cancelled(filesProcessed, bytesProcessed)
                                }
                                continue
                            }

                            copyRemoteToLocalSingleFile(
                                srcOperator = resolvedSrc.operator,
                                srcNode = source,
                                destFile = targetFile,
                                onChunkCopied = { chunk ->
                                    bytesProcessed += chunk
                                    emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, totalFiles, source.name)
                                }
                            )
                            filesProcessed++
                        }
                    }

                    // 4. Remote -> Remote (NAS -> Server / NAS -> NAS)
                    resolvedSrc is ResolvedNode.Remote && resolvedDest is ResolvedNode.Remote -> {
                        if (source.isDirectory) {
                            val targetRemoteSubDir = resolvedDest.operator.resolveChild(resolvedDest.node, source.name, isDirectory = true)
                            resolvedDest.operator.createDirectory(resolvedDest.node, source.name)

                            val tree = resolvedSrc.operator.listTree(source).getOrDefault(emptyList())
                            for (entry in tree) {
                                coroutineContext.ensureActive()
                                val targetChildRemote = resolvedDest.operator.resolveChild(targetRemoteSubDir, entry.relativeSubPath, entry.node.isDirectory)

                                if (entry.node.isDirectory) {
                                    resolvedDest.operator.createDirectory(targetRemoteSubDir, entry.relativeSubPath)
                                } else {
                                    copyRemoteToRemoteSingleFile(
                                        srcOperator = resolvedSrc.operator,
                                        srcNode = entry.node,
                                        destOperator = resolvedDest.operator,
                                        destNode = targetChildRemote,
                                        onChunkCopied = { chunk ->
                                            bytesProcessed += chunk
                                            emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, totalFiles, entry.node.name)
                                        }
                                    )
                                    filesProcessed++
                                }
                            }
                        } else {
                            val targetNode = resolveRemoteTargetNode(
                                operator = resolvedDest.operator,
                                parentDir = resolvedDest.node,
                                desiredName = source.name,
                                conflictResolution = conflictResolution
                            )
                            if (targetNode == null) {
                                if (conflictResolution == ConflictResolution.CANCEL) {
                                    return@runCatching OperationResult.Cancelled(filesProcessed, bytesProcessed)
                                }
                                continue
                            }

                            copyRemoteToRemoteSingleFile(
                                srcOperator = resolvedSrc.operator,
                                srcNode = source,
                                destOperator = resolvedDest.operator,
                                destNode = targetNode,
                                onChunkCopied = { chunk ->
                                    bytesProcessed += chunk
                                    emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, totalFiles, source.name)
                                }
                            )
                            filesProcessed++
                        }
                    }
                }
            }

            OperationResult.Success(
                filesProcessed = filesProcessed,
                bytesProcessed = bytesProcessed
            )
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw error
        }
    }

    /**
     * Moves a list of files or directories into the target destination directory.
     * Uses fast rename when moving within the same local or remote filesystem,
     * falling back to streamed cross-boundary copy and safe source cleanup.
     */
    suspend fun move(
        sources: List<StorageNode>,
        destinationDir: StorageNode,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<OperationResult> = withContext(ioDispatcher) {
        runCatching {
            val resolvedDest = resolveNode(destinationDir)
            validateDestinationDirectory(resolvedDest)

            var filesProcessed = 0
            var bytesProcessed = 0L

            for (source in sources) {
                coroutineContext.ensureActive()
                val resolvedSrc = resolveNode(source)

                // Check for fast POSIX or remote rename within the same provider
                if (resolvedSrc is ResolvedNode.Local && resolvedDest is ResolvedNode.Local) {
                    val srcFile = resolvedSrc.file
                    if (!srcFile.exists()) continue

                    val targetFile = resolveLocalTargetFile(resolvedDest.file, srcFile.name, conflictResolution)
                    if (targetFile == null) {
                        if (conflictResolution == ConflictResolution.CANCEL) {
                            return@runCatching OperationResult.Cancelled(filesProcessed, bytesProcessed)
                        }
                        continue
                    }

                    val renamed = srcFile.renameTo(targetFile)
                    if (renamed) {
                        filesProcessed++
                        val size = if (targetFile.isDirectory) 0L else targetFile.length()
                        bytesProcessed += size
                        emitProgress(onProgress, bytesProcessed, bytesProcessed, filesProcessed, sources.size, srcFile.name)
                        continue
                    }
                } else if (resolvedSrc is ResolvedNode.Remote && resolvedDest is ResolvedNode.Remote &&
                    resolvedSrc.operator.isSameServer(resolvedSrc.node, resolvedDest.node)) {
                    // Intra-server remote rename / move
                    val targetChild = resolvedDest.operator.resolveChild(resolvedDest.node, source.name, source.isDirectory)
                    val renameResult = resolvedSrc.operator.rename(resolvedSrc.node, targetChild.name)
                    if (renameResult.isSuccess) {
                        filesProcessed++
                        bytesProcessed += source.size
                        emitProgress(onProgress, bytesProcessed, bytesProcessed, filesProcessed, sources.size, source.name)
                        continue
                    }
                }

                // Cross-boundary move: Copy first -> verify -> delete source
                val copyResult = copy(
                    sources = listOf(source),
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    onProgress = onProgress
                ).getOrThrow()

                if (copyResult is OperationResult.Success) {
                    // Delete source
                    when (resolvedSrc) {
                        is ResolvedNode.Local -> {
                            if (resolvedSrc.file.isDirectory) {
                                resolvedSrc.file.deleteRecursively()
                            } else {
                                resolvedSrc.file.delete()
                            }
                        }
                        is ResolvedNode.Remote -> {
                            if (source.isDirectory) {
                                resolvedSrc.operator.deleteRecursive(source)
                            } else {
                                resolvedSrc.operator.delete(source)
                            }
                        }
                    }
                    filesProcessed += copyResult.filesProcessed
                    bytesProcessed += copyResult.bytesProcessed
                } else if (copyResult is OperationResult.Cancelled) {
                    return@runCatching copyResult
                }
            }

            OperationResult.Success(
                filesProcessed = filesProcessed,
                bytesProcessed = bytesProcessed
            )
        }
    }

    /**
     * Recursively deletes a list of files or directories (local or remote).
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
                val resolved = resolveNode(target)

                when (resolved) {
                    is ResolvedNode.Local -> {
                        val file = resolved.file
                        if (!file.exists()) continue
                        val size = if (file.isDirectory) 0L else file.length()
                        val deleted = file.deleteRecursively()
                        if (!deleted) {
                            throw IOException("Failed to delete local file/folder: ${target.path}")
                        }
                        filesProcessed++
                        bytesProcessed += size
                    }
                    is ResolvedNode.Remote -> {
                        if (target.isDirectory) {
                            resolved.operator.deleteRecursive(target)
                        } else {
                            val res = resolved.operator.delete(target)
                            if (res.isFailure) {
                                throw res.exceptionOrNull() ?: IOException("Failed to delete remote file: ${target.path}")
                            }
                        }
                        filesProcessed++
                        bytesProcessed += target.size
                    }
                }

                emitProgress(onProgress, bytesProcessed, bytesProcessed, filesProcessed, targets.size, target.name)
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

            val resolved = resolveNode(target)
            when (resolved) {
                is ResolvedNode.Local -> {
                    val targetFile = resolved.file
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
                is ResolvedNode.Remote -> {
                    resolved.operator.rename(target, sanitized).getOrThrow()
                }
            }
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

            val resolved = resolveNode(parentDir)
            when (resolved) {
                is ResolvedNode.Local -> {
                    val parentFile = resolved.file
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
                is ResolvedNode.Remote -> {
                    resolved.operator.createDirectory(parentDir, sanitized).getOrThrow()
                }
            }
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

            val resolved = resolveNode(parentDir)
            when (resolved) {
                is ResolvedNode.Local -> {
                    val parentFile = resolved.file
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
                is ResolvedNode.Remote -> {
                    resolved.operator.createFile(parentDir, sanitized).getOrThrow()
                }
            }
        }
    }

    // --- Private Helper Functions ---

    private fun resolveNode(node: StorageNode): ResolvedNode {
        val op = getOperator()
        if (op != null && op.canHandle(node)) {
            return ResolvedNode.Remote(op, node)
        }
        val uri = node.uriString ?: node.id
        val scheme = uri.substringBefore("://", "").lowercase()
        if (scheme in REMOTE_SCHEMES) {
            val validOp = op ?: throw IOException("Remote storage operation requested for '$uri' but no RemoteStorageOperator is registered.")
            return ResolvedNode.Remote(validOp, node)
        }
        return ResolvedNode.Local(File(node.path), node)
    }

    private suspend fun validateDestinationDirectory(destination: ResolvedNode) {
        when (destination) {
            is ResolvedNode.Local -> {
                if (!destination.file.exists() || !destination.file.isDirectory) {
                    throw IOException("Destination is not a valid directory: ${destination.file.absolutePath}")
                }
            }
            is ResolvedNode.Remote -> {
                val exists = destination.operator.exists(destination.node)
                if (!exists) {
                    throw IOException("Remote destination directory does not exist: ${destination.node.path}")
                }
            }
        }
    }

    private suspend fun copyLocalSingleFile(
        source: File,
        destination: File,
        conflictResolution: ConflictResolution,
        verifyIntegrity: Boolean = false,
        onChunkCopied: (chunk: Int) -> Unit
    ) {
        if (destination.exists() && conflictResolution == ConflictResolution.OVERWRITE) {
            destination.delete()
        }

        try {
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
        } catch (t: Throwable) {
            if (destination.exists()) {
                destination.delete()
            }
            throw t
        }
    }

    private suspend fun copyLocalToRemoteSingleFile(
        srcFile: File,
        destOperator: RemoteStorageOperator,
        destNode: StorageNode,
        onChunkCopied: (chunk: Int) -> Unit
    ) {
        FileInputStream(srcFile).use { inStream ->
            val outStreamResult = destOperator.openOutputStream(destNode, append = false)
            val outStream = outStreamResult.getOrThrow()
            outStream.use { out ->
                StreamCopier.copyStream(
                    source = inStream,
                    destination = out,
                    onBytesCopied = onChunkCopied
                )
            }
        }
    }

    private suspend fun copyRemoteToLocalSingleFile(
        srcOperator: RemoteStorageOperator,
        srcNode: StorageNode,
        destFile: File,
        onChunkCopied: (chunk: Int) -> Unit
    ) {
        if (destFile.exists()) {
            destFile.delete()
        }
        try {
            val inStreamResult = srcOperator.openInputStream(srcNode)
            val inStream = inStreamResult.getOrThrow()
            inStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    StreamCopier.copyStream(
                        source = input,
                        destination = output,
                        onBytesCopied = onChunkCopied
                    )
                }
            }
        } catch (t: Throwable) {
            if (destFile.exists()) {
                destFile.delete()
            }
            throw t
        }
    }

    private suspend fun copyRemoteToRemoteSingleFile(
        srcOperator: RemoteStorageOperator,
        srcNode: StorageNode,
        destOperator: RemoteStorageOperator,
        destNode: StorageNode,
        onChunkCopied: (chunk: Int) -> Unit
    ) {
        val inStream = srcOperator.openInputStream(srcNode).getOrThrow()
        inStream.use { input ->
            val outStream = destOperator.openOutputStream(destNode, append = false).getOrThrow()
            outStream.use { output ->
                StreamCopier.copyStream(
                    source = input,
                    destination = output,
                    onBytesCopied = onChunkCopied
                )
            }
        }
    }

    private fun resolveLocalTargetFile(
        parentDir: File,
        desiredName: String,
        conflictResolution: ConflictResolution
    ): File? {
        val initialFile = File(parentDir, desiredName)
        if (!initialFile.exists()) return initialFile

        return when (conflictResolution) {
            ConflictResolution.SKIP -> null
            ConflictResolution.CANCEL -> null
            ConflictResolution.OVERWRITE -> initialFile
            ConflictResolution.RENAME_AUTO -> ConflictHelper.getNonConflictingDestination(parentDir, desiredName)
        }
    }

    private suspend fun resolveRemoteTargetNode(
        operator: RemoteStorageOperator,
        parentDir: StorageNode,
        desiredName: String,
        conflictResolution: ConflictResolution
    ): StorageNode? {
        val initialNode = operator.resolveChild(parentDir, desiredName)
        val exists = operator.exists(initialNode)
        if (!exists) {
            return initialNode
        }

        return when (conflictResolution) {
            ConflictResolution.SKIP -> null
            ConflictResolution.CANCEL -> null
            ConflictResolution.OVERWRITE -> initialNode
            ConflictResolution.RENAME_AUTO -> {
                val baseName = if (desiredName.contains('.')) desiredName.substringBeforeLast('.') else desiredName
                val ext = if (desiredName.contains('.')) ".${desiredName.substringAfterLast('.')}" else ""
                var counter = 1
                var candidate = initialNode
                while (operator.exists(candidate)) {
                    val candidateName = "$baseName ($counter)$ext"
                    candidate = operator.resolveChild(parentDir, candidateName)
                    counter++
                }
                candidate
            }
        }
    }

    private fun emitProgress(
        onProgress: ((OperationProgress) -> Unit)?,
        bytesProcessed: Long,
        totalBytes: Long,
        filesProcessed: Int,
        totalFiles: Int,
        currentFileName: String
    ) {
        onProgress?.invoke(
            OperationProgress(
                bytesProcessed = bytesProcessed,
                totalBytes = totalBytes,
                filesProcessed = filesProcessed,
                totalFiles = totalFiles,
                currentFileName = currentFileName
            )
        )
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
