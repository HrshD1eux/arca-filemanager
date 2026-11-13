package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

/**
 * Signature Bottom Action Bar for multi-selection mode:
 *
 * Selection:
 * N selected
 *
 * Move to | Copy to | Properties | Delete | More
 */
@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onCompress: () -> Unit,
    onDelete: () -> Unit,
    onEncrypt: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onOpenWith: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    onCompare: (() -> Unit)? = null,
    onSendViaArcaDrop: (() -> Unit)? = null,
    lastMoveDestination: StorageNode? = null,
    onMoveToRecent: ((StorageNode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Fast 1-Tap "Move to Recent" Banner
            if (lastMoveDestination != null && onMoveToRecent != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onMoveToRecent(lastMoveDestination) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_fast_move_desc, selectedCount, lastMoveDestination.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.action_fast_move_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Move to
                BottomActionItem(
                    title = stringResource(R.string.action_move_to),
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    onClick = onMove,
                    modifier = Modifier.weight(1f)
                )

                // 2. Copy to
                BottomActionItem(
                    title = stringResource(R.string.action_copy_to),
                    icon = Icons.Default.ContentCopy,
                    onClick = onCopy,
                    modifier = Modifier.weight(1f)
                )

                // 3. Properties (direct access to size & details)
                BottomActionItem(
                    title = stringResource(R.string.action_properties),
                    icon = Icons.Outlined.Info,
                    onClick = onProperties,
                    modifier = Modifier.weight(1f)
                )

                // 4. Delete
                BottomActionItem(
                    title = stringResource(R.string.action_delete),
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )

                // 5. More
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    BottomActionItem(
                        title = stringResource(R.string.action_more),
                        icon = Icons.Default.MoreVert,
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        // Rename
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DriveFileRenameOutline,
                                    contentDescription = stringResource(R.string.action_rename)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onRename()
                            }
                        )

                        // Open with...
                        if (onOpenWith != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_with)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = stringResource(R.string.action_open_with)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenWith()
                                }
                            )
                        }

                        // Play
                        if (onPlay != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_play)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.action_play)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onPlay()
                                }
                            )
                        }

                        // Compress
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_compress)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = stringResource(R.string.action_compress)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onCompress()
                            }
                        )

                        // Share
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.action_share)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onShare()
                            }
                        )

                        // Send via ARCA Drop
                        if (onSendViaArcaDrop != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_send_arca_drop)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = stringResource(R.string.action_send_arca_drop),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onSendViaArcaDrop()
                                }
                            )
                        }

                        // Encrypt
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.action_encrypt),
                                    color = VaultEmerald,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.action_encrypt),
                                    tint = VaultEmerald
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onEncrypt()
                            }
                        )

                        // Compare (available when 2 items selected)
                        if (selectedCount == 2 && onCompare != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_compare_items)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                        contentDescription = stringResource(R.string.action_compare_items)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onCompare()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
