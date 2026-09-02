package com.Hrshd1eux.filemanager.core.storage.provider

import android.os.Environment
import android.os.StatFs
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * High-performance POSIX / java.io.File implementation of StorageProvider.
 * Used for primary internal storage and direct filesystem paths.
 */
class LocalFileSystemProvider(
    private val rootDirectory: File = Environment.getExternalStorageDirectory(),
    override val displayName: String = "Internal Storage"
) : StorageProvider {

    override val providerId: String = "local_fs"

    override fun getRootNode(): StorageNode {
        return rootDirectory.toStorageNode()
    }

    override suspend fun listChildren(targetId: String): Result<List<StorageNode>> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = File(targetId)
            if (!targetFile.exists()) {
                throw IOException("Path does not exist: $targetId")
            }
            if (!targetFile.isDirectory) {
                throw IOException("Path is not a directory: $targetId")
            }
            if (!targetFile.canRead()) {
                throw SecurityException("Permission denied reading directory: $targetId")
            }

            val files = targetFile.listFiles() ?: throw IOException("Failed to list contents of: $targetId")

            files.map { it.toStorageNode() }
        }
    }

    override suspend fun getNode(targetId: String): Result<StorageNode> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = File(targetId)
            if (!targetFile.exists()) {
                throw IOException("File does not exist: $targetId")
            }
            targetFile.toStorageNode()
        }
    }

    override suspend fun getStorageStats(): Result<StorageStats> = withContext(Dispatchers.IO) {
        runCatching {
            val statFs = StatFs(rootDirectory.absolutePath)
            StorageStats(
                totalBytes = statFs.totalBytes,
                freeBytes = statFs.availableBytes
            )
        }
    }

    override suspend fun exists(targetId: String): Boolean = withContext(Dispatchers.IO) {
        File(targetId).exists()
    }

    override fun hasPermission(targetId: String): Boolean {
        val file = File(targetId)
        return file.exists() && file.canRead()
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
