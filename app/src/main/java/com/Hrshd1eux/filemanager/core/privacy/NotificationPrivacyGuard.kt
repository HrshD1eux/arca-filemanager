package com.Hrshd1eux.filemanager.core.privacy

/**
 * Privacy enforcement policy ensuring zero leakage of file names, extensions,
 * or directory paths into Android public notifications or lockscreen banners.
 */
object NotificationPrivacyGuard {

    /**
     * Returns a generic, sanitized progress notification string.
     * Guaranteed not to contain sensitive user filenames or paths.
     */
    fun sanitizeNotificationMessage(totalItems: Int, completedItems: Int): String {
        return when {
            totalItems <= 1 -> "Processing operation..."
            else -> "Processing $completedItems of $totalItems items..."
        }
    }

    /**
     * Sanitizes notification titles to generic operation categories.
     */
    fun sanitizeNotificationTitle(operationName: String): String {
        return when (operationName.lowercase()) {
            "copy" -> "Copying files"
            "move" -> "Moving files"
            "delete" -> "Deleting files"
            "archive", "zip" -> "Archiving files"
            "extract", "unzip" -> "Extracting files"
            else -> "File Operation in Progress"
        }
    }
}
