package com.Hrshd1eux.filemanager.feature.trash

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.core.thumbnail.FileThumbnailView
import com.Hrshd1eux.filemanager.core.trash.model.TrashEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = viewModel()
) {
    val entries by viewModel.sortedEntries.collectAsStateWithLifecycle()
    val allEntries by viewModel.entries.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val isSelectionMode = selectedIds.isNotEmpty()

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var entryToRestore by remember { mutableStateOf<TrashEntry?>(null) }
    var entryToShred by remember { mutableStateOf<TrashEntry?>(null) }
    var showBulkRestoreDialog by remember { mutableStateOf(false) }
    var showBulkShredDialog by remember { mutableStateOf(false) }
    var entryToPreview by remember { mutableStateOf<TrashEntry?>(null) }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        val allSelected = selectedIds.size >= entries.size && entries.isNotEmpty()
                        IconButton(onClick = {
                            if (allSelected) viewModel.clearSelection() else viewModel.selectAll()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All"
                            )
                        }

                        // Restore Selected
                        IconButton(
                            onClick = { showBulkRestoreDialog = true },
                            enabled = !isOperating
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore Selected")
                        }

                        // Shred Selected
                        IconButton(
                            onClick = { showBulkShredDialog = true },
                            enabled = !isOperating
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "Shred Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "Trash (${allEntries.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (entries.isNotEmpty()) {
                            // View Mode Toggle Button
                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(
                                    imageVector = if (viewMode == TrashViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = "Toggle View Mode"
                                )
                            }

                            // Sort By Button
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort By"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    TrashSortBy.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option.displayName,
                                                    fontWeight = if (sortBy == option) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sortBy == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                viewModel.updateSortBy(option)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Empty Trash Button
                            TextButton(
                                onClick = { showEmptyTrashDialog = true },
                                enabled = !isOperating
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Empty")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Retention & Anti-Recovery Policy Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Items in Trash are retained for 30 days before auto-purge. Permanent shredding uses multi-pass CSPRNG overwriting, header zeroing, and hardware flush.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Trash is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Deleted files and folders will appear here and can be restored or cryptographically shredded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                if (viewMode == TrashViewMode.LIST) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            TrashEntryListCard(
                                entry = entry,
                                isSelected = selectedIds.contains(entry.id),
                                isSelectionMode = isSelectionMode,
                                isOperating = isOperating,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(entry.id)
                                    } else {
                                        entryToPreview = entry
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(entry.id) },
                                onToggleSelect = { viewModel.toggleSelection(entry.id) },
                                onRestore = { entryToRestore = entry },
                                onShred = { entryToShred = entry }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            TrashEntryGridCard(
                                entry = entry,
                                isSelected = selectedIds.contains(entry.id),
                                isSelectionMode = isSelectionMode,
                                isOperating = isOperating,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(entry.id)
                                    } else {
                                        entryToPreview = entry
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(entry.id) },
                                onToggleSelect = { viewModel.toggleSelection(entry.id) },
                                onRestore = { entryToRestore = entry },
                                onShred = { entryToShred = entry }
                            )
                        }
                    }
                }
            }
        }
    }

    // Single Restore Confirmation Dialog
    if (entryToRestore != null) {
        val target = entryToRestore!!
        AlertDialog(
            onDismissRequest = { entryToRestore = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Restore File?")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Do you want to restore \"${target.originalName}\" back to its original location?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = target.originalPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRestore = target
                        entryToRestore = null
                        viewModel.restore(toRestore)
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToRestore = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Restore Confirmation Dialog
    if (showBulkRestoreDialog) {
        val selectedEntries = entries.filter { selectedIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showBulkRestoreDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Restore ${selectedEntries.size} Files?")
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to restore all ${selectedEntries.size} selected items back to their original locations?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkRestoreDialog = false
                        viewModel.restoreMultiple(selectedEntries)
                    }
                ) {
                    Text("Restore All (${selectedEntries.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Empty Trash Confirmation Dialog
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Empty Trash & Shred All?")
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete all ${entries.size} items? " +
                            "ARCA will shred these files with multi-pass CSPRNG overwriting, header zeroing, and hardware cache flushes. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashDialog = false
                        viewModel.emptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Shred All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single File Permanent Shred Confirmation Dialog
    if (entryToShred != null) {
        val target = entryToShred!!
        AlertDialog(
            onDismissRequest = { entryToShred = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Permanently Shred File?")
                }
            },
            text = {
                Text(
                    text = "Permanently shred \"${target.originalName}\"? " +
                            "File clusters will be overwritten with pseudo-random bytes and header zeroes, followed by hardware cache flushes. " +
                            "This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toShred = target
                        entryToShred = null
                        viewModel.shredPermanently(toShred)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Shred Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToShred = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Shred Confirmation Dialog
    if (showBulkShredDialog) {
        val selectedEntries = entries.filter { selectedIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showBulkShredDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Permanently Shred ${selectedEntries.size} Files?")
                }
            },
            text = {
                Text(
                    text = "Permanently shred all ${selectedEntries.size} selected items? " +
                            "Files will be overwritten and purged from disk. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkShredDialog = false
                        viewModel.shredMultiple(selectedEntries)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Shred Permanently (${selectedEntries.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkShredDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // File Preview Dialog
    if (entryToPreview != null) {
        TrashPreviewDialog(
            entry = entryToPreview!!,
            onRestore = {
                val target = entryToPreview!!
                entryToPreview = null
                entryToRestore = target
            },
            onShred = {
                val target = entryToPreview!!
                entryToPreview = null
                entryToShred = target
            },
            onDismiss = { entryToPreview = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashEntryListCard(
    entry: TrashEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isOperating: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit,
    onShred: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            val storageNode = remember(entry.id) {
                StorageNode(
                    id = entry.id,
                    name = entry.originalName,
                    path = entry.trashedPath,
                    size = entry.sizeBytes,
                    lastModified = entry.trashedTimestampMillis,
                    isDirectory = entry.isDirectory,
                    mimeType = entry.mimeType
                )
            }

            FileThumbnailView(
                node = storageNode,
                categoryColor = if (entry.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
                iconSize = 22.dp,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.originalName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${FileFormatter.formatFileSize(entry.sizeBytes)} • Deleted ${FileFormatter.formatDateTime(entry.trashedTimestampMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.originalPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                Spacer(modifier = Modifier.width(8.dp))

                // Restore action
                IconButton(
                    onClick = onRestore,
                    enabled = !isOperating,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Shred action
                IconButton(
                    onClick = onShred,
                    enabled = !isOperating,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Shred Permanently",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashEntryGridCard(
    entry: TrashEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isOperating: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit,
    onShred: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center
            ) {
                val storageNode = remember(entry.id) {
                    StorageNode(
                        id = entry.id,
                        name = entry.originalName,
                        path = entry.trashedPath,
                        size = entry.sizeBytes,
                        lastModified = entry.trashedTimestampMillis,
                        isDirectory = entry.isDirectory,
                        mimeType = entry.mimeType
                    )
                }

                FileThumbnailView(
                    node = storageNode,
                    categoryColor = if (entry.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 36.dp,
                    shape = RoundedCornerShape(8.dp)
                )

                if (isSelectionMode) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.originalName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = FileFormatter.formatFileSize(entry.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isSelectionMode) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onRestore,
                        enabled = !isOperating,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onShred,
                        enabled = !isOperating,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Shred Permanently",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashPreviewDialog(
    entry: TrashEntry,
    onRestore: () -> Unit,
    onShred: () -> Unit,
    onDismiss: () -> Unit
) {
    val file = remember(entry.trashedPath) { File(entry.trashedPath) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var textSnippet by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(entry.trashedPath) {
        withContext(Dispatchers.IO) {
            if (file.exists()) {
                val isImg = FileTypeResolver.isImage(entry.originalName)
                if (isImg) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    imageBitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
                } else if (FileTypeResolver.isDocument(entry.originalName) || entry.originalName.endsWith(".txt", true) || entry.originalName.endsWith(".json", true) || entry.originalName.endsWith(".log", true)) {
                    textSnippet = runCatching {
                        file.bufferedReader().use { reader ->
                            val chars = CharArray(4096)
                            val len = reader.read(chars)
                            if (len > 0) String(chars, 0, len) else "Empty file."
                        }
                    }.getOrNull()
                }
            }
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .heightIn(max = 640.dp)
            .padding(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.originalName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Preview Section
                val bmp = imageBitmap
                val txt = textSnippet
                if (bmp != null && !bmp.isRecycled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = entry.originalName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                } else if (txt != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = txt,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // File Details
                Text(
                    text = "File Details",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                DetailItemRow(label = "Size", value = FileFormatter.formatFileSize(entry.sizeBytes))
                DetailItemRow(label = "Deleted At", value = FileFormatter.formatDateTime(entry.trashedTimestampMillis))
                DetailItemRow(label = "Original Location", value = entry.originalPath)
                if (entry.mimeType != null) {
                    DetailItemRow(label = "Type", value = entry.mimeType)
                }
            }
        },
        confirmButton = {
            Row {
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onShred,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Shred")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailItemRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
