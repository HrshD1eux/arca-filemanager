package com.Hrshd1eux.filemanager.feature.category

import android.os.Environment
import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.SafeFileWalker
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class CategoryRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun getFilesForCategory(
        category: CategoryFilter,
        rootPath: String? = null
    ): List<StorageNode> = withContext(ioDispatcher) {
        val basePath = rootPath ?: runCatching {
            Environment.getExternalStorageDirectory().absolutePath
        }.getOrDefault("/storage/emulated/0")

        val rootFile = File(basePath)
        if (!rootFile.exists()) {
            throw IOException("Storage location is unavailable or removed: $basePath")
        }
        if (!rootFile.canRead()) {
            throw SecurityException("Storage location is not accessible: $basePath")
        }

        if (category == CategoryFilter.DOWNLOADS) {
            val downloadPath = runCatching {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            }.getOrNull() ?: "$basePath/Download"

            val downloadDir = File(downloadPath)
            if (!downloadDir.exists() || !downloadDir.canRead()) {
                return@withContext emptyList()
            }
            val downloadItems = mutableListOf<StorageNode>()
            runCatching {
                SafeFileWalker.walkSafe(downloadDir, includeHidden = false, maxDepth = 10)
                    .filter { it.isFile }
                    .forEach { file ->
                        downloadItems.add(file.toStorageNode())
                    }
            }
            return@withContext downloadItems
        }

        val result = mutableListOf<StorageNode>()
        runCatching {
            SafeFileWalker.walkSafe(rootFile, includeHidden = false, maxDepth = 12)
                .filter { it.isFile }
                .forEach { file ->
                    // Memory optimization: ONLY allocate StorageNode for matching files!
                    if (matchesCategory(file, category)) {
                        result.add(file.toStorageNode())
                    }
                }
        }
        result
    }

    fun matchesCategory(file: File, category: CategoryFilter): Boolean {
        val fileName = file.name
        return when (category) {
            CategoryFilter.ALL -> true
            CategoryFilter.IMAGES -> FileTypeResolver.isImage(fileName)
            CategoryFilter.VIDEOS -> FileTypeResolver.isVideo(fileName)
            CategoryFilter.AUDIO -> FileTypeResolver.isAudio(fileName)
            CategoryFilter.DOCUMENTS -> FileTypeResolver.isDocument(fileName)
            CategoryFilter.ARCHIVES -> FileTypeResolver.isArchive(fileName)
            CategoryFilter.APKS -> FileTypeResolver.isPackage(fileName)
            CategoryFilter.DOWNLOADS -> file.path.contains("/Download", ignoreCase = true)
            CategoryFilter.OTHER -> !FileTypeResolver.isImage(fileName) &&
                    !FileTypeResolver.isVideo(fileName) &&
                    !FileTypeResolver.isAudio(fileName) &&
                    !FileTypeResolver.isDocument(fileName) &&
                    !FileTypeResolver.isArchive(fileName) &&
                    !FileTypeResolver.isPackage(fileName)
        }
    }

    fun sortItems(items: List<StorageNode>, order: SortOrder): List<StorageNode> {
        val comparator = when (order.field) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.SIZE -> compareBy { it.size }
            SortField.DATE -> compareBy { it.lastModified }
            SortField.TYPE -> compareBy<StorageNode> { it.name.substringAfterLast('.', "").lowercase() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        return if (order.direction == SortDirection.ASCENDING) {
            items.sortedWith(comparator)
        } else {
            items.sortedWith(comparator.reversed())
        }
    }

    suspend fun deleteFile(node: StorageNode): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val file = File(node.path)
            if (!file.exists()) error("File does not exist")
            if (!file.delete()) error("Failed to delete file")
        }
    }

    suspend fun deleteFiles(nodes: List<StorageNode>): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            var deletedCount = 0
            for (node in nodes) {
                val file = File(node.path)
                if (file.exists() && file.delete()) {
                    deletedCount++
                }
            }
            deletedCount
        }
    }

    suspend fun renameFile(node: StorageNode, newName: String): Result<StorageNode> = withContext(ioDispatcher) {
        runCatching {
            val currentFile = File(node.path)
            if (!currentFile.exists()) error("File does not exist")
            val destFile = File(currentFile.parentFile, newName)
            if (destFile.exists()) error("File with name $newName already exists")
            if (!currentFile.renameTo(destFile)) error("Failed to rename file")
            destFile.toStorageNode()
        }
    }

    private fun File.toStorageNode(): StorageNode {
        val mime = if (isDirectory) null else (MimeTypeResolver.getMimeType(extension) ?: FileTypeResolver.resolveMimeType(name))
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
