package com.Hrshd1eux.filemanager.core.network.bridge

import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.provider.NetworkProvider
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.model.StorageStats
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider

/**
 * Adapter bridging a NetworkProvider into the generic StorageProvider interface.
 *
 * ARCHITECTURAL ISOLATION:
 * The offline storage core (POSIX, SAF, Vault, Operations) has zero knowledge of
 * network sockets or protocols. This bridge adapts remote network trees into standard
 * StorageNode representations so that higher-level UI (file browser, picker, search)
 * can interact with remote shares without contaminating offline engines.
 */
class NetworkStorageBridge(
    val networkProvider: NetworkProvider
) : StorageProvider {

    override val providerId: String
        get() = "network_${networkProvider.protocol.scheme}_${networkProvider.config.id}"

    override val displayName: String
        get() = "${networkProvider.config.name} (${networkProvider.protocol.displayName})"

    override fun getRootNode(): StorageNode {
        val rootPath = if (networkProvider.config.basePath.startsWith("/")) {
            networkProvider.config.basePath
        } else {
            "/${networkProvider.config.basePath}"
        }
        val cleanName = rootPath.substringAfterLast('/').ifEmpty { networkProvider.config.name }

        return StorageNode(
            id = "${networkProvider.protocol.scheme}://${networkProvider.config.id}$rootPath",
            name = cleanName,
            path = rootPath,
            uriString = networkProvider.config.toSanitizedUriString(),
            size = 0L,
            lastModified = 0L,
            isDirectory = true,
            isHidden = false,
            mimeType = null,
            isReadable = true,
            isWritable = true
        )
    }

    override suspend fun listChildren(targetId: String): Result<List<StorageNode>> {
        val remotePath = extractRemotePath(targetId)
        return networkProvider.listChildren(remotePath).map { nodes ->
            nodes.map { it.toStorageNode() }
        }
    }

    override suspend fun getNode(targetId: String): Result<StorageNode> {
        val remotePath = extractRemotePath(targetId)
        return networkProvider.getNode(remotePath).map { it.toStorageNode() }
    }

    override suspend fun getStorageStats(): Result<StorageStats> {
        return Result.success(
            StorageStats(
                totalBytes = 0L,
                freeBytes = 0L
            )
        )
    }

    override suspend fun exists(targetId: String): Boolean {
        val remotePath = extractRemotePath(targetId)
        return networkProvider.exists(remotePath)
    }

    override fun hasPermission(targetId: String): Boolean {
        return networkProvider.connectionState.value is NetworkConnectionState.Authenticated
    }

    /**
     * Extracts canonical remote path from targetId or URI.
     */
    private fun extractRemotePath(targetId: String): String {
        return if (targetId.startsWith("${networkProvider.protocol.scheme}://")) {
            val stripped = targetId.removePrefix("${networkProvider.protocol.scheme}://")
            val pathIndex = stripped.indexOf('/')
            if (pathIndex != -1) stripped.substring(pathIndex) else "/"
        } else {
            if (targetId.startsWith("/")) targetId else "/$targetId"
        }
    }
}
