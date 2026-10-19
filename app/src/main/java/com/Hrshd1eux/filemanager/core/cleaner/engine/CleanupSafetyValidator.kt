package com.Hrshd1eux.filemanager.core.cleaner.engine

import android.os.Build
import com.Hrshd1eux.filemanager.core.cleaner.model.CleanupCandidate
import com.Hrshd1eux.filemanager.core.cleaner.model.CleanupExecutionReport
import com.Hrshd1eux.filemanager.core.cleaner.model.RevalidationResult
import com.Hrshd1eux.filemanager.core.cleaner.model.RevalidationStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Validates and safely executes file & folder cleanup operations.
 *
 * RIGOROUS SAFETY PRINCIPLES:
 * 1. Target Revalidation: Validates size, timestamp, and existence immediately prior to deletion.
 *    Stale or modified targets are strictly aborted.
 * 2. OS & Permission Guard: Detects restricted Android paths (/Android/data, /Android/obb) and read-only media.
 * 3. Empty Directory Guard: Verifies directories are still 100% empty before deletion.
 * 4. Zero Automatic Deletion: Deletion only executes on items explicitly provided and confirmed.
 */
class CleanupSafetyValidator(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Revalidates a cleanup candidate against the current live storage state.
     */
    fun revalidateTarget(candidate: CleanupCandidate): RevalidationResult {
        // 1. Check Android Scoped Storage Restrictions (/Android/data, /Android/obb on Android 11+)
        if (isAndroidRestrictedPath(candidate.path)) {
            return RevalidationResult(
                candidate = candidate,
                status = RevalidationStatus.PERMISSION_RESTRICTED,
                detailMessage = "Android system security policies prevent modifying or deleting contents in this protected app directory."
            )
        }

        val file = File(candidate.path)

        // 2. Check existence
        if (!file.exists()) {
            return RevalidationResult(
                candidate = candidate,
                status = RevalidationStatus.TARGET_NOT_FOUND,
                detailMessage = "Target file or folder no longer exists at path: ${candidate.path}"
            )
        }

        // 3. Stale Modification Check for files
        if (!candidate.isDirectory) {
            val currentLength = file.length()
            val currentLastModified = file.lastModified()

            // If file size or modification time changed since scan, it is STALE
            if (currentLength != candidate.size || currentLastModified != candidate.lastModified) {
                return RevalidationResult(
                    candidate = candidate,
                    status = RevalidationStatus.STALE_MODIFIED,
                    detailMessage = "Target file was modified or updated after the scan was conducted (expected size: ${candidate.size}, actual: $currentLength). Deletion aborted for safety."
                )
            }
        } else {
            // For directories, verify it is still an empty folder
            val children = file.listFiles()
            if (children != null && children.isNotEmpty()) {
                return RevalidationResult(
                    candidate = candidate,
                    status = RevalidationStatus.STALE_MODIFIED,
                    detailMessage = "Directory is no longer empty (${children.size} items detected). Deletion aborted."
                )
            }
        }

        // 4. Check write permissions
        if (!file.canWrite() && !file.parentFile?.canWrite().let { it == true }) {
            return RevalidationResult(
                candidate = candidate,
                status = RevalidationStatus.PERMISSION_RESTRICTED,
                detailMessage = "Permission denied: File or parent folder is read-only."
            )
        }

        return RevalidationResult(
            candidate = candidate,
            status = RevalidationStatus.VALID
        )
    }

    /**
     * Executes safe deletion on user-authorized targets with pre-flight revalidation.
     */
    suspend fun executeSafeDeletion(
        targets: List<CleanupCandidate>,
        onProgress: ((deletedCount: Int, totalCount: Int) -> Unit)? = null
    ): CleanupExecutionReport = withContext(ioDispatcher) {
        var deletedCount = 0
        var freedBytes = 0L
        var skippedStale = 0
        var restrictedCount = 0
        val failedItems = mutableListOf<Pair<CleanupCandidate, String>>()

        for ((index, candidate) in targets.withIndex()) {
            val revalidation = revalidateTarget(candidate)

            when (revalidation.status) {
                RevalidationStatus.VALID -> {
                    val file = File(candidate.path)
                    val deleted = try {
                        file.delete()
                    } catch (e: Exception) {
                        false
                    }

                    if (deleted) {
                        deletedCount++
                        freedBytes += candidate.size
                    } else {
                        failedItems.add(candidate to "Filesystem rejected deletion request.")
                    }
                }
                RevalidationStatus.STALE_MODIFIED -> {
                    skippedStale++
                    failedItems.add(candidate to (revalidation.detailMessage ?: "Target was modified externally."))
                }
                RevalidationStatus.PERMISSION_RESTRICTED -> {
                    restrictedCount++
                    failedItems.add(candidate to (revalidation.detailMessage ?: "Permission restricted by Android."))
                }
                RevalidationStatus.TARGET_NOT_FOUND -> {
                    // Already gone, count as neutral or skipped
                    failedItems.add(candidate to "Target already removed.")
                }
            }

            onProgress?.invoke(index + 1, targets.size)
        }

        CleanupExecutionReport(
            requestedCount = targets.size,
            deletedCount = deletedCount,
            freedBytes = freedBytes,
            skippedStaleCount = skippedStale,
            restrictedCount = restrictedCount,
            failedCount = failedItems.size,
            failedItems = failedItems
        )
    }

    private fun isAndroidRestrictedPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.contains("/Android/data", ignoreCase = true) ||
            normalized.contains("/Android/obb", ignoreCase = true)
    }
}
