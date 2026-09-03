package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualActionBar(
    selectedCount: Int,
    totalCount: Int,
    onCloseSelection: () -> Unit,
    onSelectAllToggle: () -> Unit,
    onBulkCopy: () -> Unit,
    onBulkMove: () -> Unit,
    onBulkDelete: () -> Unit,
    onBulkCompress: () -> Unit = {},
    onShare: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Selection"
                )
            }
        },
        actions = {
            // Select All Toggle
            IconButton(onClick = onSelectAllToggle) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = if (selectedCount == totalCount) "Deselect All" else "Select All"
                )
            }

            // Share Action
            IconButton(onClick = onShare, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share"
                )
            }

            // Rename (active when exactly 1 item selected)
            if (selectedCount == 1) {
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Default.DriveFileRenameOutline,
                        contentDescription = "Rename"
                    )
                }
            }

            // Copy
            IconButton(onClick = onBulkCopy, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy"
                )
            }

            // Compress
            IconButton(onClick = onBulkCompress, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = "Compress to ZIP"
                )
            }

            // Move
            IconButton(onClick = onBulkMove, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Move"
                )
            }

            // Delete
            IconButton(onClick = onBulkDelete, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    contentDescription = "Delete"
                )
            }

            // Properties / Info
            IconButton(onClick = onProperties, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Properties"
                )
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
