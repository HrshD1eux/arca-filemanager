package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
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

/**
 * Streamlined Selection Top App Bar.
 *
 * Displays:
 * [X] N selected                     [Properties] [Share] [Select All / Deselect All]
 *
 * Primary actions (Move to | Copy to | Properties | Delete | More) are also available on SelectionBottomBar.
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

    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear Selection"
                )
            }
        },
        actions = {
            // Quick Properties button (always visible in selection top bar)
            IconButton(onClick = onProperties) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Properties"
                )
            }
            // Quick Share button
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share"
                )
            }
            // Select All / Deselect All Toggle
            IconButton(onClick = onSelectAllToggle) {
                Icon(
                    imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                    contentDescription = if (allSelected) "Deselect All" else "Select All"
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
