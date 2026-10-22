package com.Hrshd1eux.filemanager.core.storage.util

import java.io.File

/**
 * Robust filesystem walker that guarantees resilience against OS-level permission boundaries,
 * scoped storage restrictions (e.g. Android 11+ /Android/data and /Android/obb), and unreadable paths.
 */
object SafeFileWalker {

    /**
     * Checks if a directory is an OS-restricted scoped storage path on Android 11+
     * (e.g., /storage/emulated/0/Android/data or /storage/emulated/0/Android/obb).
     */
    fun isRestrictedDirectory(dir: File): Boolean {
        val name = dir.name
        if (name.equals("data", ignoreCase = true) || name.equals("obb", ignoreCase = true)) {
            val parentName = dir.parentFile?.name
            if (parentName.equals("Android", ignoreCase = true)) {
                return true
            }
        }
        val normalized = dir.path.replace('\\', '/')
        return normalized.endsWith("/Android/data") ||
                normalized.contains("/Android/data/") ||
                normalized.endsWith("/Android/obb") ||
                normalized.contains("/Android/obb/")
    }

    /**
     * Returns a safely configured FileTreeWalk sequence that:
     * 1. Skips inaccessible directories and restricted Android system folders (/Android/data, /Android/obb)
     * 2. Continues gracefully on I/O errors without aborting the traversal
     */
    fun walkSafe(
        root: File,
        includeHidden: Boolean = false,
        maxDepth: Int = Int.MAX_VALUE,
        onRestrictedDir: ((File) -> Unit)? = null
    ): Sequence<File> {
        if (!root.exists() || !root.canRead()) return emptySequence()

        return root.walkTopDown()
            .maxDepth(maxDepth)
            .onEnter { dir ->
                if (!includeHidden && dir.name.startsWith(".")) return@onEnter false
                if (isRestrictedDirectory(dir)) {
                    onRestrictedDir?.invoke(dir)
                    return@onEnter false
                }
                if (!dir.canRead()) {
                    onRestrictedDir?.invoke(dir)
                    return@onEnter false
                }
                true
            }
            .onFail { file, _ ->
                onRestrictedDir?.invoke(file)
            }
    }
}
