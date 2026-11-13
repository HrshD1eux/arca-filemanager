package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.SelectionHaptics
import com.Hrshd1eux.filemanager.core.thumbnail.FileThumbnailView
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropManager
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropPayload

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorageNodeGridItem(
    node: StorageNode,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onQuickActionClick: (() -> Unit)? = null,
    allSelectedNodes: List<StorageNode> = emptyList(),
    onDropIntoFolder: ((StorageNode, DragDropPayload) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else if (onQuickActionClick != null) {
                        onQuickActionClick()
                    } else {
                        onToggleSelection()
                    }
                }
            ),
        shape = RoundedCornerShape(12.dp),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        onToggleSelection()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        FileThumbnailView(
                            node = node,
                            categoryColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 28.dp,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                val subtitle = if (node.isDirectory) {
                    stringResource(R.string.type_folder)
                } else {
                    FileFormatter.formatFileSize(node.size)
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
