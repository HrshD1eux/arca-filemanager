package com.Hrshd1eux.filemanager.core.operations.recovery

import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import java.io.File

/**
 * Recommendations produced after pre-flight recovery assessment (Milestones 1.4.32 & 1.4.37).
 */
enum class RecoveryRecommendation(val userDescription: String) {
    RESUME("Operation can be safely resumed from remaining pending files."),
    RESTART_REQUIRED("Source or destination state changed significantly. Restart is recommended."),
    STORAGE_DISCONNECTED("Storage device or network host is disconnected. Reconnect to resume."),
    CANNOT_RESUME("Source files are no longer available or corrupted.")
}

/**
 * Detailed assessment report of an interrupted operation prior to resuming (Milestones 1.4.32, 1.4.36, 1.4.37).
 */
data class RecoveryAssessment(
    val entry: OperationJournalEntry,
    val recommendation: RecoveryRecommendation,
    val verifiedCompletedBytes: Long,
    val verifiedCompletedFilesCount: Int,
    val pendingBytes: Long,
    val pendingFilesCount: Int,
    val missingOrCorruptFilesCount: Int,
    val sourceChangedFilesCount: Int,
    val orphanedTempFilesFound: List<File> = emptyList(),
    val canResumeSafely: Boolean,
    val statusMessage: String,
    val notice: String
) {
    val totalBytesExpected: Long get() = entry.totalBytesExpected
    val totalFilesExpected: Int get() = entry.totalFilesExpected
}

/**
 * Result of executing an operation recovery (Resume, Restart, or Discard).
 */
data class OperationRecoveryResult(
    val operationId: String,
    val success: Boolean,
    val filesProcessed: Int,
    val bytesProcessed: Long,
    val errors: List<String> = emptyList(),
    val summary: String
)

/**
 * User-facing presentation report for the recovery interface (Milestone 1.4.36).
 *
 * Golden Invariant:
 * The progress figures strictly reflect recorded and verified state,
 * NOT merely bytes that were attempted.
 */
data class RecoveryReport(
    val operationId: String,
    val title: String,
    val operationType: JournalOperationType,
    val verifiedBytes: Long,
    val totalBytes: Long,
    val verifiedFilesCount: Int,
    val totalFilesCount: Int,
    val pendingFilesCount: Int,
    val canResume: Boolean,
    val notice: String,
    val storageDisconnected: Boolean = false,
    val orphanedTempFilesCount: Int = 0
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) ((verifiedBytes.toFloat() / totalBytes) * 100f).coerceIn(0f, 100f) else 0f

    val formattedVerifiedSize: String
        get() = FileFormatter.formatFileSize(verifiedBytes)

    val formattedTotalSize: String
        get() = FileFormatter.formatFileSize(totalBytes)

    val progressDisplay: String
        get() = "$formattedVerifiedSize of $formattedTotalSize"

    companion object {
        fun fromAssessment(assessment: RecoveryAssessment): RecoveryReport {
            val entry = assessment.entry
            val isDisconnected = assessment.recommendation == RecoveryRecommendation.STORAGE_DISCONNECTED
            return RecoveryReport(
                operationId = entry.id,
                title = entry.title,
                operationType = entry.type,
                verifiedBytes = assessment.verifiedCompletedBytes,
                totalBytes = entry.totalBytesExpected,
                verifiedFilesCount = assessment.verifiedCompletedFilesCount,
                totalFilesCount = entry.totalFilesExpected,
                pendingFilesCount = assessment.pendingFilesCount + assessment.missingOrCorruptFilesCount,
                canResume = assessment.canResumeSafely && !isDisconnected,
                notice = assessment.notice,
                storageDisconnected = isDisconnected,
                orphanedTempFilesCount = assessment.orphanedTempFilesFound.size
            )
        }
    }
}
