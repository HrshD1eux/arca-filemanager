package com.Hrshd1eux.filemanager.core.storage.model

/**
 * Universal, immutable representation of a file or directory.
 * Unifies local POSIX storage, SAF document trees, and future archive/vault/remote nodes.
 */
data class StorageNode(
    val id: String,
    val name: String,
    val path: String,
    val uriString: String? = null,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val isHidden: Boolean = false,
    val mimeType: String? = null,
    val isReadable: Boolean = true,
    val isWritable: Boolean = true
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "")
}
