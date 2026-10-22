package com.Hrshd1eux.filemanager.core.cleaner.scanner

import android.os.Environment
import com.Hrshd1eux.filemanager.core.cleaner.model.LargeFileThreshold
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.SafeFileWalker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class LargeFilesScanner(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun scanLargeFiles(
        rootPath: String? = null,
        threshold: LargeFileThreshold = LargeFileThreshold.MB_100
    ): List<StorageNode> = withContext(ioDispatcher) {
        val targetPath = rootPath ?: runCatching {
            Environment.getExternalStorageDirectory().absolutePath
        }.getOrDefault("/storage/emulated/0")

        val root = File(targetPath)
        if (!root.exists() || !root.canRead()) {
            return@withContext emptyList()
        }

        val largeFiles = mutableListOf<StorageNode>()

        runCatching {
            SafeFileWalker.walkSafe(root, includeHidden = false)
                .filter { it.isFile && it.length() >= threshold.minBytes }
                .forEach { file ->
                    largeFiles.add(file.toStorageNode())
                }
        }

        largeFiles.sortedByDescending { it.size }
    }

    private fun File.toStorageNode(): StorageNode {
        val mime = if (isDirectory) null else MimeTypeResolver.getMimeType(extension)
        return StorageNode(
            id = absolutePath,
            name = name.ifEmpty { absolutePath },
            path = absolutePath,
            isDirectory = isDirectory,
            size = length(),
            lastModified = lastModified(),
            mimeType = mime
        )
    }
}
