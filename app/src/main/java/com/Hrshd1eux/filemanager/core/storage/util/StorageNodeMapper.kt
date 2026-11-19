package com.Hrshd1eux.filemanager.core.storage.util

import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import java.io.File

/**
 * Canonical mapper converting standard [File] objects to unified [StorageNode] domain models.
 * Eliminates redundant private conversion methods across engine and viewer subsystems.
 */
fun File.toStorageNode(): StorageNode {
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
        isHidden = name.startsWith("."),
        mimeType = mime,
        isReadable = canRead(),
        isWritable = canWrite()
    )
}
