package com.Hrshd1eux.filemanager.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.TwoToneIconBadge
import com.Hrshd1eux.filemanager.core.designsystem.bounceClick
import com.Hrshd1eux.filemanager.core.storage.model.StorageFolderShortcut

/**
 * Customizable Storage Directories List.
 * Allows users to navigate to standard folders, add custom folders/paths,
 * and edit/delete shortcuts for fast access.
 */
@Composable
fun HomeStorageFoldersList(
    shortcuts: List<StorageFolderShortcut>,
    onFolderClick: (path: String, title: String) -> Unit,
    onAddShortcut: (name: String, path: String) -> Unit,
    onEditShortcut: (id: String, name: String, path: String) -> Unit,
    onDeleteShortcut: (id: String) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingShortcut by remember { mutableStateOf<StorageFolderShortcut?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.section_storage_folders),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { showOptionsMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.storage_folders_reset_defaults)) },
                            leadingIcon = {
                                Icon(Icons.Default.RestartAlt, contentDescription = null)
                            },
                            onClick = {
                                showOptionsMenu = false
                                onResetDefaults()
                            }
                        )
                    }
                }
            }
        }

        // Folders Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (shortcuts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.storage_folders_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onResetDefaults) {
                                Text(stringResource(R.string.storage_folders_restore))
                            }
                        }
                    }
                } else {
                    shortcuts.forEachIndexed { index, shortcut ->
                        StorageFolderRow(
                            shortcut = shortcut,
                            onClick = { onFolderClick(shortcut.path, shortcut.name) },
                            onEdit = { editingShortcut = shortcut }
                        )

                        if (index < shortcuts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Quick Add Row at the bottom of the card
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(scaleDown = 0.98f, onClick = { showAddDialog = true })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TwoToneIconBadge(
                        icon = Icons.Default.Add,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 38.dp,
                        iconSize = 20.dp,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = stringResource(R.string.storage_folders_add_custom),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Add Shortcut Dialog
    if (showAddDialog) {
        FolderShortcutEditorDialog(
            title = "Add Folder Shortcut",
            initialName = "",
            initialPath = "/storage/emulated/0/",
            onConfirm = { name, path ->
                showAddDialog = false
                onAddShortcut(name, path)
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Edit Shortcut Dialog
    editingShortcut?.let { shortcut ->
        FolderShortcutEditorDialog(
            title = "Edit Folder Shortcut",
            initialName = shortcut.name,
            initialPath = shortcut.path,
            onConfirm = { name, path ->
                editingShortcut = null
                onEditShortcut(shortcut.id, name, path)
            },
            onDelete = {
                editingShortcut = null
                onDeleteShortcut(shortcut.id)
            },
            onDismiss = { editingShortcut = null }
        )
    }
}

@Composable
private fun StorageFolderRow(
    shortcut: StorageFolderShortcut,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            TwoToneIconBadge(
                icon = Icons.Default.Folder,
                tint = FolderYellow,
                size = 38.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = shortcut.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = shortcut.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit ${shortcut.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FolderShortcutEditorDialog(
    title: String,
    initialName: String,
    initialPath: String,
    onConfirm: (name: String, path: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var path by remember { mutableStateOf(initialPath) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val quickSuggestions = remember {
        listOf(
            "DCIM" to "/storage/emulated/0/DCIM",
            "Camera" to "/storage/emulated/0/DCIM/Camera",
            "Music" to "/storage/emulated/0/Music",
            "Screenshots" to "/storage/emulated/0/Pictures/Screenshots",
            "Telegram" to "/storage/emulated/0/Telegram",
            "Recordings" to "/storage/emulated/0/Recordings",
            "Podcasts" to "/storage/emulated/0/Podcasts",
            "Alarms" to "/storage/emulated/0/Alarms"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Quick suggestions:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSuggestions.forEach { (sugName, sugPath) ->
                        FilterChip(
                            selected = path == sugPath,
                            onClick = {
                                name = sugName
                                path = sugPath
                                errorMessage = null
                            },
                            label = { Text(sugName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.folder_dialog_name_label)) },
                    placeholder = { Text(stringResource(R.string.folder_dialog_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = path,
                    onValueChange = {
                        path = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.folder_dialog_path_label)) },
                    placeholder = { Text("/storage/emulated/0/...") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanPath = path.trim()
                    if (cleanPath.isEmpty() || cleanPath == "/storage/emulated/0" || cleanPath == "/storage/emulated/0/") {
                        errorMessage = "Please enter a valid folder path"
                        return@Button
                    }
                    val cleanName = name.trim().ifEmpty { cleanPath.substringAfterLast('/').ifEmpty { "Folder" } }
                    onConfirm(cleanName, cleanPath)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}
