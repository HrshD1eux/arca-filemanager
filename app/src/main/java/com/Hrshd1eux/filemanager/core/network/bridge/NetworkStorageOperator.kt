package com.Hrshd1eux.filemanager.core.network.bridge

import com.Hrshd1eux.filemanager.core.network.provider.NetworkProvider
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import com.Hrshd1eux.filemanager.core.network.util.NetworkPathResolver
import com.Hrshd1eux.filemanager.core.operations.engine.RemoteStorageOperator
import com.Hrshd1eux.filemanager.core.operations.engine.RemoteTreeEntry
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.Hrshd1eux.filemanager.core.network.util.NetworkOperationKind
import com.Hrshd1eux.filemanager.core.network.util.NetworkRetryPolicy
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Concrete implementation of RemoteStorageOperator bridging network providers
 * into the offline FileOperationEngine.
 */
class NetworkStorageOperator(
    private val providerResolver: (suspend (String) -> NetworkProvider?) = { configId ->
        NetworkProviderRegistry.get(configId)
    }
) : RemoteStorageOperator {

    override fun canHandle(node: StorageNode): Boolean {
        return NetworkPathResolver.isRemote(node)
    }

    override fun isSameServer(nodeA: StorageNode, nodeB: StorageNode): Boolean {
        val specA = NetworkPathResolver.parseRemoteSpec(nodeA) ?: return false
        val specB = NetworkPathResolver.parseRemoteSpec(nodeB) ?: return false
        return specA.scheme == specB.scheme && specA.serverConfigId == specB.serverConfigId
    }

    override suspend fun openInputStream(node: StorageNode): Result<InputStream> {
        val spec = NetworkPathResolver.parseRemoteSpec(node)
            ?: return Result.failure(IOException("Not a valid remote node: ${node.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))
        return provider.openInputStream(spec.remotePath)
    }

    override suspend fun openOutputStream(node: StorageNode, append: Boolean): Result<OutputStream> {
        val spec = NetworkPathResolver.parseRemoteSpec(node)
            ?: return Result.failure(IOException("Not a valid remote node: ${node.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))
        return provider.openOutputStream(spec.remotePath, append)
    }

    override suspend fun createDirectory(parentDir: StorageNode, dirName: String): Result<StorageNode> {
        val spec = NetworkPathResolver.parseRemoteSpec(parentDir)
            ?: return Result.failure(IOException("Not a valid remote parent node: ${parentDir.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))
        val targetPath = NetworkPathResolver.joinRemotePath(spec.remotePath, dirName)
        return runCatching {
            NetworkRetryPolicy.executeSafely(
                kind = NetworkOperationKind.DESTRUCTIVE,
                operationName = "CREATE_DIRECTORY",
                targetPath = targetPath
            ) {
                provider.createDirectory(targetPath).getOrThrow()
            }
            resolveChild(parentDir, dirName, isDirectory = true)
        }
    }

    override suspend fun createFile(parentDir: StorageNode, fileName: String): Result<StorageNode> {
        val spec = NetworkPathResolver.parseRemoteSpec(parentDir)
            ?: return Result.failure(IOException("Not a valid remote parent node: ${parentDir.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))
        val targetPath = NetworkPathResolver.joinRemotePath(spec.remotePath, fileName)
        val outResult = provider.openOutputStream(targetPath, append = false)
        return outResult.mapCatching { outStream ->
            outStream.close()
            resolveChild(parentDir, fileName, isDirectory = false)
        }
    }

    override suspend fun delete(target: StorageNode): Result<Unit> {
        val spec = NetworkPathResolver.parseRemoteSpec(target)
            ?: return Result.failure(IOException("Not a valid remote node: ${target.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))
        return runCatching {
            NetworkRetryPolicy.executeSafely(
                kind = NetworkOperationKind.DESTRUCTIVE,
                operationName = "DELETE",
                targetPath = spec.remotePath
            ) {
                provider.delete(spec.remotePath).getOrThrow()
            }
        }
    }

    override suspend fun deleteRecursive(target: StorageNode): Result<Unit> {
        val spec = NetworkPathResolver.parseRemoteSpec(target)
            ?: return Result.failure(IOException("Not a valid remote node: ${target.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))

        return runCatching {
            NetworkRetryPolicy.executeSafely(
                kind = NetworkOperationKind.DESTRUCTIVE,
                operationName = "DELETE_RECURSIVE",
                targetPath = spec.remotePath
            ) {
                val treeResult = listTree(target)
                if (treeResult.isFailure) {
                    return@executeSafely provider.delete(spec.remotePath).getOrThrow()
                }
                val entries = treeResult.getOrDefault(emptyList())

                // 1. Delete files first
                for (entry in entries.filter { !it.node.isDirectory }) {
                    currentCoroutineContext().ensureActive()
                    val entrySpec = NetworkPathResolver.parseRemoteSpec(entry.node) ?: continue
                    provider.delete(entrySpec.remotePath).getOrThrow()
                }

                // 2. Delete child directories deepest first
                val dirEntries = entries.filter { it.node.isDirectory }.sortedByDescending { it.relativeSubPath.length }
                for (dir in dirEntries) {
                    currentCoroutineContext().ensureActive()
                    val dirSpec = NetworkPathResolver.parseRemoteSpec(dir.node) ?: continue
                    provider.delete(dirSpec.remotePath).getOrThrow()
                }

                // 3. Delete root target directory
                provider.delete(spec.remotePath).getOrThrow()
            }
        }
    }

    override suspend fun rename(target: StorageNode, newName: String): Result<StorageNode> {
        val spec = NetworkPathResolver.parseRemoteSpec(target)
            ?: return Result.failure(IOException("Not a valid remote node: ${target.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))
        return runCatching {
            NetworkRetryPolicy.executeSafely(
                kind = NetworkOperationKind.DESTRUCTIVE,
                operationName = "RENAME",
                targetPath = spec.remotePath
            ) {
                provider.rename(spec.remotePath, newName).getOrThrow()
            }
            val parentPath = spec.remotePath.substringBeforeLast('/', "")
            val newRemotePath = if (parentPath.isEmpty()) "/$newName" else "$parentPath/$newName"
            val uri = "${spec.scheme}://${spec.serverConfigId}$newRemotePath"
            target.copy(
                id = uri,
                name = newName,
                path = newRemotePath,
                uriString = uri
            )
        }
    }

    override suspend fun exists(node: StorageNode): Boolean {
        val spec = NetworkPathResolver.parseRemoteSpec(node) ?: return false
        val provider = providerResolver(spec.serverConfigId) ?: return false
        return provider.exists(spec.remotePath)
    }

    override suspend fun listChildren(targetDir: StorageNode): Result<List<StorageNode>> {
        val spec = NetworkPathResolver.parseRemoteSpec(targetDir)
            ?: return Result.failure(IOException("Not a valid remote node: ${targetDir.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))
        return runCatching {
            val list = NetworkRetryPolicy.executeSafely(
                kind = NetworkOperationKind.READ_IDEMPOTENT,
                operationName = "LIST_CHILDREN",
                targetPath = spec.remotePath
            ) {
                provider.listChildren(spec.remotePath).getOrThrow()
            }
            list.map { child ->
                val childUri = "${spec.scheme}://${spec.serverConfigId}${child.path}"
                StorageNode(
                    id = childUri,
                    name = child.name,
                    path = child.path,
                    uriString = childUri,
                    size = child.size,
                    lastModified = child.lastModified,
                    isDirectory = child.isDirectory,
                    isHidden = child.name.startsWith("."),
                    mimeType = child.mimeType,
                    isReadable = true,
                    isWritable = true
                )
            }
        }
    }

    override fun resolveChild(parentDir: StorageNode, childName: String, isDirectory: Boolean): StorageNode {
        val spec = NetworkPathResolver.parseRemoteSpec(parentDir)
        val cleanName = childName.substringAfterLast('/')
        val joinedPath = if (spec != null) {
            NetworkPathResolver.joinRemotePath(spec.remotePath, childName)
        } else {
            "${parentDir.path.trimEnd('/')}/$childName"
        }
        val uri = if (spec != null) {
            "${spec.scheme}://${spec.serverConfigId}$joinedPath"
        } else {
            joinedPath
        }

        return StorageNode(
            id = uri,
            name = cleanName,
            path = joinedPath,
            uriString = uri,
            isDirectory = isDirectory,
            isReadable = true,
            isWritable = true
        )
    }

    override suspend fun listTree(targetDir: StorageNode): Result<List<RemoteTreeEntry>> {
        val spec = NetworkPathResolver.parseRemoteSpec(targetDir)
            ?: return Result.failure(IOException("Not a valid remote node: ${targetDir.path}"))
        val provider = providerResolver(spec.serverConfigId)
            ?: return Result.failure(IOException("No active network connection for server '${spec.serverConfigId}'"))

        val results = mutableListOf<RemoteTreeEntry>()
        val queue = ArrayDeque<String>()
        queue.add(spec.remotePath)

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val currentPath = queue.removeFirst()
            val children = provider.listChildren(currentPath).getOrDefault(emptyList())

            for (child in children) {
                currentCoroutineContext().ensureActive()
                val relPath = if (child.path.startsWith(spec.remotePath)) {
                    child.path.removePrefix(spec.remotePath).trimStart('/')
                } else {
                    child.name
                }
                val childUri = "${spec.scheme}://${spec.serverConfigId}${child.path}"
                val node = StorageNode(
                    id = childUri,
                    name = child.name,
                    path = child.path,
                    uriString = childUri,
                    size = child.size,
                    lastModified = child.lastModified,
                    isDirectory = child.isDirectory,
                    isHidden = child.name.startsWith("."),
                    mimeType = child.mimeType,
                    isReadable = true,
                    isWritable = true
                )
                results.add(RemoteTreeEntry(node = node, relativeSubPath = relPath))
                if (child.isDirectory) {
                    queue.add(child.path)
                }
            }
        }
        return Result.success(results)
    }
}
