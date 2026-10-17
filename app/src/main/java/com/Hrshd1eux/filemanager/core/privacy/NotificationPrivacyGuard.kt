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

    /**
     * Sanitizes vault operation messages for public notifications and diagnostic logs.
     * Guaranteed never to include confidential vault filenames, paths, or query terms.
     */
    fun sanitizeVaultNotification(operation: String): String {
        return when (operation.lowercase()) {
            "import" -> "Encrypting files into Vault"
            "export" -> "Decrypting files from Vault"
            "delete" -> "Securely deleting vault items"
            "move" -> "Organizing vault items"
            else -> "Vault operation in progress"
        }
    }

    /**
     * Returns sanitized feedback messages for vault file operations without leaking filenames.
     */
    fun sanitizeVaultMessage(action: String, count: Int = 1): String {
        return when (action.lowercase()) {
            "created_folder" -> "New folder created"
            "renamed" -> "Item renamed"
            "deleted" -> if (count == 1) "Item deleted permanently" else "Deleted $count items permanently"
            "moved" -> if (count == 1) "Item moved" else "Moved $count items"
            "imported" -> if (count == 1) "Imported 1 file into Vault" else "Imported $count files into Vault"
            "exported" -> if (count == 1) "Exported 1 file from Vault" else "Exported $count files from Vault"
            else -> "Operation completed"
        }
    }
}
