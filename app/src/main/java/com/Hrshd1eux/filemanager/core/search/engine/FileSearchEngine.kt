package com.Hrshd1eux.filemanager.core.search.engine

import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.DateFilter
import com.Hrshd1eux.filemanager.core.search.model.FileTypeScope
import com.Hrshd1eux.filemanager.core.search.model.MatchType
import com.Hrshd1eux.filemanager.core.search.model.SearchCriteria
import com.Hrshd1eux.filemanager.core.search.model.SearchSortBy
import com.Hrshd1eux.filemanager.core.search.model.SizeFilter
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.SafeFileWalker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.Calendar
import java.util.regex.Pattern

class FileSearchEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "heic", "heif", "ico")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v")
    private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "mid")
    private val docExtensions = setOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml", "html", "md")
    private val archiveExtensions = setOf("zip", "tar", "gz", "7z", "rar", "bz2", "xz", "iso", "apk", "jar")
    private val apkExtensions = setOf("apk", "xapk", "apks", "apkm", "aab")

    fun search(criteria: SearchCriteria): Flow<List<StorageNode>> = flow {
        val envPath = runCatching { android.os.Environment.getExternalStorageDirectory()?.absolutePath }.getOrNull()
        val rootPath = if (criteria.searchEverywhere) {
            if (!envPath.isNullOrEmpty() && File(envPath).exists()) envPath else criteria.rootPath.ifEmpty { "/" }
        } else {
            criteria.rootPath.ifEmpty { envPath ?: "/" }
        }
        val rootFile = File(rootPath)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            emit(emptyList())
            return@flow
        }

        val rawQuery = criteria.query.trim()
        val now = System.currentTimeMillis()
        val dateThreshold = calculateDateThreshold(criteria.dateFilter, now)

        val batch = mutableListOf<StorageNode>()
        var lastEmitTime = System.currentTimeMillis()

        try {
            SafeFileWalker.walkSafe(rootFile, includeHidden = criteria.includeHidden)
                .filter { dirOrFile ->
                    val normalized = dirOrFile.path.replace('\\', '/')
                    !dirOrFile.name.equals("vault", ignoreCase = true) && !normalized.contains("/vault/") && !normalized.contains("/.vault/")
                }
                .forEach { file ->
                    currentCoroutineContext().ensureActive()

                    if (file != rootFile && matchesCriteria(file, rawQuery, criteria, dateThreshold)) {
                        batch.add(file.toStorageNode())

                        val currentTime = System.currentTimeMillis()
                        if (batch.size >= 25 || (currentTime - lastEmitTime >= 100 && batch.isNotEmpty())) {
                            emit(sortNodes(batch, criteria.sortBy, rawQuery))
                            lastEmitTime = currentTime
                        }
                    }
                }
        } catch (_: Exception) {
            // Directory permission restrictions during traversal
        }

        // Final emit of all collected items sorted
        emit(sortNodes(batch, criteria.sortBy, rawQuery))
    }.flowOn(ioDispatcher)

    private fun sortNodes(items: List<StorageNode>, sortBy: SearchSortBy, query: String): List<StorageNode> {
        return when (sortBy) {
            SearchSortBy.RELEVANCE -> items.sortedWith(
                compareByDescending<StorageNode> { it.name.equals(query, ignoreCase = true) }
                    .thenByDescending { it.name.startsWith(query, ignoreCase = true) }
                    .thenByDescending { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            SearchSortBy.NAME_ASC -> items.sortedBy { it.name.lowercase() }
            SearchSortBy.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
            SearchSortBy.DATE_DESC -> items.sortedByDescending { it.lastModified }
            SearchSortBy.DATE_ASC -> items.sortedBy { it.lastModified }
            SearchSortBy.SIZE_DESC -> items.sortedByDescending { it.size }
            SearchSortBy.SIZE_ASC -> items.sortedBy { it.size }
        }
    }

    private fun matchesCriteria(
        file: File,
        rawQuery: String,
        criteria: SearchCriteria,
        dateThreshold: Long
    ): Boolean {
        val normalizedPath = file.path.replace('\\', '/')
        if (file.name.equals("vault", ignoreCase = true) ||
            normalizedPath.contains("/vault/") ||
            normalizedPath.endsWith("/vault") ||
            normalizedPath.contains("/.vault/")
        ) {
            return false
        }

        if (!criteria.includeHidden && file.name.startsWith(".")) {
            return false
        }

        // FileTypeScope Filter
        when (criteria.fileTypeScope) {
            FileTypeScope.ALL -> {}
            FileTypeScope.FILES_ONLY -> if (file.isDirectory) return false
            FileTypeScope.FOLDERS_ONLY -> if (!file.isDirectory) return false
        }

        // Advanced Name / Query Matching
        if (rawQuery.isNotEmpty()) {
            val fileName = if (criteria.matchCase) file.name else file.name.lowercase()
            val queryText = if (criteria.matchCase) rawQuery else rawQuery.lowercase()

            val matches = when (criteria.matchType) {
                MatchType.CONTAINS -> {
                    val ext = if (criteria.matchCase) file.extension else file.extension.lowercase()
                    fileName.contains(queryText) || (!file.isDirectory && ext.isNotEmpty() && ext.contains(queryText.removePrefix(".")))
                }
                MatchType.STARTS_WITH -> {
                    fileName.startsWith(queryText)
                }
                MatchType.EXACT -> {
                    fileName == queryText || (!file.isDirectory && file.nameWithoutExtension.let { if (criteria.matchCase) it else it.lowercase() } == queryText)
                }
                MatchType.WILDCARD -> {
                    val regexString = "^" + Pattern.quote(queryText)
                        .replace("*", "\\E.*\\Q")
                        .replace("?", "\\E.\\Q") + "$"
                    runCatching {
                        val flags = if (criteria.matchCase) 0 else Pattern.CASE_INSENSITIVE
                        Pattern.compile(regexString, flags).matcher(fileName).find()
                    }.getOrDefault(fileName.contains(queryText))
                }
            }
            if (!matches) return false
        }

        // Specific Extension Filter
        if (!criteria.extensionFilter.isNullOrBlank()) {
            if (file.isDirectory) return false
            val ext = file.extension.lowercase()
            val expected = criteria.extensionFilter.lowercase().removePrefix(".")
            if (ext != expected) return false
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
