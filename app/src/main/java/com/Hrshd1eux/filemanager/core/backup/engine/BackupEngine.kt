package com.Hrshd1eux.filemanager.core.backup.engine

import com.Hrshd1eux.filemanager.core.backup.model.BackupMode
import com.Hrshd1eux.filemanager.core.backup.model.BackupPreviewAction
import com.Hrshd1eux.filemanager.core.backup.model.BackupPreviewItem
import com.Hrshd1eux.filemanager.core.backup.model.BackupPreviewReport
import com.Hrshd1eux.filemanager.core.backup.model.BackupProfile
import com.Hrshd1eux.filemanager.core.backup.model.BackupStatus
import com.Hrshd1eux.filemanager.core.backup.model.MirrorDeletionNotConfirmedException
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
import kotlin.math.abs

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
    val summary: String,
    val verifiedCount: Int = 0,
    val failedVerificationCount: Int = 0,
    val deletedInMirrorCount: Int = 0
)

/**
 * Backup Engine implementing Phase C foundations (Milestones 1.4.25 – 1.4.29).
 *
 * Capabilities:
 * - 1.4.25: Profiles with Copy & Mirror policies, verification, and exclusion patterns.
 * - 1.4.26: Pre-execution preview report (files to copy, update, skip, mirror deletions, inaccessible).
 * - 1.4.27: Reliable Copy Mode with streaming progress, cooperative cancellation, and atomic rename.
 * - 1.4.28: Mirror Mode with strict explicit confirmation guard, destination revalidation, and zero source deletion.
 * - 1.4.29: Content integrity verification via SHA-256 streaming with failed verification reporting.
 */
class BackupEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val repository: BackupProfileRepository? = null,
    private val checksumEngine: ChecksumEngine = ChecksumEngine(ioDispatcher)
) {

    /**
     * Generates a structured pre-execution backup preview (Milestone 1.4.26).
     * Strictly read-only: never creates, modifies, or deletes any files.
     */
    suspend fun previewBackup(profile: BackupProfile): BackupPreviewReport = withContext(ioDispatcher) {
        val destDir = File(profile.destinationPath)
        val filesToCopy = mutableListOf<BackupPreviewItem>()
        val filesToUpdate = mutableListOf<BackupPreviewItem>()
        val filesAlreadyMatching = mutableListOf<BackupPreviewItem>()
        val filesToDeleteInMirror = mutableListOf<BackupPreviewItem>()
        val inaccessibleItems = mutableListOf<BackupPreviewItem>()

        val sourceRelativePaths = mutableSetOf<String>()

        // 1. Scan source trees
        for (sourcePath in profile.sourcePaths) {
            coroutineContext.ensureActive()
            val src = File(sourcePath)
            if (!src.exists() || !src.canRead()) {
                inaccessibleItems.add(
                    BackupPreviewItem(
                        sourceFile = src,
                        destinationFile = null,
                        relativePath = src.name,
                        action = BackupPreviewAction.INACCESSIBLE,
                        sizeBytes = 0L,
                        reason = if (!src.exists()) "Source path does not exist" else "Source path is not readable (Permission denied)"
                    )
                )
                continue
            }

            if (src.isFile) {
                if (shouldInclude(src, profile)) {
                    val relPath = src.name
                    sourceRelativePaths.add(relPath)
                    val target = File(destDir, relPath)
                    categorizeSourceFile(src, target, relPath, profile, filesToCopy, filesToUpdate, filesAlreadyMatching)
                }
            } else if (src.isDirectory) {
                src.walkTopDown().onEnter { dir ->
                    if (!dir.canRead()) {
                        inaccessibleItems.add(
                            BackupPreviewItem(
                                sourceFile = dir,
                                destinationFile = null,
                                relativePath = dir.name,
                                action = BackupPreviewAction.INACCESSIBLE,
                                sizeBytes = 0L,
                                reason = "Directory is not readable (Permission denied)"
                            )
                        )
                        false
                    } else true
                }.forEach { file ->
                    coroutineContext.ensureActive()
                    if (file.isFile && shouldInclude(file, profile)) {
                        val relPath = file.relativeTo(src).path.replace('\\', '/')
                        sourceRelativePaths.add(relPath)
                        val target = File(destDir, relPath)
                        categorizeSourceFile(file, target, relPath, profile, filesToCopy, filesToUpdate, filesAlreadyMatching)
                    }
                }
            }
        }

        // 2. If in MIRROR mode, scan destination tree for files to delete (Milestone 1.4.28)
        if (profile.mode == BackupMode.MIRROR && destDir.exists() && destDir.isDirectory) {
            destDir.walkTopDown().onEnter { dir ->
                if (!dir.canRead()) {
                    inaccessibleItems.add(
                        BackupPreviewItem(
                            sourceFile = null,
                            destinationFile = dir,
                            relativePath = dir.name,
                            action = BackupPreviewAction.INACCESSIBLE,
                            sizeBytes = 0L,
                            reason = "Destination directory is not readable (Permission denied)"
                        )
                    )
                    false
                } else true
            }.forEach { destFile ->
                coroutineContext.ensureActive()
                if (destFile.isFile) {
                    val relPath = destFile.relativeTo(destDir).path.replace('\\', '/')
                    if (!sourceRelativePaths.contains(relPath)) {
                        filesToDeleteInMirror.add(
                            BackupPreviewItem(
                                sourceFile = null,
                                destinationFile = destFile,
                                relativePath = relPath,
                                action = BackupPreviewAction.DELETE_IN_MIRROR,
                                sizeBytes = destFile.length(),
                                reason = "File exists in destination but is missing from source (Mirror sync removal)"
                            )
                        )
                    }
                }
            }
        }

        val totalBytesToCopy = filesToCopy.sumOf { it.sizeBytes } + filesToUpdate.sumOf { it.sizeBytes }
        val totalBytesToDelete = filesToDeleteInMirror.sumOf { it.sizeBytes }

        BackupPreviewReport(
            profileId = profile.id,
            profileName = profile.name,
            mode = profile.mode,
            filesToCopy = filesToCopy,
            filesToUpdate = filesToUpdate,
            filesAlreadyMatching = filesAlreadyMatching,
            filesToDeleteInMirror = filesToDeleteInMirror,
            inaccessibleItems = inaccessibleItems,
            totalBytesToCopy = totalBytesToCopy,
            totalBytesToDelete = totalBytesToDelete,
            requiresMirrorConfirmation = filesToDeleteInMirror.isNotEmpty()
        )
    }

    /**
     * Executes backup according to the profile rules (Milestones 1.4.27 & 1.4.28).
     *
     * In Mirror Mode: requires [confirmedMirrorDeletion] to be true if files must be pruned from destination.
     */
    suspend fun executeBackup(
        profile: BackupProfile,
        confirmedMirrorDeletion: Boolean = false,
        onProgress: ((BackupProgress) -> Unit)? = null
    ): BackupReport = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()
        var filesCopied = 0
        var filesSkipped = 0
        var filesFailed = 0
        var verifiedCount = 0
        var failedVerificationCount = 0
        var deletedInMirrorCount = 0
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

            // 1. Preview analysis for safe planning and change detection
            val preview = previewBackup(profile)

            // Milestone 1.4.28 Guard: Require explicit confirmation for mirror mode deletion BEFORE execution
            if (profile.mode == BackupMode.MIRROR && preview.filesToDeleteInMirror.isNotEmpty()) {
                if (!confirmedMirrorDeletion) {
                    throw MirrorDeletionNotConfirmedException(
                        "Mirror mode requires explicit confirmation before deleting ${preview.filesToDeleteInMirror.size} files from destination (${FileFormatter.formatFileSize(preview.totalBytesToDelete)})."
                    )
                }
            }

            // 2. Collect candidate files to copy from source
            val candidateFiles = mutableListOf<Pair<File, File>>()
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
            val totalBytes = candidateFiles.sumOf { it.first.length() }
            var bytesProcessed = 0L
            var filesProcessed = 0
            val speedTrackingStart = System.currentTimeMillis()

            // 3. Process each file safely (Copy & Verify Phase)
            for ((sourceFile, sourceBase) in candidateFiles) {
                coroutineContext.ensureActive()
                filesProcessed++

                val relativePath = if (sourceFile == sourceBase) {
                    sourceFile.name
                } else {
                    sourceFile.relativeTo(sourceBase).path.replace('\\', '/')
                }
                val targetFile = File(destDir, relativePath)

                // Incremental / skip conditions with content verification
                if (profile.mode != BackupMode.FULL && targetFile.exists() && targetFile.isFile) {
                    if (targetFile.length() == sourceFile.length() &&
                        abs(targetFile.lastModified() - sourceFile.lastModified()) < 2000L
                    ) {
                        var isIdentical = true
                        if (profile.verifyIntegrity) {
                            val srcSha = checksumEngine.calculateAlgorithm(sourceFile, ChecksumAlgorithm.SHA_256).getOrNull()?.hashHex
                            val tgtSha = checksumEngine.calculateAlgorithm(targetFile, ChecksumAlgorithm.SHA_256).getOrNull()?.hashHex
                            if (srcSha != null && tgtSha != null && srcSha.equals(tgtSha, ignoreCase = true)) {
                                verifiedCount++
                                isIdentical = true
                            } else if (srcSha != null && tgtSha != null) {
                                // Content mismatch: re-copy to heal destination
                                isIdentical = false
                            }
                        }
                        if (isIdentical) {
                            filesSkipped++
                            bytesProcessed += sourceFile.length()
                            emitProgress(onProgress, sourceFile.name, filesProcessed, totalFiles, bytesProcessed, totalBytes, speedTrackingStart)
                            continue
                        }
                    }
                }

                // Ensure parent folder exists
                val targetParent = targetFile.parentFile
                if (targetParent != null && !targetParent.exists()) {
                    targetParent.mkdirs()
                }

                // Atomic copy using temporary artifact (.arca_backup_tmp)
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

                    // Milestone 1.4.29: Content integrity verification
                    if (profile.verifyIntegrity) {
                        val srcSha = checksumEngine.calculateAlgorithm(sourceFile, ChecksumAlgorithm.SHA_256).getOrNull()?.hashHex
                        val tmpSha = checksumEngine.calculateAlgorithm(tempTarget, ChecksumAlgorithm.SHA_256).getOrNull()?.hashHex
                        if (srcSha != null && tmpSha != null && !srcSha.equals(tmpSha, ignoreCase = true)) {
                            failedVerificationCount++
                            throw IOException("Integrity check failed: Source SHA-256 ($srcSha) != Target SHA-256 ($tmpSha)")
                        }
                        verifiedCount++
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

            // 4. Mirror Mode Pruning (Strictly AFTER Copy & Verification)
            // Safety Invariant: NEVER prune destination files if any copy or verification failed!
            if (profile.mode == BackupMode.MIRROR && preview.filesToDeleteInMirror.isNotEmpty()) {
                if (filesFailed > 0 || failedVerificationCount > 0) {
                    errors.add(
                        BackupError(
                            profile.destinationPath,
                            "Mirror pruning aborted: Destination files were NOT deleted because $filesFailed files failed to copy and $failedVerificationCount failed verification."
                        )
                    )
                } else {
                    val destCanonical = destDir.canonicalPath
                    for (item in preview.filesToDeleteInMirror) {
                        coroutineContext.ensureActive()
                        val target = item.destinationFile ?: File(destDir, item.relativePath)
                        try {
                            val targetCanonical = target.canonicalPath
                            if (!targetCanonical.startsWith(destCanonical)) {
                                throw SecurityException("Security violation: Target is outside destination directory: $targetCanonical")
                            }
                            if (isRestrictedPath(targetCanonical)) {
                                throw SecurityException("Protected system path cannot be deleted: $targetCanonical")
                            }
                            if (target.exists()) {
                                if (target.delete()) {
                                    deletedInMirrorCount++
                                } else {
                                    errors.add(BackupError(target.path, "Failed to delete destination file in mirror sync"))
                                }
                            }
                        } catch (e: Exception) {
                            errors.add(BackupError(target.path, "Mirror deletion error: ${e.localizedMessage}"))
                        }
                    }
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
                append("Failed: $filesFailed. ")
                if (deletedInMirrorCount > 0) {
                    append("Mirror pruned: $deletedInMirrorCount files. ")
                }
                if (profile.verifyIntegrity) {
                    append("Verified: $verifiedCount SHA-256 matches ($failedVerificationCount failed).")
                }
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
                summary = summary,
                verifiedCount = verifiedCount,
                failedVerificationCount = failedVerificationCount,
                deletedInMirrorCount = deletedInMirrorCount
            )

            repository?.updateProfileRunResult(profile.id, startTime, finalStatus, summary)
            report
        } catch (mde: MirrorDeletionNotConfirmedException) {
            throw mde
        } catch (ce: CancellationException) {
            val duration = System.currentTimeMillis() - startTime
            val cancelSummary = "Backup was cancelled after ${duration / 1000}s ($filesCopied copied, $filesSkipped skipped)."
            repository?.updateProfileRunResult(profile.id, startTime, BackupStatus.CANCELLED, cancelSummary)
            throw ce
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
                summary = errSummary,
                verifiedCount = verifiedCount,
                failedVerificationCount = failedVerificationCount,
                deletedInMirrorCount = deletedInMirrorCount
            )
            repository?.updateProfileRunResult(profile.id, startTime, BackupStatus.FAILED, errSummary)
            report
        }
    }

    private fun categorizeSourceFile(
        sourceFile: File,
        targetFile: File,
        relativePath: String,
        profile: BackupProfile,
        filesToCopy: MutableList<BackupPreviewItem>,
        filesToUpdate: MutableList<BackupPreviewItem>,
        filesAlreadyMatching: MutableList<BackupPreviewItem>
    ) {
        if (!targetFile.exists()) {
            filesToCopy.add(
                BackupPreviewItem(
                    sourceFile = sourceFile,
                    destinationFile = targetFile,
                    relativePath = relativePath,
                    action = BackupPreviewAction.COPY_NEW,
                    sizeBytes = sourceFile.length(),
                    reason = "New file not present in destination"
                )
            )
        } else if (profile.mode != BackupMode.FULL &&
            targetFile.length() == sourceFile.length() &&
            abs(targetFile.lastModified() - sourceFile.lastModified()) < 2000L
        ) {
            filesAlreadyMatching.add(
                BackupPreviewItem(
                    sourceFile = sourceFile,
                    destinationFile = targetFile,
                    relativePath = relativePath,
                    action = BackupPreviewAction.ALREADY_MATCHES,
                    sizeBytes = sourceFile.length(),
                    reason = "Identical size and modification timestamp"
                )
            )
        } else {
            filesToUpdate.add(
                BackupPreviewItem(
                    sourceFile = sourceFile,
                    destinationFile = targetFile,
                    relativePath = relativePath,
                    action = BackupPreviewAction.UPDATE_MODIFIED,
                    sizeBytes = sourceFile.length(),
                    reason = "Source is newer or size differs"
                )
            )
        }
    }

    private fun isRestrictedPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.contains("/Android/data", ignoreCase = true) ||
            normalized.contains("/Android/obb", ignoreCase = true)
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
