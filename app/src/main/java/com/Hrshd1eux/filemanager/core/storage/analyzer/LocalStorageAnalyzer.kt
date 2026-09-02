package com.Hrshd1eux.filemanager.core.storage.analyzer

import android.os.Environment
import android.os.StatFs
import com.Hrshd1eux.filemanager.core.storage.model.CategorySize
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.model.StorageCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalStorageAnalyzer(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "dng", "svg", "bmp")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "m4v")
    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub", "csv")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk")

    suspend fun analyzeStorage(rootPath: String? = null): StorageBreakdown = withContext(ioDispatcher) {
        val targetPath = rootPath ?: runCatching {
            Environment.getExternalStorageDirectory().absolutePath
        }.getOrDefault("/storage/emulated/0")

        val rootFile = File(targetPath)
        if (!rootFile.exists() || !rootFile.canRead()) {
            return@withContext StorageBreakdown()
        }

        // 1. Calculate StatFs metrics
        val (totalBytes, freeBytes, usedBytes) = runCatching {
            val stat = StatFs(targetPath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = total - free
            Triple(total, free, used)
        }.getOrDefault(Triple(0L, 0L, 0L))

        // 2. Scan categorized directories
        var imageBytes = 0L
        var imageCount = 0
        var videoBytes = 0L
        var videoCount = 0
        var audioBytes = 0L
        var audioCount = 0
        var docBytes = 0L
        var docCount = 0
        var archiveBytes = 0L
        var archiveCount = 0

        runCatching {
            rootFile.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") }
                .filter { it.isFile && !it.name.startsWith(".") }
                .forEach { file ->
                    val ext = file.extension.lowercase()
                    val length = file.length()
                    when {
                        ext in imageExtensions -> {
                            imageBytes += length
                            imageCount++
                        }
                        ext in videoExtensions -> {
                            videoBytes += length
                            videoCount++
                        }
                        ext in audioExtensions -> {
                            audioBytes += length
                            audioCount++
                        }
                        ext in documentExtensions -> {
                            docBytes += length
                            docCount++
                        }
                        ext in archiveExtensions -> {
                            archiveBytes += length
                            archiveCount++
                        }
                    }
                }
        }

        val classifiedSum = imageBytes + videoBytes + audioBytes + docBytes + archiveBytes
        val otherBytes = if (usedBytes > classifiedSum) usedBytes - classifiedSum else 0L

        val categories = listOf(
            CategorySize(StorageCategory.IMAGES, imageBytes, imageCount),
            CategorySize(StorageCategory.VIDEOS, videoBytes, videoCount),
            CategorySize(StorageCategory.AUDIO, audioBytes, audioCount),
            CategorySize(StorageCategory.DOCUMENTS, docBytes, docCount),
            CategorySize(StorageCategory.ARCHIVES, archiveBytes, archiveCount),
            CategorySize(StorageCategory.OTHER, otherBytes, 0)
        )

        StorageBreakdown(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            categories = categories,
            isCalculating = false
        )
    }

    fun classifyFile(file: File): StorageCategory {
        val ext = file.extension.lowercase()
        return when {
            ext in imageExtensions -> StorageCategory.IMAGES
            ext in videoExtensions -> StorageCategory.VIDEOS
            ext in audioExtensions -> StorageCategory.AUDIO
            ext in documentExtensions -> StorageCategory.DOCUMENTS
            ext in archiveExtensions -> StorageCategory.ARCHIVES
            else -> StorageCategory.OTHER
        }
    }
}
