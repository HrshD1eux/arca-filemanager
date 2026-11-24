package com.Hrshd1eux.filemanager.core.trash.util

import java.io.File

/**
 * Robust classifier and filter for trashed items.
 *
 * Detects:
 * 1. Android MediaStore Scoped Storage trashed files (.trashed-<timestamp>-<name>, .trashed_*, .trashed)
 * 2. ARCA internal trash files (<uuid>.trash) and files with .trash / .trashed extensions
 * 3. Files or directories residing inside known trash directories (.arca_trash, .trash, .Trash, Trash, .trashed, .recycle, etc.)
 */
object TrashFilter {

    private val TRASH_DIR_NAMES = setOf(
        ".arca_trash",
        ".trash",
        ".Trash",
        ".trashed",
        "Trash",
        "trash",
        ".recycle",
        ".Recycle.Bin",
        ".lost+found"
    )

    private val TRASH_FILE_EXTENSIONS = setOf(
        "trash",
        "trashed"
    )

    /**
     * Determines whether a given file or directory is part of trash
     * (either ARCA internal trash, Android MediaStore trash, or standard trash directories).
     */
    fun isTrashed(file: File): Boolean {
        val name = file.name

        // 1. Android MediaStore trash naming pattern: .trashed-<timestamp>-<name> or .trashed
        if (name.startsWith(".trashed", ignoreCase = true) || name.contains(".trashed-", ignoreCase = true)) {
            return true
        }

        // 2. Trash file extension (e.g. <uuid>.trash or file.trashed)
        val ext = file.extension
        if (ext.isNotEmpty() && ext.lowercase() in TRASH_FILE_EXTENSIONS) {
            return true
        }

        // 3. Resides in any known trash directory in the path hierarchy
        var parent: File? = file.parentFile
        while (parent != null) {
            if (isTrashDirectory(parent)) {
                return true
            }
            parent = parent.parentFile
        }

        return false
    }

    /**
     * Checks if the directory itself is a dedicated trash / recycle directory.
     */
    fun isTrashDirectory(dir: File): Boolean {
        val name = dir.name
        if (name in TRASH_DIR_NAMES) {
            return true
        }
        if (name.startsWith(".trashed", ignoreCase = true)) {
            return true
        }
        return false
    }
}
