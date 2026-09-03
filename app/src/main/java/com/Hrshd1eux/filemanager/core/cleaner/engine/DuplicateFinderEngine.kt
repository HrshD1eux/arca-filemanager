package com.Hrshd1eux.filemanager.core.cleaner.engine

import android.os.Environment
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateGroup
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateScanResult
import com.Hrshd1eux.filemanager.core.cleaner.model.ScanStage
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class DuplicateFinderEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun scanDuplicates(
        rootPath: String? = null,
        onProgress: ((stage: ScanStage, progress: Float, currentFile: String) -> Unit)? = null
    ): DuplicateScanResult = withContext(ioDispatcher) {
        val targetPath = rootPath ?: runCatching {
            Environment.getExternalStorageDirectory().absolutePath
        }.getOrDefault("/storage/emulated/0")

        val root = File(targetPath)
        if (!root.exists() || !root.canRead()) {
            return@withContext DuplicateScanResult()
        }

        // ==========================================
        // Phase 1: File Inventory & Size Grouping
        // ==========================================
        onProgress?.invoke(ScanStage.INDEXING_SIZES, 0f, "")
        val sizeBuckets = HashMap<Long, MutableList<File>>()

        runCatching {
            root.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") }
                .filter { it.isFile && !it.name.startsWith(".") && it.length() > 0 }
                .forEach { file ->
                    coroutineContext.ensureActive()
                    val length = file.length()
                    sizeBuckets.getOrPut(length) { mutableListOf() }.add(file)
                }
        }

        // Prune single-instance sizes immediately
        val candidateBuckets = sizeBuckets.filterValues { it.size >= 2 }
        if (candidateBuckets.isEmpty()) {
            return@withContext DuplicateScanResult()
        }

        val totalCandidateFiles = candidateBuckets.values.sumOf { it.size }
        var processedFiles = 0

        // ==========================================
        // Phase 2 & 3: Streaming SHA-256 Hashing
        // ==========================================
        onProgress?.invoke(ScanStage.HASHING_CANDIDATES, 0f, "")
        val hashGroups = HashMap<String, MutableList<File>>()

        for ((_, fileList) in candidateBuckets) {
            for (file in fileList) {
                coroutineContext.ensureActive()
                val hash = computeSha256(file)
                if (hash != null) {
                    hashGroups.getOrPut(hash) { mutableListOf() }.add(file)
                }
                processedFiles++
                val progress = processedFiles.toFloat() / totalCandidateFiles.toFloat()
                onProgress?.invoke(ScanStage.HASHING_CANDIDATES, progress, file.name)
            }
        }

        // ==========================================
        // Phase 4: Duplicate Group Verification
        // ==========================================
        onProgress?.invoke(ScanStage.VERIFYING, 0.95f, "")
        val verifiedGroups = mutableListOf<DuplicateGroup>()

        for ((hash, files) in hashGroups) {
            if (files.size >= 2) {
                val nodes = files.map { it.toStorageNode() }.sortedByDescending { it.lastModified }
                val sizePerFile = files.first().length()
                verifiedGroups.add(
                    DuplicateGroup(
                        hash = hash,
                        sizePerFile = sizePerFile,
                        files = nodes
                    )
                )
            }
        }

        // Sort duplicate groups by recoverable wasted bytes descending
        val sortedGroups = verifiedGroups.sortedByDescending { it.wastedBytes }
        val totalDuplicateFiles = sortedGroups.sumOf { it.files.size }
        val totalWastedBytes = sortedGroups.sumOf { it.wastedBytes }

        onProgress?.invoke(ScanStage.COMPLETED, 1f, "")

        DuplicateScanResult(
            groups = sortedGroups,
            totalDuplicateFilesCount = totalDuplicateFiles,
            totalWastedBytes = totalWastedBytes
        )
    }

    private fun computeSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
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

    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
    }
}
