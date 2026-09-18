package com.Hrshd1eux.filemanager.core.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.vector.ImageVector
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver

/**
 * Intelligent quick actions available in ARCA.
 */
enum class QuickActionType(
    val title: String,
    val icon: ImageVector
) {
    OPEN("Open", Icons.AutoMirrored.Filled.OpenInNew),
    OPEN_FOLDER("Open", Icons.Default.FolderOpen),
    OPEN_ARCHIVE("Open", Icons.Default.FolderZip),
    MOVE("Move", Icons.AutoMirrored.Filled.DriveFileMove),
    COPY("Copy", Icons.Default.ContentCopy),
    COMPRESS("Compress", Icons.Default.Archive),
    ENCRYPT("Encrypt", Icons.Default.Lock),
    SHARE("Share", Icons.Default.Share),
    PROPERTIES("Properties", Icons.Outlined.Info),
    EXTRACT("Extract", Icons.Default.Unarchive),
    TEST("Test", Icons.Default.TaskAlt),
    ADD("Add", Icons.Default.Add)
}

/**
 * The contextual environment determining which actions are valid.
 */
enum class QuickActionContext {
    FOLDER,
    FILE,
    ARCHIVE;

    companion object {
        fun fromNode(node: StorageNode): QuickActionContext {
            return when {
                node.isDirectory -> FOLDER
                FileTypeResolver.isArchive(node.name) -> ARCHIVE
                else -> FILE
            }
        }
    }

    /**
     * Returns the ordered list of actions for this context.
     *
     * Folder:
     * Open, Move, Copy, Compress, Encrypt, Share
     *
     * File:
     * Open, Share, Copy, Move, Compress, Encrypt, Properties
     *
     * Archive:
     * Open, Extract, Test, Add, Encrypt, Properties
     */
    fun getActions(node: StorageNode? = null): List<QuickActionType> {
        return when (this) {
            FOLDER -> listOf(
                QuickActionType.OPEN_FOLDER,
                QuickActionType.MOVE,
                QuickActionType.COPY,
                QuickActionType.COMPRESS,
                QuickActionType.ENCRYPT,
                QuickActionType.SHARE
            )
            FILE -> listOf(
                QuickActionType.OPEN,
                QuickActionType.SHARE,
                QuickActionType.COPY,
                QuickActionType.MOVE,
                QuickActionType.COMPRESS,
                QuickActionType.ENCRYPT,
                QuickActionType.PROPERTIES
            )
            ARCHIVE -> {
                val isReadOnlyArchive = node != null && (
                    FileTypeResolver.isRar(node.name) ||
                    FileTypeResolver.isIso(node.name) ||
                    node.name.endsWith(".7z", ignoreCase = true) ||
                    node.name.contains(".tar", ignoreCase = true)
                )
                if (isReadOnlyArchive) {
                    listOf(
                        QuickActionType.OPEN_ARCHIVE,
                        QuickActionType.EXTRACT,
                        QuickActionType.TEST,
                        QuickActionType.ENCRYPT,
                        QuickActionType.PROPERTIES
                    )
                } else {
                    listOf(
                        QuickActionType.OPEN_ARCHIVE,
                        QuickActionType.EXTRACT,
                        QuickActionType.TEST,
                        QuickActionType.ADD,
                        QuickActionType.ENCRYPT,
                        QuickActionType.PROPERTIES
                    )
                }
            }
        }
    }
}
