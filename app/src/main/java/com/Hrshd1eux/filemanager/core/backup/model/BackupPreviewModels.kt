package com.Hrshd1eux.filemanager.core.backup.model

import java.io.File

/**
 * Categorized action proposed for an item in a backup preview (Milestone 1.4.26).
 */
enum class BackupPreviewAction(val displayName: String) {
    COPY_NEW("Files to copy"),
    UPDATE_MODIFIED("Files to update"),
    ALREADY_MATCHES("Files that already match"),
    DELETE_IN_MIRROR("Files that would be deleted in mirror mode"),
    INACCESSIBLE("Items that could not be compared")
}

data class BackupPreviewItem(
    val sourceFile: File?,
    val destinationFile: File?,
    val relativePath: String,
    val action: BackupPreviewAction,
    val sizeBytes: Long,
    val reason: String
)

/**
 * Structured pre-execution preview report (Milestone 1.4.26).
 */
data class BackupPreviewReport(
    val profileId: String,
    val profileName: String,
    val mode: BackupMode,
    val filesToCopy: List<BackupPreviewItem> = emptyList(),
    val filesToUpdate: List<BackupPreviewItem> = emptyList(),
    val filesAlreadyMatching: List<BackupPreviewItem> = emptyList(),
    val filesToDeleteInMirror: List<BackupPreviewItem> = emptyList(),
    val inaccessibleItems: List<BackupPreviewItem> = emptyList(),
    val totalBytesToCopy: Long = 0L,
    val totalBytesToDelete: Long = 0L,
    val requiresMirrorConfirmation: Boolean = false
) {
    val totalActionableFilesCount: Int
        get() = filesToCopy.size + filesToUpdate.size + filesToDeleteInMirror.size
}

/**
 * Exception thrown when mirror mode execution is requested without explicit user authorization (Milestone 1.4.28).
 */
class MirrorDeletionNotConfirmedException(message: String) : IllegalStateException(message)
