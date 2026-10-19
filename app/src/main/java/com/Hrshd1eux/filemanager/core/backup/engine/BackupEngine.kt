package com.Hrshd1eux.filemanager.core.backup.engine

import com.Hrshd1eux.filemanager.core.backup.model.BackupMode
import com.Hrshd1eux.filemanager.core.backup.model.BackupProfile
import com.Hrshd1eux.filemanager.core.backup.model.BackupStatus
import com.Hrshd1eux.filemanager.core.backup.repository.BackupProfileRepository
import com.Hrshd1eux.filemanager.core.checksum.ChecksumAlgorithm
import com.Hrshd1eux.filemanager.core.checksum.ChecksumEngine
import com.Hrshd1eux.filemanager.core.operations.util.StreamCopier
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class BackupProgress(
    val currentFileName: String = "",
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val percent: Float = 0f
)

data class BackupError(
    val filePath: String,
    val errorMessage: String
)

data class BackupReport(
    val profileId: String,
    val profileName: String,
    val status: BackupStatus,
    val startTime: Long,
    val durationMs: Long,
    val filesCopied: Int,
    val filesSkipped: Int,
    val filesFailed: Int,
    val bytesTransferred: Long,
    val errors: List<BackupError> = emptyList(),
    val summary: String
)

class BackupEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val repository: BackupProfileRepository? = null,
    private val checksumEngine: ChecksumEngine = ChecksumEngine(ioDispatcher)
) {

    /**
     * Executes a backup job non-destructively according to the profile rules.
     * Tracks progress, supports cancellation, handles errors per-file without crashing,
     * and updates profile status upon completion.
     */
    suspend fun executeBackup(
        profile: BackupProfile,
        onProgress: ((BackupProgress) -> Unit)? = null
    ): BackupReport = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()
        var filesCopied = 0
        var filesSkipped = 0
        var filesFailed = 0
        var bytesTransferred = 0L
        val errors = mutableListOf<BackupError>()

        try {
            val destDir = File(profile.destinationPath)
            if (!destDir.exists()) {
                val created = destDir.mkdirs()
                if (!created && !destDir.exists()) {
                    throw IOException("Could not create destination directory: ${destDir.path}")
                }
            }

            // 1. Scan and collect candidate files across all source roots
            val candidateFiles = mutableListOf<Pair<File, File>>() // Pair(SourceFile, SourceBaseDir)
            for (sourcePath in profile.sourcePaths) {
                coroutineContext.ensureActive()
                val src = File(sourcePath)
                if (!src.exists()) {
                    errors.add(BackupError(sourcePath, "Source directory does not exist"))
                    continue
                }

                if (src.isFile) {
                    if (shouldInclude(src, profile)) {
                        candidateFiles.add(Pair(src, src.parentFile ?: src))
                    }
                } else if (src.isDirectory) {
                    src.walkTopDown().forEach { file ->
                        coroutineContext.ensureActive()
                        if (file.isFile && shouldInclude(file, profile)) {
                            candidateFiles.add(Pair(file, src))
                        }
                    }
                }
            }

            val totalFiles = candidateFiles.size
            var totalBytes = candidateFiles.sumOf { it.first.length() }
            var bytesProcessed = 0L
            var filesProcessed = 0
            val speedTrackingStart = System.currentTimeMillis()

            // 2. Process each file safely
            for ((sourceFile, sourceBase) in candidateFiles) {
                coroutineContext.ensureActive()
                filesProcessed++

                val relativePath = if (sourceFile == sourceBase) {
                    sourceFile.name
                } else {
                    sourceFile.relativeTo(sourceBase).path
                }
                val targetFile = File(destDir, relativePath)

                // Check incremental skip conditions
                if (profile.mode == BackupMode.INCREMENTAL && targetFile.exists() && targetFile.isFile) {
                    if (targetFile.length() == sourceFile.length() &&
                        targetFile.lastModified() >= sourceFile.lastModified() - 2000L
                    ) {
                        filesSkipped++
                        bytesProcessed += sourceFile.length()
                        emitProgress(onProgress, sourceFile.name, filesProcessed, totalFiles, bytesProcessed, totalBytes, speedTrackingStart)
                        continue
                    }
                }

                // Ensure parent folder exists
                val targetParent = targetFile.parentFile
                if (targetParent != null && !targetParent.exists()) {
                    targetParent.mkdirs()
                }

                // Copy single file with temporary artifact safety
                val tempTarget = File(targetFile.parentFile, ".${targetFile.name}.arca_backup_tmp")
                try {
                    var fileBytesTransferred = 0L
                    FileInputStream(sourceFile).use { inStream ->
                        FileOutputStream(tempTarget).use { outStream ->
                            StreamCopier.copyStream(
                                source = inStream,
                                destination = outStream,
                                onBytesCopied = { chunk ->
                                    coroutineContext.ensureActive()
                                    bytesProcessed += chunk
                                    fileBytesTransferred += chunk
                                    emitProgress(onProgress, sourceFile.name, filesProcessed, totalFiles, bytesProcessed, totalBytes, speedTrackingStart)
                                }
                            )
                        }
                    }

                    // Optional cryptographic verification
                    if (profile.verifyIntegrity) {
                        val srcSha = checksumEngine.calculateAlgorithm(sourceFile, ChecksumAlgorithm.SHA_256).getOrNull()?.hashHex
                        val tmpSha = checksumEngine.calculateAlgorithm(tempTarget, ChecksumAlgorithm.SHA_256).getOrNull()?.hashHex
                        if (srcSha != null && tmpSha != null && !srcSha.equals(tmpSha, ignoreCase = true)) {
                            throw IOException("Integrity check failed: Source SHA-256 ($srcSha) != Target SHA-256 ($tmpSha)")
                        }
                    }

                    // Atomically replace target
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (!tempTarget.renameTo(targetFile)) {
                        tempTarget.copyTo(targetFile, overwrite = true)
                        tempTarget.delete()
                    }

                    // Preserve modification timestamp
                    targetFile.setLastModified(sourceFile.lastModified())

                    filesCopied++
                    bytesTransferred += fileBytesTransferred
                } catch (ce: CancellationException) {
                    if (tempTarget.exists()) tempTarget.delete()
                    throw ce
                } catch (t: Throwable) {
                    if (tempTarget.exists()) tempTarget.delete()
                    filesFailed++
                    errors.add(BackupError(sourceFile.path, t.localizedMessage ?: "Unknown copy error"))
                }
            }

            val duration = System.currentTimeMillis() - startTime
            val finalStatus = when {
                filesFailed > 0 && filesCopied > 0 -> BackupStatus.PARTIAL_SUCCESS
                filesFailed > 0 && filesCopied == 0 -> BackupStatus.FAILED
                else -> BackupStatus.SUCCESS
            }

            val summary = buildString {
                append("Backup completed in ${duration / 1000}s. ")
                append("Copied: $filesCopied (${FileFormatter.formatFileSize(bytesTransferred)}), ")
                append("Skipped: $filesSkipped, ")
                append("Failed: $filesFailed.")
            }

            val report = BackupReport(
                profileId = profile.id,
                profileName = profile.name,
                status = finalStatus,
                startTime = startTime,
                durationMs = duration,
                filesCopied = filesCopied,
                filesSkipped = filesSkipped,
                filesFailed = filesFailed,
                bytesTransferred = bytesTransferred,
                errors = errors,
                summary = summary
            )

            repository?.updateProfileRunResult(profile.id, startTime, finalStatus, summary)
            report
        } catch (ce: CancellationException) {
            val duration = System.currentTimeMillis() - startTime
            val cancelSummary = "Backup was cancelled after ${duration / 1000}s ($filesCopied copied, $filesSkipped skipped)."
            val report = BackupReport(
                profileId = profile.id,
                profileName = profile.name,
                status = BackupStatus.CANCELLED,
                startTime = startTime,
                durationMs = duration,
                filesCopied = filesCopied,
                filesSkipped = filesSkipped,
                filesFailed = filesFailed,
                bytesTransferred = bytesTransferred,
                errors = errors,
                summary = cancelSummary
            )
            repository?.updateProfileRunResult(profile.id, startTime, BackupStatus.CANCELLED, cancelSummary)
            report
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            val errSummary = "Backup failed: ${t.localizedMessage}"
            val report = BackupReport(
                profileId = profile.id,
                profileName = profile.name,
                status = BackupStatus.FAILED,
                startTime = startTime,
                durationMs = duration,
                filesCopied = filesCopied,
                filesSkipped = filesSkipped,
                filesFailed = filesFailed + 1,
                bytesTransferred = bytesTransferred,
                errors = errors + BackupError(profile.destinationPath, t.localizedMessage ?: "Fatal error"),
                summary = errSummary
            )
            repository?.updateProfileRunResult(profile.id, startTime, BackupStatus.FAILED, errSummary)
            report
        }
    }

    private fun shouldInclude(file: File, profile: BackupProfile): Boolean {
        if (!profile.includeHiddenFiles && (file.name.startsWith(".") || file.parentFile?.name?.startsWith(".") == true)) {
            return false
        }
        for (pattern in profile.exclusionPatterns) {
            if (matchesPattern(file.name, pattern)) {
                return false
            }
        }
        return true
    }

    private fun matchesPattern(fileName: String, pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed == "*") return true
        if (trimmed.startsWith("*.") && fileName.endsWith(trimmed.substring(1), ignoreCase = true)) {
            return true
        }
        if (trimmed.endsWith("*") && fileName.startsWith(trimmed.substringBefore("*"), ignoreCase = true)) {
            return true
        }
        if (fileName.equals(trimmed, ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun emitProgress(
        onProgress: ((BackupProgress) -> Unit)?,
        currentFile: String,
        filesProcessed: Int,
        totalFiles: Int,
        bytesProcessed: Long,
        totalBytes: Long,
        startTimeMs: Long
    ) {
        if (onProgress == null) return
        val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
        val speed = if (elapsedSec > 0.0) (bytesProcessed / elapsedSec).toLong() else 0L
        val percent = if (totalBytes > 0L) (bytesProcessed.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

        onProgress(
            BackupProgress(
                currentFileName = currentFile,
                filesProcessed = filesProcessed,
                totalFiles = totalFiles,
                bytesProcessed = bytesProcessed,
                totalBytes = totalBytes,
                speedBytesPerSec = speed,
                percent = percent
            )
        )
    }
}
