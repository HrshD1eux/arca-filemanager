package com.Hrshd1eux.filemanager.core.storage.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Calculates folder item counts and total recursive byte size on background threads.
 */
object FolderDetailsCalculator {

    data class FolderDetails(
        val totalFiles: Long = 0L,
        val totalFolders: Long = 0L,
        val totalBytes: Long = 0L
    )

    suspend fun calculateDetails(
        directory: File,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): FolderDetails = withContext(ioDispatcher) {
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext FolderDetails()
        }

        var files = 0L
        var folders = 0L
        var bytes = 0L

        try {
            directory.walkTopDown()
                .onEnter { it.canRead() }
                .forEach { file ->
                    if (file != directory) {
                        if (file.isDirectory) {
                            folders++
                        } else {
                            files++
                            bytes += file.length()
                        }
                    }
                }
        } catch (_: Exception) {
            // Inaccessible subdirectories or permission changes during traversal
        }

        FolderDetails(
            totalFiles = files,
            totalFolders = folders,
            totalBytes = bytes
        )
    }
}
