package com.Hrshd1eux.filemanager.core.network.util

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

/**
 * Utility for parsing and identifying remote network storage nodes.
 */
object NetworkPathResolver {

    private val SUPPORTED_SCHEMES = setOf("sftp", "ftp", "ftps", "smb", "webdav")

    data class RemoteNodeSpec(
        val scheme: String,
        val serverConfigId: String,
        val remotePath: String
    )

    /**
     * Parses a StorageNode into RemoteNodeSpec if it represents a remote network node.
     * Returns null if the node represents local storage.
     */
    fun parseRemoteSpec(node: StorageNode): RemoteNodeSpec? {
        val uri = node.uriString ?: node.id
        if (!uri.contains("://") || uri.startsWith("file://")) {
            return null
        }

        val scheme = uri.substringBefore("://").lowercase()
        if (scheme !in SUPPORTED_SCHEMES) {
            return null
        }

        val rest = uri.substringAfter("://")
        val slashIndex = rest.indexOf('/')
        val serverConfigId = if (slashIndex != -1) rest.substring(0, slashIndex) else rest
        val rawPath = if (slashIndex != -1) rest.substring(slashIndex) else node.path
        val normalizedPath = when {
            rawPath.isEmpty() -> "/"
            rawPath.startsWith("/") -> rawPath
            else -> "/$rawPath"
        }

        return RemoteNodeSpec(
            scheme = scheme,
            serverConfigId = serverConfigId,
            remotePath = normalizedPath
        )
    }

    /**
     * Returns true if the given StorageNode resides on a remote network share.
     */
    fun isRemote(node: StorageNode): Boolean = parseRemoteSpec(node) != null

    /**
     * Normalizes a child path under a remote parent directory.
     */
    fun joinRemotePath(parentDir: String, childName: String): String {
        val cleanParent = if (parentDir.endsWith("/")) parentDir.dropLast(1) else parentDir
        val cleanChild = if (childName.startsWith("/")) childName.drop(1) else childName
        return if (cleanParent.isEmpty()) "/$cleanChild" else "$cleanParent/$cleanChild"
    }
}
