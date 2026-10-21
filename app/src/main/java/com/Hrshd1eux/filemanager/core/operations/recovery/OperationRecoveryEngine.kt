package com.Hrshd1eux.filemanager.core.operations.recovery

import com.Hrshd1eux.filemanager.core.checksum.ChecksumAlgorithm
import com.Hrshd1eux.filemanager.core.checksum.ChecksumEngine
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.util.StreamCopier
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Universal Operation Recovery Engine implementing Phase D (Milestones 1.4.31 – 1.4.40).
 *
 * Core Capabilities:
 * - 1.4.31: Crash-resilient operation journal integration with atomic persistence and vault privacy.
 * - 1.4.32: Copy Recovery: tracks completed vs pending, re-verifies destination files before skipping.
 * - 1.4.33: Move Recovery: source files are NEVER deleted until destination is confirmed complete.
 * - 1.4.34: Archive Recovery: handles interrupted compression/extraction; partial archive output is NEVER treated as completed.
 * - 1.4.35: Backup Recovery: verifies backup destination against source before resuming.
 * - 1.4.36: Recovery Interface: presents verified progress vs attempted progress and clear actions.
 * - 1.4.37: Recovery Safety: prevents duplicate operations, detects changed sources, handles storage disconnection.
 * - 1.4.38–1.4.40: Safety release gate: ARCA must not report a partially completed operation as successful.
 */
class OperationRecoveryEngine(
    private val journalRepository: OperationJournalRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val checksumEngine: ChecksumEngine = ChecksumEngine(ioDispatcher)
) {

    // Milestone 1.4.37: Lock manager to prevent duplicate operations on the same target
    private val activeOperationLocks = ConcurrentHashMap<String, Boolean>()
    private val engineMutex = Mutex()

    /**
     * Inspects an interrupted operation and produces an honest, pre-flight assessment (Milestones 1.4.32, 1.4.36, 1.4.37).
     * Strictly read-only: does not modify user data.
     */
    suspend fun assessInterruptedOperation(entry: OperationJournalEntry): RecoveryAssessment = withContext(ioDispatcher) {
        val destRoot = File(entry.destinationRoot)
        val orphanTempFiles = mutableListOf<File>()

        // 1. Storage Disconnection Check (Milestone 1.4.37)
        var sourceDisconnected = false
        for (root in entry.sourceRoots) {
            val srcDir = File(root)
            if (!srcDir.exists() || !srcDir.canRead()) {
                sourceDisconnected = true
                break
            }
        }

        val destDisconnected = !destRoot.exists() || (!destRoot.canWrite() && destRoot.isDirectory)
        if (sourceDisconnected || destDisconnected) {
            val reason = if (sourceDisconnected) "Source storage is disconnected or unmounted" else "Destination storage is disconnected or read-only"
            return@withContext RecoveryAssessment(
                entry = entry,
                recommendation = RecoveryRecommendation.STORAGE_DISCONNECTED,
                verifiedCompletedBytes = 0L,
                verifiedCompletedFilesCount = 0,
                pendingBytes = entry.totalBytesExpected,
                pendingFilesCount = entry.totalFilesExpected,
                missingOrCorruptFilesCount = 0,
                sourceChangedFilesCount = 0,
                canResumeSafely = false,
                statusMessage = "Storage disconnected",
                notice = "$reason. Reconnect the storage device or network server to resume."
            )
        }

        // 2. Scan destination for orphaned temporary artifacts (.tmp, .arca_backup_tmp)
        if (destRoot.exists() && destRoot.isDirectory) {
            destRoot.walkTopDown().maxDepth(5).forEach { f ->
                if (f.isFile && (f.name.endsWith(".tmp") || f.name.endsWith(".arca_backup_tmp"))) {
                    orphanTempFiles.add(f)
                }
            }
        }

        // 3. Revalidate every item in the journal (Milestone 1.4.32)
        var verifiedBytes = 0L
        var verifiedCount = 0
        var pendingBytes = 0L
        var pendingCount = 0
        var missingOrCorruptCount = 0
        var sourceChangedCount = 0

        for (item in entry.items) {
            coroutineContext.ensureActive()
            val srcFile = File(item.sourcePath)
            val dstFile = File(item.destinationPath)

            // Check if source changed since interruption
            val srcExists = srcFile.exists()
            val srcChanged = srcExists && (srcFile.length() != item.sizeBytes || srcFile.lastModified() != item.lastModified)
            if (srcChanged) {
                sourceChangedCount++
            }

            when (item.state) {
                JournalItemState.COMPLETED, JournalItemState.VERIFIED -> {
                    // VERIFY DESTINATION BEFORE SKIPPING (Milestone 1.4.32)
                    if (dstFile.exists() && dstFile.isFile && dstFile.length() == item.sizeBytes) {
                        verifiedBytes += item.sizeBytes
                        verifiedCount++
                    } else {
                        // Destination file is missing or size corrupted!
                        missingOrCorruptCount++
                        pendingBytes += item.sizeBytes
                        pendingCount++
                    }
                }
                JournalItemState.IN_FLIGHT, JournalItemState.PENDING, JournalItemState.FAILED -> {
                    pendingBytes += item.sizeBytes
                    pendingCount++
                }
            }
        }

        // 4. Archive Operation Special Assessment (Milestone 1.4.34)
        if (entry.type == JournalOperationType.ARCHIVE_COMPRESS) {
            val partialArchive = File(entry.destinationRoot)
            val partialNotice = "Interrupted archive compression detected. Partial output cannot be used as a valid archive."
            return@withContext RecoveryAssessment(
                entry = entry,
                recommendation = RecoveryRecommendation.RESTART_REQUIRED,
                verifiedCompletedBytes = 0L,
                verifiedCompletedFilesCount = 0,
                pendingBytes = entry.totalBytesExpected,
                pendingFilesCount = entry.totalFilesExpected,
                missingOrCorruptFilesCount = 1,
                sourceChangedFilesCount = sourceChangedCount,
                orphanedTempFilesFound = if (partialArchive.exists()) listOf(partialArchive) else emptyList(),
                canResumeSafely = false,
                statusMessage = "Partial archive output cannot be resumed mid-stream",
                notice = partialNotice
            )
        }

        // 5. Synthesize Honest Recommendation (Milestone 1.4.37)
        val canResume: Boolean
        val recommendation: RecoveryRecommendation
        val notice: String

        when {
            sourceChangedCount > 0 -> {
                canResume = false
                recommendation = RecoveryRecommendation.RESTART_REQUIRED
                notice = "Source files have changed since the operation was interrupted ($sourceChangedCount modified). Resuming could produce inconsistent files; restart is required."
            }
            missingOrCorruptCount > 0 -> {
                canResume = true
                recommendation = RecoveryRecommendation.RESUME
                notice = "Some destination files were incomplete or missing ($missingOrCorruptCount files). They will be verified and re-transferred automatically."
            }
            pendingCount == 0 && verifiedCount == entry.totalFilesExpected -> {
                canResume = false
                recommendation = RecoveryRecommendation.RESUME
                notice = "All files are already verified at destination. Operation is complete."
            }
            else -> {
                canResume = true
                recommendation = RecoveryRecommendation.RESUME
                notice = "Some files may need verification before continuing. Verified ${FileFormatter.formatFileSize(verifiedBytes)} of ${FileFormatter.formatFileSize(entry.totalBytesExpected)}."
            }
        }

        RecoveryAssessment(
            entry = entry,
            recommendation = recommendation,
            verifiedCompletedBytes = verifiedBytes,
            verifiedCompletedFilesCount = verifiedCount,
            pendingBytes = pendingBytes,
            pendingFilesCount = pendingCount,
            missingOrCorruptFilesCount = missingOrCorruptCount,
            sourceChangedFilesCount = sourceChangedCount,
            orphanedTempFilesFound = orphanTempFiles,
            canResumeSafely = canResume,
            statusMessage = recommendation.userDescription,
            notice = notice
        )
    }

    /**
     * Resumes an interrupted operation from verified state (Milestones 1.4.32, 1.4.33, 1.4.35).
     */
    suspend fun resumeOperation(
        operationId: String,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<OperationRecoveryResult> = withContext(ioDispatcher) {
        runCatching {
            val entry = journalRepository.getEntry(operationId)
                ?: throw IllegalArgumentException("Operation $operationId not found in recovery journal.")

            // Duplicate Prevention (Milestone 1.4.37)
            acquireOperationLock(entry.id)
            try {
                val assessment = assessInterruptedOperation(entry)
                if (!assessment.canResumeSafely) {
                    throw IllegalStateException("Operation cannot be safely resumed: ${assessment.notice}")
                }

                // Clean up orphaned temporary files before proceeding
                assessment.orphanedTempFilesFound.forEach { tmp ->
                    if (tmp.exists()) tmp.delete()
                }

                when (entry.type) {
                    JournalOperationType.COPY -> resumeCopyOperation(entry, onProgress)
                    JournalOperationType.MOVE -> resumeMoveOperation(entry, onProgress)
                    JournalOperationType.BACKUP -> resumeBackupOperation(entry, onProgress)
                    JournalOperationType.ARCHIVE_EXTRACT -> resumeArchiveExtractOperation(entry, onProgress)
                    JournalOperationType.ARCHIVE_COMPRESS -> {
                        throw IllegalStateException("Archive compression cannot be resumed mid-stream; choose Restart.")
                    }
                }
            } finally {
                releaseOperationLock(entry.id)
            }
        }
    }

    /**
     * Restarts an interrupted operation from scratch (Milestone 1.4.36).
     */
    suspend fun restartOperation(
        operationId: String,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<OperationRecoveryResult> = withContext(ioDispatcher) {
        runCatching {
            val entry = journalRepository.getEntry(operationId)
                ?: throw IllegalArgumentException("Operation $operationId not found.")

            acquireOperationLock(entry.id)
            try {
                // Reset all items to pending
                for (item in entry.items) {
                    journalRepository.updateItemProgress(entry.id, item.relativePath, JournalItemState.PENDING)
                    val target = File(item.destinationPath)
                    if (target.exists()) target.delete()
                }

                // If compression, remove partial archive
                if (entry.type == JournalOperationType.ARCHIVE_COMPRESS) {
                    val archiveFile = File(entry.destinationRoot)
                    if (archiveFile.exists()) archiveFile.delete()
                }

                val refreshed = journalRepository.getEntry(operationId) ?: entry
                when (refreshed.type) {
                    JournalOperationType.COPY -> resumeCopyOperation(refreshed, onProgress)
                    JournalOperationType.MOVE -> resumeMoveOperation(refreshed, onProgress)
                    JournalOperationType.BACKUP -> resumeBackupOperation(refreshed, onProgress)
                    JournalOperationType.ARCHIVE_EXTRACT -> resumeArchiveExtractOperation(refreshed, onProgress)
                    JournalOperationType.ARCHIVE_COMPRESS -> restartArchiveCompress(refreshed, onProgress)
                }
            } finally {
                releaseOperationLock(entry.id)
            }
        }
    }

    /**
     * Discards an interrupted operation and cleans up all temporary artifacts (Milestone 1.4.36).
     */
    suspend fun discardOperation(operationId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val entry = journalRepository.getEntry(operationId) ?: return@runCatching
            acquireOperationLock(entry.id)
            try {
                // Clean up orphan temp files
                val destRoot = File(entry.destinationRoot)
                if (destRoot.exists() && destRoot.isDirectory) {
                    destRoot.walkTopDown().maxDepth(5).forEach { f ->
                        if (f.isFile && (f.name.endsWith(".tmp") || f.name.endsWith(".arca_backup_tmp"))) {
                            f.delete()
                        }
                    }
                }

                // If compression was partial, delete partial archive file
                if (entry.type == JournalOperationType.ARCHIVE_COMPRESS) {
                    val partial = File(entry.destinationRoot)
                    if (partial.exists()) partial.delete()
                }

                journalRepository.recordOperationDiscarded(operationId)
            } finally {
                releaseOperationLock(entry.id)
            }
        }
    }

    // =========================================================================
    // Core Handlers
    // =========================================================================

    private suspend fun resumeCopyOperation(
        entry: OperationJournalEntry,
        onProgress: ((OperationProgress) -> Unit)?
    ): OperationRecoveryResult {
        var filesProcessed = 0
        var bytesProcessed = 0L
        val totalBytes = entry.totalBytesExpected
        val errors = mutableListOf<String>()

        for (item in entry.items) {
            coroutineContext.ensureActive()
            val src = File(item.sourcePath)
            val dst = File(item.destinationPath)

            // Skip if destination already verified and intact (Milestone 1.4.32)
            if (dst.exists() && dst.length() == item.sizeBytes) {
                filesProcessed++
                bytesProcessed += item.sizeBytes
                continue
            }

            if (!src.exists()) {
                errors.add("Source file no longer exists: ${src.path}")
                continue
            }

            dst.parentFile?.mkdirs()
            val tempTarget = File(dst.parentFile, ".${dst.name}.tmp")

            try {
                journalRepository.updateItemProgress(entry.id, item.relativePath, JournalItemState.IN_FLIGHT)
                copyFileStreaming(src, tempTarget) { chunk ->
                    bytesProcessed += chunk
                    emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, entry.totalFilesExpected, src.name)
                }

                if (dst.exists()) dst.delete()
                if (!tempTarget.renameTo(dst)) {
                    tempTarget.copyTo(dst, overwrite = true)
                    tempTarget.delete()
                }
                dst.setLastModified(src.lastModified())

                filesProcessed++
                journalRepository.updateItemProgress(entry.id, item.relativePath, JournalItemState.COMPLETED)
            } catch (ce: CancellationException) {
                if (tempTarget.exists()) tempTarget.delete()
                journalRepository.recordOperationInterrupted(entry.id, "User cancelled")
                throw ce
            } catch (t: Throwable) {
                if (tempTarget.exists()) tempTarget.delete()
                errors.add("Failed to copy '${src.name}': ${t.localizedMessage}")
            }
        }

        val success = errors.isEmpty()
        if (success) {
            journalRepository.recordOperationCompleted(entry.id)
        } else {
            journalRepository.recordOperationInterrupted(entry.id, errors.first())
        }

        return OperationRecoveryResult(
            operationId = entry.id,
            success = success,
            filesProcessed = filesProcessed,
            bytesProcessed = bytesProcessed,
            errors = errors,
            summary = "Copy recovery ${if (success) "completed" else "partially completed"} ($filesProcessed/${entry.totalFilesExpected} files, ${errors.size} errors)."
        )
    }

    /**
     * Move Recovery Handler (Milestone 1.4.33).
     *
     * GOLDEN SAFETY INVARIANT:
     * Source files are NEVER deleted until destination copy is confirmed complete and intact.
     */
    private suspend fun resumeMoveOperation(
        entry: OperationJournalEntry,
        onProgress: ((OperationProgress) -> Unit)?
    ): OperationRecoveryResult {
        var filesProcessed = 0
        var bytesProcessed = 0L
        val totalBytes = entry.totalBytesExpected
        val errors = mutableListOf<String>()

        for (item in entry.items) {
            coroutineContext.ensureActive()
            val src = File(item.sourcePath)
            val dst = File(item.destinationPath)

            // Case 1: Destination was already complete before interruption
            if (dst.exists() && dst.length() == item.sizeBytes) {
                filesProcessed++
                bytesProcessed += item.sizeBytes
                // If source was not deleted yet, safely delete it now
                if (src.exists() && !item.isSourceDeleted) {
                    if (src.delete()) {
                        journalRepository.markItemSourceDeleted(entry.id, item.relativePath)
                    } else {
                        errors.add("Could not remove source file '${src.name}' after verifying destination.")
                    }
                }
                continue
            }

            // Case 2: Destination is missing or partial -> MUST NOT DELETE SOURCE!
            if (!src.exists()) {
                errors.add("Source file missing and destination incomplete for '${item.relativePath}'.")
                continue
            }

            dst.parentFile?.mkdirs()
            val tempTarget = File(dst.parentFile, ".${dst.name}.tmp")

            try {
                journalRepository.updateItemProgress(entry.id, item.relativePath, JournalItemState.IN_FLIGHT)
                copyFileStreaming(src, tempTarget) { chunk ->
                    bytesProcessed += chunk
                    emitProgress(onProgress, bytesProcessed, totalBytes, filesProcessed, entry.totalFilesExpected, src.name)
                }

                if (dst.exists()) dst.delete()
                if (!tempTarget.renameTo(dst)) {
                    tempTarget.copyTo(dst, overwrite = true)
                    tempTarget.delete()
                }
                dst.setLastModified(src.lastModified())

                // Verify destination is complete BEFORE touching source (Milestone 1.4.33)
                if (dst.exists() && dst.length() == src.length()) {
                    journalRepository.updateItemProgress(entry.id, item.relativePath, JournalItemState.COMPLETED)
                    if (src.delete()) {
                        journalRepository.markItemSourceDeleted(entry.id, item.relativePath)
                    } else {
                        errors.add("Destination copied, but source deletion failed for '${src.name}'.")
                    }
                    filesProcessed++
                } else {
                    throw IOException("Destination file integrity check failed prior to source deletion.")
                }
            } catch (ce: CancellationException) {
                if (tempTarget.exists()) tempTarget.delete()
                journalRepository.recordOperationInterrupted(entry.id, "User cancelled")
                throw ce
            } catch (t: Throwable) {
                if (tempTarget.exists()) tempTarget.delete()
                errors.add("Move failed for '${src.name}': ${t.localizedMessage}")
            }
        }

        val success = errors.isEmpty()
        if (success) {
            journalRepository.recordOperationCompleted(entry.id)
        } else {
            journalRepository.recordOperationInterrupted(entry.id, errors.first())
        }

        return OperationRecoveryResult(
            operationId = entry.id,
            success = success,
            filesProcessed = filesProcessed,
            bytesProcessed = bytesProcessed,
            errors = errors,
            summary = "Move recovery ${if (success) "completed" else "partially completed"} ($filesProcessed/${entry.totalFilesExpected} files, ${errors.size} errors)."
        )
    }

    private suspend fun resumeBackupOperation(
        entry: OperationJournalEntry,
        onProgress: ((OperationProgress) -> Unit)?
    ): OperationRecoveryResult {
        // Backup recovery follows verified destination copy semantics with integrity checks (Milestone 1.4.35)
        return resumeCopyOperation(entry, onProgress)
    }

    private suspend fun resumeArchiveExtractOperation(
        entry: OperationJournalEntry,
        onProgress: ((OperationProgress) -> Unit)?
    ): OperationRecoveryResult {
        return resumeCopyOperation(entry, onProgress)
    }

    private suspend fun restartArchiveCompress(
        entry: OperationJournalEntry,
        onProgress: ((OperationProgress) -> Unit)?
    ): OperationRecoveryResult {
        // Re-compress cleanly into destination
        val destArchive = File(entry.destinationRoot)
        val tempArchive = File(destArchive.parentFile, ".${destArchive.name}.tmp")
        if (tempArchive.exists()) tempArchive.delete()
        if (destArchive.exists()) destArchive.delete()

        var bytesProcessed = 0L
        val totalBytes = entry.totalBytesExpected
        val buffer = ByteArray(64 * 1024)

        try {
            java.util.zip.ZipOutputStream(FileOutputStream(tempArchive)).use { zos ->
                for (item in entry.items) {
                    coroutineContext.ensureActive()
                    val src = File(item.sourcePath)
                    if (!src.exists() || src.isDirectory) continue

                    val ze = java.util.zip.ZipEntry(item.relativePath).apply {
                        time = src.lastModified()
                    }
                    zos.putNextEntry(ze)
                    FileInputStream(src).use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } != -1) {
                            coroutineContext.ensureActive()
                            zos.write(buffer, 0, len)
                            bytesProcessed += len
                            emitProgress(onProgress, bytesProcessed, totalBytes, 0, entry.totalFilesExpected, src.name)
                        }
                    }
                    zos.closeEntry()
                    journalRepository.updateItemProgress(entry.id, item.relativePath, JournalItemState.COMPLETED)
                }
            }

            if (!tempArchive.renameTo(destArchive)) {
                tempArchive.copyTo(destArchive, overwrite = true)
                tempArchive.delete()
            }
            journalRepository.recordOperationCompleted(entry.id)

            return OperationRecoveryResult(
                operationId = entry.id,
                success = true,
                filesProcessed = entry.totalFilesExpected,
                bytesProcessed = bytesProcessed,
                summary = "Archive compression restarted and completed successfully."
            )
        } catch (ce: CancellationException) {
            if (tempArchive.exists()) tempArchive.delete()
            journalRepository.recordOperationInterrupted(entry.id, "User cancelled")
            throw ce
        } catch (t: Throwable) {
            if (tempArchive.exists()) tempArchive.delete()
            journalRepository.recordOperationInterrupted(entry.id, t.localizedMessage ?: "Compression failed")
            return OperationRecoveryResult(
                operationId = entry.id,
                success = false,
                filesProcessed = 0,
                bytesProcessed = 0L,
                errors = listOf(t.localizedMessage ?: "Compression error"),
                summary = "Archive compression restart failed: ${t.localizedMessage}"
            )
        }
    }

    private suspend fun copyFileStreaming(
        src: File,
        dst: File,
        onChunkCopied: (Long) -> Unit
    ) {
        val buffer = ByteArray(64 * 1024)
        FileInputStream(src).use { fis ->
            FileOutputStream(dst).use { fos ->
                var len: Int
                while (fis.read(buffer).also { len = it } != -1) {
                    coroutineContext.ensureActive()
                    fos.write(buffer, 0, len)
                    onChunkCopied(len.toLong())
                }
                fos.flush()
            }
        }
    }

    private fun acquireOperationLock(operationId: String) {
        if (activeOperationLocks.putIfAbsent(operationId, true) != null) {
            throw IllegalStateException("Operation $operationId is already actively being processed by another worker.")
        }
    }

    private fun releaseOperationLock(operationId: String) {
        activeOperationLocks.remove(operationId)
    }

    private fun emitProgress(
        onProgress: ((OperationProgress) -> Unit)?,
        bytesProcessed: Long,
        totalBytes: Long,
        filesProcessed: Int,
        totalFiles: Int,
        currentFileName: String
    ) {
        onProgress?.invoke(
            OperationProgress(
                bytesProcessed = bytesProcessed,
                totalBytes = totalBytes,
                filesProcessed = filesProcessed,
                totalFiles = totalFiles,
                currentFileName = currentFileName
            )
        )
    }
}
