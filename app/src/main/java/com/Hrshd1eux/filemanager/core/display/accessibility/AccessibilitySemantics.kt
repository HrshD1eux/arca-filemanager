package com.Hrshd1eux.filemanager.core.display.accessibility

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.permission.StorageAccessState
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accessibility standards and TalkBack semantic formatters for ARCA.
 *
 * Conforms to:
 * 1. WCAG 2.1 Level AA touch target minimum: 48dp x 48dp.
 * 2. Meaningful screen-reader content descriptions for folders, files, and permissions.
 * 3. Structured accessibility labels for progress announcements.
 */
object AccessibilitySemantics {

    val MIN_TOUCH_TARGET_SIZE: Dp = 48.dp

    /**
     * Formats an accessible TalkBack label for a file or folder node.
     */
    fun formatNodeLabel(node: StorageNode): String {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(node.lastModified))

        return if (node.isDirectory) {
            "${node.name}, folder, modified $formattedDate"
        } else {
            val formattedSize = FileFormatter.formatFileSize(node.size)
            val ext = node.extension.ifEmpty { "file" }
            "${node.name}, $ext file, $formattedSize, modified $formattedDate"
        }
    }

    /**
     * Formats an accessible TalkBack description for storage access state.
     */
    fun formatStorageAccessDescription(state: StorageAccessState): String {
        return buildString {
            append("Storage access status: ${state.level.displayName}. ")
            if (state.hasAllFilesAccess) {
                append("Full storage access is active. ")
            } else if (state.isScopedAccess) {
                append("Scoped folder access is active with ${state.scopedTreeUris.size} folder permissions. ")
            } else {
                append("Storage access is not granted. ")
            }
            if (state.isRevoked) {
                append("Access was previously granted but is currently revoked by the system. ")
            }
        }
    }

    /**
     * Formats an accessible TalkBack label for file operation progress.
     */
    fun formatProgressAnnouncement(operationName: String, percent: Int, processedBytes: Long, totalBytes: Long): String {
        val formattedProcessed = FileFormatter.formatFileSize(processedBytes)
        val formattedTotal = FileFormatter.formatFileSize(totalBytes)
        return "$operationName: $percent percent complete. $formattedProcessed of $formattedTotal transferred."
    }
}
