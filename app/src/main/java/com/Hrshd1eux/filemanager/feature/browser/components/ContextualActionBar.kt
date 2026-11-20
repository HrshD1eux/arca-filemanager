package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.Hrshd1eux.filemanager.R

/**
 * Streamlined Selection Top App Bar with overflow action menu.
 *
 * Displays:
 * [X] N selected                     [Properties] [Share] [Select All] [MoreVert (Overflow)]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualActionBar(
    selectedCount: Int,
    totalCount: Int,
    onCloseSelection: () -> Unit,
    onSelectAllToggle: () -> Unit,
    onBulkCopy: () -> Unit = {},
    onBulkMove: () -> Unit = {},
    onBulkDelete: () -> Unit = {},
    onBulkCompress: () -> Unit = {},
    onShare: () -> Unit = {},
    onRename: () -> Unit = {},
    onProperties: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allSelected = totalCount > 0 && selectedCount == totalCount
    var showOverflowMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.selection_count, selectedCount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_clear_selection)
                )
            }
        },
        actions = {
            // Quick Properties button (always visible in selection top bar)
            IconButton(onClick = onProperties) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.action_properties)
                )
            }
            // Quick Share button
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_share)
                )
            }
            // Select All / Deselect All Toggle
            IconButton(onClick = onSelectAllToggle) {
                Icon(
                    imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                    contentDescription = if (allSelected) stringResource(R.string.action_deselect_all) else stringResource(R.string.action_select_all)
                )
            }
            // 3-Dot Overflow Menu
            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more)
                    )
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_to)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                        },
                        onClick = {
                            showOverflowMenu = false
                            onBulkCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_to)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                        },
                        onClick = {
                            showOverflowMenu = false
                            onBulkMove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compress (.zip)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Archive, contentDescription = null)
                        },
                        onClick = {
                            showOverflowMenu = false
                            onBulkCompress()
                        }
                    )
                    if (selectedCount == 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.DriveFileRenameOutline, contentDescription = null)
                            },
                            onClick = {
                                showOverflowMenu = false
                                onRename()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            showOverflowMenu = false
                            onBulkDelete()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    )
}
