package com.Hrshd1eux.filemanager.core.storage.analyzer

import android.os.Environment
import android.os.StatFs
import com.Hrshd1eux.filemanager.core.storage.model.CategorySize
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.util.SafeFileWalker
import com.Hrshd1eux.filemanager.core.storage.model.StorageCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalStorageAnalyzer(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val installedAppsRepository: InstalledAppsRepository? = null,
    private val systemStorageRepository: SystemStorageRepository? = null
) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "dng", "svg", "bmp")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "m4v")
    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub", "csv")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk")

    suspend fun analyzeStorage(rootPath: String? = null): StorageBreakdown = withContext(ioDispatcher) {
        val defaultPath = runCatching {
            Environment.getExternalStorageDirectory().absolutePath
        }.getOrDefault("/storage/emulated/0")
        val isDefaultStorage = rootPath == null || rootPath == defaultPath
        val targetPath = rootPath ?: defaultPath

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
        var otherFilesBytes = 0L
        var otherFilesCount = 0

        runCatching {
            SafeFileWalker.walkSafe(rootFile, includeHidden = false)
                .filter { it.isFile }
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
                        else -> {
                            otherFilesBytes += length
                            otherFilesCount++
                        }
                    }
                }
        }

        var appBytes = 0L
        var appCount = 0
        var systemBytes = 0L

        if (isDefaultStorage) {
            runCatching {
                val appsRepo = installedAppsRepository ?: InstalledAppsRepository.getInstance()
                val report = appsRepo.getInstalledApps(forceRefresh = false)
                appBytes = report.totalMeasurableBytes
                appCount = report.totalAppCount
            }

            runCatching {
                val systemDir = Environment.getRootDirectory()
                if (systemDir.exists() && systemDir.canRead()) {
                    val stat = StatFs(systemDir.absolutePath)
                    systemBytes = (stat.totalBytes - stat.availableBytes).coerceAtLeast(0L)
                }
            }
        }

        val classifiedFilesSum = imageBytes + videoBytes + audioBytes + docBytes + archiveBytes
        val otherBytes = if (isDefaultStorage) {
            val totalKnown = classifiedFilesSum + appBytes
            if (usedBytes > totalKnown) (usedBytes - totalKnown).coerceAtLeast(otherFilesBytes) else otherFilesBytes
        } else {
            if (usedBytes > classifiedFilesSum) usedBytes - classifiedFilesSum else otherFilesBytes
        }

        val categories = mutableListOf(
            CategorySize(StorageCategory.IMAGES, imageBytes, imageCount),
            CategorySize(StorageCategory.VIDEOS, videoBytes, videoCount),
            CategorySize(StorageCategory.AUDIO, audioBytes, audioCount),
            CategorySize(StorageCategory.DOCUMENTS, docBytes, docCount),
            CategorySize(StorageCategory.ARCHIVES, archiveBytes, archiveCount)
        )

        if (isDefaultStorage) {
            if (appBytes > 0L || appCount > 0) {
                categories.add(CategorySize(StorageCategory.APPS, appBytes, appCount))
            }
            if (systemBytes > 0L) {
                categories.add(CategorySize(StorageCategory.SYSTEM, systemBytes, 0))
            }
        }

        categories.add(CategorySize(StorageCategory.OTHER, otherBytes, otherFilesCount))

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
