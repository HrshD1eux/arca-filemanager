package com.Hrshd1eux.filemanager.core.network.model

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

/**
 * Immutable representation of a remote file or directory on a network server.
 */
data class NetworkNode(
    /** Canonical remote path on the server (e.g. "/shares/media/file.mp4"). */
    val path: String,

    /** File or directory name. */
    val name: String,

    /** True if this node represents a remote directory. */
    val isDirectory: Boolean,

    /** Size in bytes (0L for directories or unknown stream sizes). */
    val size: Long = 0L,

    /** Timestamp of last modification in epoch milliseconds (0 if unavailable). */
    val lastModified: Long = 0L,

    /** Remote POSIX permissions string if provided by protocol (e.g. "rwxr-xr-x"). */
    val permissions: String? = null,

    /** Resolved MIME type based on file extension or HTTP Content-Type. */
    val mimeType: String? = null,

    /** The network protocol used to discover this node. */
    val protocol: NetworkProtocol,

    /** Identifier of the server configuration providing this node. */
    val serverConfigId: String
) {
    val isHidden: Boolean
        get() = name.startsWith(".")

    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "")

    /**
     * Converts this remote network node into a standard StorageNode
     * for seamless presentation in the file browser and search UI.
     */
    fun toStorageNode(): StorageNode {
        return StorageNode(
            id = "${protocol.scheme}://$serverConfigId$path",
            name = name,
            path = path,
            uriString = "${protocol.scheme}://$serverConfigId$path",
            size = size,
            lastModified = lastModified,
            isDirectory = isDirectory,
            isHidden = isHidden,
            mimeType = mimeType,
            isReadable = true,
            isWritable = true
        )
    }
}
