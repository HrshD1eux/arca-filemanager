package com.Hrshd1eux.filemanager.core.operations.model

/**
 * Snapshot of ongoing file operation progress.
 */
data class OperationProgress(
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = ""
) {
    val progressRatio: Float = if (totalBytes > 0L) {
        (bytesProcessed.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else if (totalFiles > 0) {
        (filesProcessed.toDouble() / totalFiles.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
}
