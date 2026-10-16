package com.Hrshd1eux.filemanager.feature.vault

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry
import com.Hrshd1eux.filemanager.core.vault.model.VaultUiState
import com.Hrshd1eux.filemanager.feature.archive.components.ArchiveAddItemsDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.vault.components.CreateVaultSetup
import com.Hrshd1eux.filemanager.feature.vault.components.UnlockVaultView
import com.Hrshd1eux.filemanager.feature.vault.components.VaultLockSettingsDialog
import com.Hrshd1eux.filemanager.feature.vault.components.VaultMoveDialog
import com.Hrshd1eux.filemanager.feature.vault.components.VaultPreviewDialog
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lockSettings by viewModel.lockSettings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val localProvider = remember { LocalFileSystemProvider() }

    var showImportPicker by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showLockSettingsDialog by remember { mutableStateOf(false) }
    var entryToRename by remember { mutableStateOf<VaultFileEntry?>(null) }
    var entryToMove by remember { mutableStateOf<VaultFileEntry?>(null) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<VaultFileEntry?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var entryToExport by remember { mutableStateOf<VaultFileEntry?>(null) }
    var showBatchExportPicker by remember { mutableStateOf(false) }
    var previewEntry by remember { mutableStateOf<VaultFileEntry?>(null) }

    val isUnlocked = uiState is VaultUiState.Unlocked
    val unlockedState = uiState as? VaultUiState.Unlocked
    val isSelectionMode = (unlockedState?.selectedIds?.size ?: 0) > 0

    BackHandler {
        if (isSelectionMode) {
            viewModel.clearSelection()
        } else if (unlockedState?.isSearching == true) {
            viewModel.toggleSearch(false)
        } else if (unlockedState != null && unlockedState.currentVirtualPath.isNotEmpty()) {
            viewModel.navigateUp()
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode && unlockedState != null) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${unlockedState.selectedIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { showBatchMoveDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move Selected", tint = VaultEmerald)
                        }
                        IconButton(onClick = { showBatchExportPicker = true }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export Selected")
                        }
                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else if (unlockedState?.isSearching == true) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = unlockedState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = {
                                Text(
                                    text = "Search vault files...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            trailingIcon = {
                                if (unlockedState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleSearch(false) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Search"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = VaultEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Private Vault",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (unlockedState != null && unlockedState.currentVirtualPath.isNotEmpty()) {
                                viewModel.navigateUp()
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate Back"
                            )
                        }
                    },
                    actions = {
                        if (isUnlocked) {
                            IconButton(onClick = { viewModel.toggleSearch(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Vault",
                                    tint = VaultEmerald
                                )
                            }
                            IconButton(onClick = { showCreateFolderDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "New Folder",
                                    tint = VaultEmerald
                                )
                            }
                            IconButton(onClick = { showLockSettingsDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Vault Settings",
                                    tint = VaultEmerald
                                )
                            }
                            IconButton(onClick = { viewModel.lockVault() }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Vault",
                                    tint = VaultEmerald
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        floatingActionButton = {
            if (isUnlocked && !isSelectionMode && unlockedState?.isSearching != true) {
                FloatingActionButton(
                    onClick = { showImportPicker = true },
                    containerColor = VaultEmerald,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Import Files")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is VaultUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VaultEmerald)
                    }
                }
                is VaultUiState.Unconfigured -> {
                    CreateVaultSetup(
                        onCreateVault = { password ->
                            viewModel.createVault(password)
                        }
                    )
                }
                is VaultUiState.Locked -> {
                    UnlockVaultView(
                        onUnlock = { password ->
                            viewModel.unlockVault(password)
                        },
                        errorMessage = state.error
                    )
                }
                is VaultUiState.Unlocked -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.isSearching && state.searchQuery.isNotBlank()) {
                            if (state.searchResults.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "No matching files in Vault",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "No vault files match \"${state.searchQuery}\". Try searching by partial name, folder, or extension.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.clearSearch() },
                                            colors = ButtonDefaults.buttonColors(containerColor = VaultEmerald)
                                        ) {
                                            Text("Clear Search")
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${state.searchResults.size} results for \"${state.searchQuery}\"",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = VaultEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }

                                    items(state.searchResults, key = { it.id }) { entry ->
                                        val isSelected = state.selectedIds.contains(entry.id)
                                        VaultEntryRow(
                                            entry = entry,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            showPath = true,
                                            onItemClick = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleSelection(entry.id)
                                                } else if (entry.isDirectory) {
                                                    viewModel.navigateInto(entry)
                                                } else {
                                                    previewEntry = entry
                                                }
                                            },
                                            onItemLongClick = {
                                                viewModel.toggleSelection(entry.id)
                                            },
                                            onPreview = { previewEntry = entry },
                                            onExport = { entryToExport = entry },
                                            onMove = { entryToMove = entry },
                                            onRename = { entryToRename = entry },
                                            onDelete = { entryToDelete = entry }
                                        )
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(80.dp))
                                    }
                                }
                            }
                        } else {
                            // Virtual Breadcrumbs
                            VaultBreadcrumbsBar(
                                currentPath = state.currentVirtualPath,
                                onNavigateToRoot = { viewModel.navigateToBreadcrumb(-1) },
                                onNavigateToSegment = { index -> viewModel.navigateToBreadcrumb(index) }
                            )

                            if (state.currentEntries.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = if (state.currentVirtualPath.isEmpty()) "Private Vault is Empty" else "This folder is empty",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Tap '+' to import files or '📁' to create a folder.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${state.currentEntries.size} items in folder (${FileFormatter.formatFileSize(state.totalSize)} total in Vault)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }

                                    items(state.currentEntries, key = { it.id }) { entry ->
                                        val isSelected = state.selectedIds.contains(entry.id)
                                        VaultEntryRow(
                                            entry = entry,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            onItemClick = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleSelection(entry.id)
                                                } else if (entry.isDirectory) {
                                                    viewModel.navigateInto(entry)
                                                } else {
                                                    previewEntry = entry
                                                }
                                            },
                                            onItemLongClick = {
                                                viewModel.toggleSelection(entry.id)
                                            },
                                            onPreview = { previewEntry = entry },
                                            onExport = { entryToExport = entry },
                                            onMove = { entryToMove = entry },
                                            onRename = { entryToRename = entry },
                                            onDelete = { entryToDelete = entry }
                                        )
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(80.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Active Progress Dialog
                    state.activeProgress?.let { progress ->
                        OperationProgressDialog(
                            title = "Vault Operation",
                            progress = progress,
                            onCancel = {}
                        )
                    }
                }
            }
        }
    }

    // In-Memory Secure Preview Dialog
    previewEntry?.let { entry ->
        VaultPreviewDialog(
            entry = entry,
            vaultManager = viewModel.vaultManager,
            onExport = {
                val target = entry
                previewEntry = null
                entryToExport = target
            },
            onDismiss = { previewEntry = null }
        )
    }

    // Import Items Source Picker Dialog
    if (showImportPicker) {
        ArchiveAddItemsDialog(
            storageProvider = localProvider,
            onItemsSelected = { sourceFiles ->
                showImportPicker = false
                viewModel.importFiles(sourceFiles)
            },
            onDismiss = { showImportPicker = false }
        )
    }

    // Create Virtual Folder Dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Folder in Vault") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = folderName.trim()
                        if (name.isNotEmpty()) {
                            showCreateFolderDialog = false
                            viewModel.createFolder(name)
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    entryToRename?.let { entry ->
        var newName by remember { mutableStateOf(entry.originalName) }
        AlertDialog(
            onDismissRequest = { entryToRename = null },
            title = { Text("Rename in Vault") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty() && name != entry.originalName) {
                            entryToRename = null
                            viewModel.renameEntry(entry.id, name)
                        } else {
                            entryToRename = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Move Single Entry Dialog
    entryToMove?.let { entry ->
        VaultMoveDialog(
            vaultManager = viewModel.vaultManager,
            count = 1,
            onConfirmMove = { targetPath ->
                entryToMove = null
                viewModel.moveEntry(entry.id, targetPath)
            },
            onDismiss = { entryToMove = null }
        )
    }

    // Batch Move Dialog
    if (showBatchMoveDialog && unlockedState != null) {
        VaultMoveDialog(
            vaultManager = viewModel.vaultManager,
            count = unlockedState.selectedIds.size,
            onConfirmMove = { targetPath ->
                showBatchMoveDialog = false
                viewModel.moveSelected(targetPath)
            },
            onDismiss = { showBatchMoveDialog = false }
        )
    }

    // Single Delete Confirmation Dialog
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete '${entry.originalName}'? The encrypted data will be wiped immediately."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryToDelete = null
                        viewModel.deleteEntry(entry)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteDialog && unlockedState != null) {
        val count = unlockedState.selectedIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Delete $count items?") },
            text = {
                Text("Are you sure you want to permanently delete these $count items from the encrypted vault?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        viewModel.deleteSelected()
                    }
                ) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Single Entry Dialog
    entryToExport?.let { entry ->
        val defaultDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        DestinationPickerDialog(
            storageProvider = localProvider,
            title = "Export '${entry.originalName}' to...",
            confirmButtonText = "Export Here",
            onDestinationSelected = { destNode, _ ->
                entryToExport = null
                viewModel.exportSingle(entry, File(destNode.path))
            },
            onDismiss = { entryToExport = null }
        )
    }

    // Batch Export Dialog
    if (showBatchExportPicker && unlockedState != null) {
        val count = unlockedState.selectedIds.size
        DestinationPickerDialog(
            storageProvider = localProvider,
            title = "Export $count items to...",
            confirmButtonText = "Export Here",
            onDestinationSelected = { destNode, _ ->
                showBatchExportPicker = false
                viewModel.exportSelected(File(destNode.path))
            },
            onDismiss = { showBatchExportPicker = false }
        )
    }

    // Vault Lock Settings Dialog
    if (showLockSettingsDialog) {
        VaultLockSettingsDialog(
            currentSettings = lockSettings,
            onSaveSettings = { newSettings ->
                viewModel.updateLockSettings(newSettings)
            },
            onManualLock = {
                viewModel.lockVault()
            },
            onDismiss = { showLockSettingsDialog = false }
        )
    }
}

@Composable
private fun VaultBreadcrumbsBar(
    currentPath: String,
    onNavigateToRoot: () -> Unit,
    onNavigateToSegment: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(currentPath) { currentPath.split("/").filter { it.isNotEmpty() } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (segments.isEmpty()) VaultEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            onClick = onNavigateToRoot
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = VaultEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Vault Root",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (segments.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                    color = if (segments.isEmpty()) VaultEmerald else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        segments.forEachIndexed { index, segment ->
            Text(
                text = " / ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val isLast = index == segments.size - 1
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isLast) VaultEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                onClick = { onNavigateToSegment(index) }
            ) {
                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    color = if (isLast) VaultEmerald else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultEntryRow(
    entry: VaultFileEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    showPath: Boolean = false,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onItemClick,
                    onLongClick = onItemLongClick
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onItemClick() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (entry.isDirectory) FolderYellow.copy(alpha = 0.15f) else VaultEmerald.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = if (entry.isDirectory) FolderYellow else VaultEmerald,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.originalName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showPath && entry.parentVirtualPath.isNotEmpty()) {
                    Text(
                        text = "📁 /${entry.parentVirtualPath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isDirectory) {
                        Text(
                            text = "Encrypted Folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = FileFormatter.formatFileSize(entry.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • AES-256",
                            style = MaterialTheme.typography.bodySmall,
                            color = VaultEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (!entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Preview in memory") },
                            leadingIcon = {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = VaultEmerald)
                            },
                            onClick = {
                                showMenu = false
                                onPreview()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Export to Storage") },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to...") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onMove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
