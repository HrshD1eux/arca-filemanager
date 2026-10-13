package com.Hrshd1eux.filemanager.core.search.engine

import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.DateFilter
import com.Hrshd1eux.filemanager.core.search.model.SearchCriteria
import com.Hrshd1eux.filemanager.core.search.model.SizeFilter
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.Calendar

class FileSearchEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "heic", "heif", "ico")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v")
    private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "mid")
    private val docExtensions = setOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml", "html", "md")
    private val archiveExtensions = setOf("zip", "tar", "gz", "7z", "rar", "bz2", "xz", "iso", "apk", "jar")
    private val apkExtensions = setOf("apk", "xapk", "apks")

    fun search(criteria: SearchCriteria): Flow<List<StorageNode>> = flow {
        val rootFile = File(criteria.rootPath)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            emit(emptyList())
            return@flow
        }

        val cleanQuery = criteria.query.trim().lowercase()
        val now = System.currentTimeMillis()
        val dateThreshold = calculateDateThreshold(criteria.dateFilter, now)

        val batch = mutableListOf<StorageNode>()
        var lastEmitTime = System.currentTimeMillis()

        try {
            rootFile.walkTopDown()
                .onEnter { it.canRead() }
                .forEach { file ->
                    currentCoroutineContext().ensureActive()

                    if (file != rootFile && matchesCriteria(file, cleanQuery, criteria, dateThreshold)) {
                        batch.add(file.toStorageNode())

                        val currentTime = System.currentTimeMillis()
                        if (batch.size >= 25 || (currentTime - lastEmitTime >= 100 && batch.isNotEmpty())) {
                            emit(batch.toList())
                            lastEmitTime = currentTime
                        }
                    }
                }
        } catch (_: Exception) {
            // Directory permission restrictions during traversal
        }

        // Final emit of all collected items
        emit(batch.toList())
    }.flowOn(ioDispatcher)

    private fun matchesCriteria(
        file: File,
        cleanQuery: String,
        criteria: SearchCriteria,
        dateThreshold: Long
    ): Boolean {
        if (!criteria.includeHidden && file.name.startsWith(".")) {
            return false
        }

        val fileName = file.name.lowercase()

        // Name / Substring / Extension matching
        if (cleanQuery.isNotEmpty()) {
            val matchesName = fileName.contains(cleanQuery)
            val extension = file.extension.lowercase()
            val matchesExt = extension.isNotEmpty() && (extension == cleanQuery.removePrefix(".") || extension.contains(cleanQuery))
            if (!matchesName && !matchesExt) {
                return false
            }
        }

        // Category Filter
        if (criteria.category != CategoryFilter.ALL) {
            if (file.isDirectory) return false
            val ext = file.extension.lowercase()
            val matchesCategory = when (criteria.category) {
                CategoryFilter.ALL -> true
                CategoryFilter.IMAGES -> imageExtensions.contains(ext)
                CategoryFilter.VIDEOS -> videoExtensions.contains(ext)
                CategoryFilter.AUDIO -> audioExtensions.contains(ext)
                CategoryFilter.DOCUMENTS -> docExtensions.contains(ext)
                CategoryFilter.DOWNLOADS -> file.path.contains("/Download", ignoreCase = true)
                CategoryFilter.ARCHIVES -> archiveExtensions.contains(ext)
                CategoryFilter.APKS -> apkExtensions.contains(ext)
                CategoryFilter.OTHER -> !imageExtensions.contains(ext) &&
                        !videoExtensions.contains(ext) &&
                        !audioExtensions.contains(ext) &&
                        !docExtensions.contains(ext) &&
                        !archiveExtensions.contains(ext) &&
                        !apkExtensions.contains(ext)
            }
            if (!matchesCategory) return false
        }

        // Size Filter (applies to files only)
        if (criteria.sizeFilter != SizeFilter.ANY) {
            if (file.isDirectory) return false
            val size = file.length()
            val matchesSize = when (criteria.sizeFilter) {
                SizeFilter.ANY -> true
                SizeFilter.LESS_THAN_1MB -> size < 1_048_576L
                SizeFilter.BETWEEN_1MB_AND_100MB -> size in 1_048_576L..104_857_600L
                SizeFilter.GREATER_THAN_100MB -> size > 104_857_600L
            }
            if (!matchesSize) return false
        }

        // Date Filter
        if (criteria.dateFilter != DateFilter.ANY) {
            val lastModified = file.lastModified()
            if (lastModified < dateThreshold) return false
        }

        return true
    }

    private fun calculateDateThreshold(filter: DateFilter, now: Long): Long {
        if (filter == DateFilter.ANY) return 0L

        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        return when (filter) {
            DateFilter.ANY -> 0L
            DateFilter.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            DateFilter.THIS_WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                calendar.timeInMillis
            }
            DateFilter.THIS_MONTH -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                calendar.timeInMillis
            }
            DateFilter.THIS_YEAR -> {
                calendar.add(Calendar.DAY_OF_YEAR, -365)
                calendar.timeInMillis
            }
        }
    }

    private fun File.toStorageNode(): StorageNode {
        return StorageNode(
            id = absolutePath,
            name = name,
            path = absolutePath,
            size = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            isDirectory = isDirectory,
            mimeType = if (isDirectory) null else MimeTypeResolver.getMimeType(extension)
        )
    }
}
