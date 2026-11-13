package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.thumbnail.FileThumbnailView
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver

import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import com.Hrshd1eux.filemanager.core.designsystem.SelectionHaptics
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropManager
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropPayload
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorageNodeCompactItem(
    node: StorageNode,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onCompressClick: () -> Unit = {},
    onExtractClick: () -> Unit = {},
    onTestArchiveClick: () -> Unit = {},
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenWithClick: () -> Unit = {},
    onSendToClick: () -> Unit = {},
    onChecksumClick: () -> Unit = {},
    onInfoClick: () -> Unit,
    onQuickActionClick: (() -> Unit)? = null,
    allSelectedNodes: List<StorageNode> = emptyList(),
    onDropIntoFolder: ((StorageNode, DragDropPayload) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var isHoveredAsDropTarget by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current

    val dndTarget = remember(node) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isHoveredAsDropTarget = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isHoveredAsDropTarget = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isHoveredAsDropTarget = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isHoveredAsDropTarget = false
                val clipData = event.toAndroidDragEvent().clipData
                val payload = DragDropManager.parseClipData(clipData) ?: return false
                onDropIntoFolder?.invoke(node, payload)
                return true
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (node.name.startsWith(".")) 0.65f else 1.0f)
            .then(
                if (node.isDirectory && onDropIntoFolder != null) {
                    Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = { true },
                        target = dndTarget
                    )
                } else Modifier
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        onToggleSelection()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    SelectionHaptics.playLongPressFeedback(context, view)
                    onToggleSelection()
                }
            ),
        shape = RoundedCornerShape(8.dp),
        border = if (isHoveredAsDropTarget) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isHoveredAsDropTarget) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        onToggleSelection()
                    },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .dragAndDropSource {
                        detectTapGestures(
                            onLongPress = {
                                val nodesToDrag = if (isSelected && isSelectionMode && allSelectedNodes.isNotEmpty()) {
                                    allSelectedNodes
                                } else {
                                    listOf(node)
                                }
                                startTransfer(
                                    DragAndDropTransferData(
                                        clipData = DragDropManager.createClipData(nodesToDrag)
                                    )
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (node.isDirectory) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(FolderYellow.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = FolderYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    FileThumbnailView(
                        node = node,
                        categoryColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                        iconSize = 16.dp,
                        shape = RoundedCornerShape(6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (!node.isDirectory) {
                Text(
                    text = FileFormatter.formatFileSize(node.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = {
                            if (onQuickActionClick != null) {
                                onQuickActionClick()
                            } else {
                                showMenu = !showMenu
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_to)) },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onCopyClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_to)) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onMoveClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_compress_zip)) },
                            leadingIcon = {
                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onCompressClick()
                            }
                        )
                        if (FileTypeResolver.isArchive(node.name)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_extract)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onExtractClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_test_archive)) },
                                leadingIcon = {
                                    Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onTestArchiveClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onRenameClick()
                            }
                        )
                        if (!node.isDirectory) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_with)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenWithClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onShareClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_checksums)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onChecksumClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) stringResource(R.string.action_remove_from_favorites) else stringResource(R.string.action_add_to_favorites)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_properties)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onInfoClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
