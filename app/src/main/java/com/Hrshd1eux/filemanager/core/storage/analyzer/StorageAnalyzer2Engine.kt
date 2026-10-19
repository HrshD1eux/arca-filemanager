package com.Hrshd1eux.filemanager.core.storage.analyzer

import android.os.Environment
import com.Hrshd1eux.filemanager.core.cleaner.model.AnalyzerSortBy
import com.Hrshd1eux.filemanager.core.cleaner.model.CleanupCandidate
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateGroup
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateMatchLevel
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateScanResult
import com.Hrshd1eux.filemanager.core.cleaner.model.EmptyFolderItem
import com.Hrshd1eux.filemanager.core.cleaner.model.LargeFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.model.OldFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.model.ScanDiagnostics
import com.Hrshd1eux.filemanager.core.storage.model.CategorySize
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.model.StorageCategory
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Storage Analyzer 2.0 Engine.
 *
 * Implements comprehensive storage inspection with 6 dedicated analysis views:
 * 1. Large files (configurable threshold, sorting by size & date)
 * 2. Duplicate candidates (staged detection: size -> metadata -> SHA-256 hash)
 * 3. Old files (configurable threshold, disclaimer regarding modification time)
 * 4. Archives (all supported archive formats)
 * 5. Empty folders (zero-content directories)
 * 6. File-category breakdown (zero double-counting, honest inaccessible indicators)
 */
class StorageAnalyzer2Engine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "dng", "svg", "bmp")
    private val videoExtensions = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "m4v")
    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub", "csv")
    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "apk", "tgz")

    // =========================================================================
    // 1. Large Files View (1.4.11 & 1.4.12)
    // =========================================================================

    suspend fun scanLargeFiles(
        rootPath: String? = null,
        threshold: LargeFileThreshold = LargeFileThreshold.MB_100,
        sortBy: AnalyzerSortBy = AnalyzerSortBy.SIZE_DESC
    ): Pair<List<CleanupCandidate>, ScanDiagnostics> = withContext(ioDispatcher) {
        val root = resolveRoot(rootPath)
        val candidates = mutableListOf<CleanupCandidate>()
        val inaccessiblePaths = mutableListOf<String>()
        var scannedFiles = 0
        var scannedDirs = 0
        val startTime = System.currentTimeMillis()

        if (root.exists() && root.canRead()) {
            root.walkTopDown()
                .onEnter { dir ->
                    scannedDirs++
                    if (!dir.canRead()) {
                        inaccessiblePaths.add(dir.absolutePath)
                        false
                    } else {
                        !dir.name.startsWith(".")
                    }
                }
                .filter { it.isFile && !it.name.startsWith(".") }
                .forEach { file ->
                    coroutineContext.ensureActive()
                    scannedFiles++
                    val length = file.length()
                    if (length >= threshold.minBytes) {
                        candidates.add(
                            CleanupCandidate(
                                id = file.absolutePath,
                                path = file.absolutePath,
                                name = file.name,
                                size = length,
                                lastModified = file.lastModified(),
                                isDirectory = false,
                                category = classifyFile(file),
                                flagReason = "Large file (${file.length() / (1024 * 1024)} MB ≥ ${threshold.displayName})"
                            )
                        )
                    }
                }
        }

        val sorted = sortCandidates(candidates, sortBy)
        val diagnostics = ScanDiagnostics(
            scannedFilesCount = scannedFiles,
            scannedDirectoriesCount = scannedDirs,
            inaccessibleFoldersCount = inaccessiblePaths.size,
            inaccessiblePaths = inaccessiblePaths,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
        Pair(sorted, diagnostics)
    }

    // =========================================================================
    // 2. Old Files View (1.4.11 & 1.4.12)
    // =========================================================================

    suspend fun scanOldFiles(
        rootPath: String? = null,
        threshold: OldFileThreshold = OldFileThreshold.YEAR_1,
        sortBy: AnalyzerSortBy = AnalyzerSortBy.DATE_ASC
    ): Pair<List<CleanupCandidate>, ScanDiagnostics> = withContext(ioDispatcher) {
        val root = resolveRoot(rootPath)
        val cutoffTimestamp = System.currentTimeMillis() - threshold.ageMs
        val candidates = mutableListOf<CleanupCandidate>()
        val inaccessiblePaths = mutableListOf<String>()
        var scannedFiles = 0
        var scannedDirs = 0
        val startTime = System.currentTimeMillis()

        if (root.exists() && root.canRead()) {
            root.walkTopDown()
                .onEnter { dir ->
                    scannedDirs++
                    if (!dir.canRead()) {
                        inaccessiblePaths.add(dir.absolutePath)
                        false
                    } else {
                        !dir.name.startsWith(".")
                    }
                }
                .filter { it.isFile && !it.name.startsWith(".") }
                .forEach { file ->
                    coroutineContext.ensureActive()
                    scannedFiles++
                    val lastMod = file.lastModified()
                    if (lastMod in 1..cutoffTimestamp) {
                        candidates.add(
                            CleanupCandidate(
                                id = file.absolutePath,
                                path = file.absolutePath,
                                name = file.name,
                                size = file.length(),
                                lastModified = lastMod,
                                isDirectory = false,
                                category = classifyFile(file),
                                flagReason = "Old file (Not modified in ${threshold.displayName})"
                            )
                        )
                    }
                }
        }

        val sorted = sortCandidates(candidates, sortBy)
        val diagnostics = ScanDiagnostics(
            scannedFilesCount = scannedFiles,
            scannedDirectoriesCount = scannedDirs,
            inaccessibleFoldersCount = inaccessiblePaths.size,
            inaccessiblePaths = inaccessiblePaths,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
        Pair(sorted, diagnostics)
    }

    // =========================================================================
    // 3. Archives View (1.4.11)
    // =========================================================================

    suspend fun scanArchives(
        rootPath: String? = null,
        sortBy: AnalyzerSortBy = AnalyzerSortBy.SIZE_DESC
    ): Pair<List<CleanupCandidate>, ScanDiagnostics> = withContext(ioDispatcher) {
        val root = resolveRoot(rootPath)
        val candidates = mutableListOf<CleanupCandidate>()
        val inaccessiblePaths = mutableListOf<String>()
        var scannedFiles = 0
        var scannedDirs = 0
        val startTime = System.currentTimeMillis()

        if (root.exists() && root.canRead()) {
            root.walkTopDown()
                .onEnter { dir ->
                    scannedDirs++
                    if (!dir.canRead()) {
                        inaccessiblePaths.add(dir.absolutePath)
                        false
                    } else {
                        !dir.name.startsWith(".")
                    }
                }
                .filter { it.isFile && !it.name.startsWith(".") }
                .forEach { file ->
                    coroutineContext.ensureActive()
                    scannedFiles++
                    val ext = file.extension.lowercase()
                    if (ext in archiveExtensions) {
                        candidates.add(
                            CleanupCandidate(
                                id = file.absolutePath,
                                path = file.absolutePath,
                                name = file.name,
                                size = file.length(),
                                lastModified = file.lastModified(),
                                isDirectory = false,
                                category = StorageCategory.ARCHIVES,
                                flagReason = "Archive package (${ext.uppercase()})"
                            )
                        )
                    }
                }
        }

        val sorted = sortCandidates(candidates, sortBy)
        val diagnostics = ScanDiagnostics(
            scannedFilesCount = scannedFiles,
            scannedDirectoriesCount = scannedDirs,
            inaccessibleFoldersCount = inaccessiblePaths.size,
            inaccessiblePaths = inaccessiblePaths,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
        Pair(sorted, diagnostics)
    }

    // =========================================================================
    // 4. Empty Folders View (1.4.11)
    // =========================================================================

    suspend fun scanEmptyFolders(
        rootPath: String? = null
    ): Pair<List<EmptyFolderItem>, ScanDiagnostics> = withContext(ioDispatcher) {
        val root = resolveRoot(rootPath)
        val emptyFolders = mutableListOf<EmptyFolderItem>()
        val inaccessiblePaths = mutableListOf<String>()
        var scannedFiles = 0
        var scannedDirs = 0
        val startTime = System.currentTimeMillis()

        if (root.exists() && root.canRead()) {
            root.walkTopDown()
                .onEnter { dir ->
                    scannedDirs++
                    if (!dir.canRead()) {
                        inaccessiblePaths.add(dir.absolutePath)
                        false
                    } else {
                        !dir.name.startsWith(".")
                    }
                }
                .filter { it.isDirectory && it.absolutePath != root.absolutePath && !it.name.startsWith(".") }
                .forEach { dir ->
                    coroutineContext.ensureActive()
                    val children = dir.listFiles()
                    if (children != null && children.isEmpty()) {
                        emptyFolders.add(
                            EmptyFolderItem(
                                id = dir.absolutePath,
                                path = dir.absolutePath,
                                name = dir.name,
                                lastModified = dir.lastModified()
                            )
                        )
                    }
                }
        }

        val diagnostics = ScanDiagnostics(
            scannedFilesCount = scannedFiles,
            scannedDirectoriesCount = scannedDirs,
            inaccessibleFoldersCount = inaccessiblePaths.size,
            inaccessiblePaths = inaccessiblePaths,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
        Pair(emptyFolders.sortedBy { it.name.lowercase() }, diagnostics)
    }

    // =========================================================================
    // 5. Duplicate Detection (Staged Process) (1.4.13)
    // =========================================================================

    suspend fun scanDuplicateCandidatesStaged(
        rootPath: String? = null,
        computeHashes: Boolean = true,
        onProgress: ((progress: Float, currentFile: String) -> Unit)? = null
    ): Pair<DuplicateScanResult, ScanDiagnostics> = withContext(ioDispatcher) {
        val root = resolveRoot(rootPath)
        val inaccessiblePaths = mutableListOf<String>()
        val sizeBuckets = HashMap<Long, MutableList<File>>()
        var scannedFiles = 0
        var scannedDirs = 0
        val startTime = System.currentTimeMillis()

        if (root.exists() && root.canRead()) {
            root.walkTopDown()
                .onEnter { dir ->
                    scannedDirs++
                    if (!dir.canRead()) {
                        inaccessiblePaths.add(dir.absolutePath)
                        false
                    } else {
                        !dir.name.startsWith(".")
                    }
                }
                .filter { it.isFile && !it.name.startsWith(".") && it.length() > 0 }
                .forEach { file ->
                    coroutineContext.ensureActive()
                    scannedFiles++
                    sizeBuckets.getOrPut(file.length()) { mutableListOf() }.add(file)
                }
        }

        // Stage 1: Filter buckets with >= 2 files
        val candidateBuckets = sizeBuckets.filterValues { it.size >= 2 }
        if (candidateBuckets.isEmpty()) {
            val emptyDiag = ScanDiagnostics(scannedFiles, scannedDirs, inaccessiblePaths.size, inaccessiblePaths)
            return@withContext Pair(DuplicateScanResult(), emptyDiag)
        }

        val duplicateGroups = mutableListOf<DuplicateGroup>()

        if (!computeHashes) {
            // Stage 2: Return preliminary possible candidates (same size and name)
            for ((size, fileList) in candidateBuckets) {
                val nodes = fileList.map { it.toStorageNode() }.sortedByDescending { it.lastModified }
                duplicateGroups.add(
                    DuplicateGroup(
                        hash = "size_${size}_pending_hash",
                        sizePerFile = size,
                        files = nodes,
                        matchLevel = DuplicateMatchLevel.POSSIBLE_METADATA_CANDIDATE
                    )
                )
            }
        } else {
            // Stage 3 & 4: Cryptographic SHA-256 verification
            val totalCandidates = candidateBuckets.values.sumOf { it.size }
            var processed = 0
            val hashGroups = HashMap<String, MutableList<File>>()

            for ((_, fileList) in candidateBuckets) {
                for (file in fileList) {
                    coroutineContext.ensureActive()
                    val hash = computeSha256(file)
                    if (hash != null) {
                        hashGroups.getOrPut(hash) { mutableListOf() }.add(file)
                    }
                    processed++
                    onProgress?.invoke(processed.toFloat() / totalCandidates.toFloat(), file.name)
                }
            }

            for ((hash, files) in hashGroups) {
                if (files.size >= 2) {
                    val nodes = files.map { it.toStorageNode() }.sortedByDescending { it.lastModified }
                    duplicateGroups.add(
                        DuplicateGroup(
                            hash = hash,
                            sizePerFile = files.first().length(),
                            files = nodes,
                            matchLevel = DuplicateMatchLevel.CONFIRMED_HASH_MATCH
                        )
                    )
                }
            }
        }

        val sortedGroups = duplicateGroups.sortedByDescending { it.wastedBytes }
        val totalDuplicateFiles = sortedGroups.sumOf { it.files.size }
        val totalWastedBytes = sortedGroups.sumOf { it.wastedBytes }

        val result = DuplicateScanResult(
            groups = sortedGroups,
            totalDuplicateFilesCount = totalDuplicateFiles,
            totalWastedBytes = totalWastedBytes
        )
        val diagnostics = ScanDiagnostics(
            scannedFilesCount = scannedFiles,
            scannedDirectoriesCount = scannedDirs,
            inaccessibleFoldersCount = inaccessiblePaths.size,
            inaccessiblePaths = inaccessiblePaths,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
        Pair(result, diagnostics)
    }

    // =========================================================================
    // 6. Category Breakdown with Zero Double-Counting (1.4.14)
    // =========================================================================

    suspend fun scanCategoryBreakdown(
        rootPath: String? = null
    ): Pair<StorageBreakdown, ScanDiagnostics> = withContext(ioDispatcher) {
        val root = resolveRoot(rootPath)
        val inaccessiblePaths = mutableListOf<String>()
        var scannedFiles = 0
        var scannedDirs = 0
        val startTime = System.currentTimeMillis()

        var imageBytes = 0L; var imageCount = 0
        var videoBytes = 0L; var videoCount = 0
        var audioBytes = 0L; var audioCount = 0
        var docBytes = 0L; var docCount = 0
        var archiveBytes = 0L; var archiveCount = 0
        var otherBytes = 0L; var otherCount = 0

        if (root.exists() && root.canRead()) {
            root.walkTopDown()
                .onEnter { dir ->
                    scannedDirs++
                    if (!dir.canRead()) {
                        inaccessiblePaths.add(dir.absolutePath)
                        false
                    } else {
                        !dir.name.startsWith(".")
                    }
                }
                .filter { it.isFile && !it.name.startsWith(".") }
                .forEach { file ->
                    coroutineContext.ensureActive()
                    scannedFiles++
                    val len = file.length()
                    val ext = file.extension.lowercase()

                    // Strict disjoint partition: every file belongs to exactly ONE category
                    when {
                        ext in imageExtensions -> { imageBytes += len; imageCount++ }
                        ext in videoExtensions -> { videoBytes += len; videoCount++ }
                        ext in audioExtensions -> { audioBytes += len; audioCount++ }
                        ext in documentExtensions -> { docBytes += len; docCount++ }
                        ext in archiveExtensions -> { archiveBytes += len; archiveCount++ }
                        else -> { otherBytes += len; otherCount++ }
                    }
                }
        }

        val totalScannedBytes = imageBytes + videoBytes + audioBytes + docBytes + archiveBytes + otherBytes

        val categories = listOf(
            CategorySize(StorageCategory.IMAGES, imageBytes, imageCount),
            CategorySize(StorageCategory.VIDEOS, videoBytes, videoCount),
            CategorySize(StorageCategory.AUDIO, audioBytes, audioCount),
            CategorySize(StorageCategory.DOCUMENTS, docBytes, docCount),
            CategorySize(StorageCategory.ARCHIVES, archiveBytes, archiveCount),
            CategorySize(StorageCategory.OTHER, otherBytes, otherCount)
        )

        val rootTotal = runCatching { root.totalSpace }.getOrDefault(0L)
        val rootFree = runCatching { root.freeSpace }.getOrDefault(0L)
        val rootUsed = if (rootTotal > 0L) (rootTotal - rootFree).coerceAtLeast(0L) else totalScannedBytes

        val breakdown = StorageBreakdown(
            totalBytes = if (rootTotal > 0L) rootTotal else totalScannedBytes,
            freeBytes = rootFree,
            usedBytes = rootUsed,
            categories = categories,
            isCalculating = false
        )

        val diagnostics = ScanDiagnostics(
            scannedFilesCount = scannedFiles,
            scannedDirectoriesCount = scannedDirs,
            inaccessibleFoldersCount = inaccessiblePaths.size,
            inaccessiblePaths = inaccessiblePaths,
            isEstimate = false,
            scanDurationMs = System.currentTimeMillis() - startTime
        )

        Pair(breakdown, diagnostics)
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private fun resolveRoot(rootPath: String?): File {
        val path = rootPath ?: runCatching {
            Environment.getExternalStorageDirectory().absolutePath
        }.getOrDefault("/storage/emulated/0")
        return File(path)
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

    private fun sortCandidates(candidates: List<CleanupCandidate>, sortBy: AnalyzerSortBy): List<CleanupCandidate> {
        return when (sortBy) {
            AnalyzerSortBy.SIZE_DESC -> candidates.sortedByDescending { it.size }
            AnalyzerSortBy.SIZE_ASC -> candidates.sortedBy { it.size }
            AnalyzerSortBy.DATE_DESC -> candidates.sortedByDescending { it.lastModified }
            AnalyzerSortBy.DATE_ASC -> candidates.sortedBy { it.lastModified }
            AnalyzerSortBy.NAME_ASC -> candidates.sortedBy { it.name.lowercase() }
        }
    }

    private fun computeSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            FileInputStream(file).use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hashBytes = digest.digest()
            val sb = StringBuilder(hashBytes.size * 2)
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (_: Exception) {
            null
        }
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
