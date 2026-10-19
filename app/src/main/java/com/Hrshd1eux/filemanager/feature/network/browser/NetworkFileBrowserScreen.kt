package com.Hrshd1eux.filemanager.feature.network.browser

import android.content.Context
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DensityMedium
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepositoryImpl
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.feature.browser.components.CreateFolderDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.browser.components.SortBottomSheet
import com.Hrshd1eux.filemanager.feature.browser.components.StagedTransferFloatingBar
import com.Hrshd1eux.filemanager.feature.browser.components.StorageNodeCompactItem
import com.Hrshd1eux.filemanager.feature.browser.components.StorageNodeGridItem
import com.Hrshd1eux.filemanager.feature.browser.components.StorageNodeItem
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet
import java.io.File

/**
 * Remote Network File Browser Screen.
 *
 * Implements an identical visual layout and user experience to the local FileBrowserScreen:
 * - TopAppBar with folder name, remote host, protocol security badge
 * - Interactive breadcrumb trail navigation
 * - List, Compact, and Grid view modes
 * - Real-time sorting and filtering
 * - Multi-selection with batch delete and download actions
 * - Dialogs: Create Folder, Rename, Delete Confirmation, Properties
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkFileBrowserScreen(
    connectionId: String,
    initialPath: String? = null,
    title: String = "Remote Server",
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: NetworkFileBrowserViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val repo = NetworkConnectionRepositoryImpl(context.applicationContext)
                return NetworkFileBrowserViewModel(connectionId = connectionId, repository = repo) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedNodeIds by viewModel.selectedNodeIds.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val stagedTransfer by viewModel.stagedTransfer.collectAsStateWithLifecycle()
    val activeOperation by viewModel.activeOperation.collectAsStateWithLifecycle()
    val activeProgress by viewModel.activeProgress.collectAsStateWithLifecycle()
    val confirmDelete by remember { com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository.getInstance() }.confirmDelete.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var showMenu by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var nodeToRename by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var bulkNodesToDelete by remember { mutableStateOf<List<StorageNode>?>(null) }
    var selectedNodeForProperties by remember { mutableStateOf<StorageNode?>(null) }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler {
        if (selectedNodeIds.isNotEmpty()) {
            viewModel.clearSelection()
        } else if (isSearching) {
            viewModel.toggleSearch()
        } else {
            val handled = viewModel.navigateUp()
            if (!handled) {
                viewModel.disconnect()
                onNavigateBack()
            }
        }
    }

    val downloadsDir = remember {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Filter remote items...") },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        )
                    } else {
                        Column {
                            val displayTitle = when (val state = uiState) {
                                is NetworkBrowserUiState.Success -> state.currentTitle
                                else -> title
                            }
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Security Subtitle & Host Indicator
                            val successState = uiState as? NetworkBrowserUiState.Success
                            if (successState != null) {
                                val config = successState.serverConfig
                                val isFtp = config.protocol == NetworkProtocol.FTP
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isFtp) Icons.Default.Warning else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isFtp) Color(0xFFE65100) else VaultEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isFtp) "${config.host} • ⚠ Plaintext" else "${config.host} • ${config.protocol.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isFtp) Color(0xFFBF360C) else VaultEmerald,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedNodeIds.isNotEmpty()) {
                            viewModel.clearSelection()
                        } else if (isSearching) {
                            viewModel.toggleSearch()
                        } else {
                            val handled = viewModel.navigateUp()
                            if (!handled) {
                                viewModel.disconnect()
                                onNavigateBack()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!isSearching) {
                        // Search / Filter
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Filter")
                        }

                        // View Mode Cycle (List -> Compact -> Grid)
                        IconButton(onClick = { viewModel.cycleViewMode() }) {
                            Icon(
                                imageVector = when (viewMode) {
                                    ViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                    ViewMode.COMPACT -> Icons.Default.DensityMedium
                                    ViewMode.GRID -> Icons.Default.GridView
                                },
                                contentDescription = "View Mode"
                            )
                        }

                        // Sort Order
                        IconButton(onClick = { showSortBottomSheet = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }

                        // Refresh
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }

                        // Overflow Menu
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Folder") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showCreateFolderDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Disconnect", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.disconnect()
                                        onNavigateBack()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState is NetworkBrowserUiState.Success && selectedNodeIds.isEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New Folder"
                    )
                }
            }
        },
        bottomBar = {
            if (selectedNodeIds.isNotEmpty() && uiState is NetworkBrowserUiState.Success) {
                val state = uiState as NetworkBrowserUiState.Success
                val selectedItems = state.items.filter { selectedNodeIds.contains(it.id) }

                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${selectedNodeIds.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { viewModel.selectAll() }) {
                                    Text("All")
                                }

                                TextButton(onClick = { viewModel.stageNodesForCopy(selectedItems) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy")
                                }

                                TextButton(onClick = { viewModel.stageNodesForMove(selectedItems) }) {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Move")
                                }

                                Button(
                                    onClick = { viewModel.downloadSelected(selectedItems, downloadsDir) },
                                    enabled = !isDownloading
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download")
                                }

                                IconButton(
                                    onClick = {
                                        if (confirmDelete) {
                                            bulkNodesToDelete = selectedItems
                                        } else {
                                            viewModel.delete(selectedItems)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedNodeIds.isEmpty() && stagedTransfer != null && uiState is NetworkBrowserUiState.Success) {
                val currentFolder = viewModel.getCurrentFolderNode()
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    StagedTransferFloatingBar(
                        transfer = stagedTransfer!!,
                        currentFolder = currentFolder,
                        onCommitTransfer = { viewModel.commitStagedTransfer(currentFolder) },
                        onCancel = { viewModel.clearStagedTransfer() }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Interactive Breadcrumb Trail
            val successState = uiState as? NetworkBrowserUiState.Success
            if (successState != null && successState.breadcrumbs.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        successState.breadcrumbs.forEachIndexed { index, crumb ->
                            val isRoot = index == 0
                            val isLast = index == successState.breadcrumbs.size - 1

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isLast) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.loadDirectory(crumb.path) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isRoot) {
                                        Icon(
                                            imageVector = if (successState.isEncryptedTransport) Icons.Default.Lock else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (successState.isEncryptedTransport) VaultEmerald else Color(0xFFE65100),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = crumb.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Main State Switcher
            when (val state = uiState) {
                is NetworkBrowserUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is NetworkBrowserUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                val titleText = if (state.serverName != null) {
                                    "Could not connect to ${state.serverName}"
                                } else {
                                    "Could not connect"
                                }
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (!state.detail.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = state.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (state.canRetry) {
                                        Button(onClick = { viewModel.refresh() }) {
                                            Text("Retry")
                                        }
                                    }
                                    TextButton(onClick = onNavigateBack) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }
                }

                is NetworkBrowserUiState.Success -> {
                    if (state.items.isEmpty()) {
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
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (state.searchQuery.isNotBlank()) "No items match '${state.searchQuery}'" else "This remote folder is empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val selectedItems = state.items.filter { selectedNodeIds.contains(it.id) }

                        when (viewMode) {
                            ViewMode.GRID -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(state.items, key = { it.id }) { node ->
                                        StorageNodeGridItem(
                                            node = node,
                                            isSelected = selectedNodeIds.contains(node.id),
                                            isSelectionMode = selectedNodeIds.isNotEmpty(),
                                            allSelectedNodes = selectedItems,
                                            onClick = {
                                                if (node.isDirectory) {
                                                    viewModel.navigateInto(node)
                                                } else {
                                                    viewModel.downloadFile(node, downloadsDir)
                                                }
                                            },
                                            onToggleSelection = {
                                                viewModel.toggleSelection(node.id)
                                            },
                                            onQuickActionClick = {
                                                selectedNodeForProperties = node
                                            }
                                        )
                                    }
                                }
                            }

                            ViewMode.COMPACT -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(state.items, key = { it.id }) { node ->
                                        StorageNodeCompactItem(
                                            node = node,
                                            isSelected = selectedNodeIds.contains(node.id),
                                            isSelectionMode = selectedNodeIds.isNotEmpty(),
                                            isFavorite = false,
                                            allSelectedNodes = selectedItems,
                                            onClick = {
                                                if (node.isDirectory) {
                                                    viewModel.navigateInto(node)
                                                } else {
                                                    viewModel.downloadFile(node, downloadsDir)
                                                }
                                            },
                                            onToggleSelection = {
                                                viewModel.toggleSelection(node.id)
                                            },
                                            onToggleFavorite = {},
                                            onCopyClick = {},
                                            onMoveClick = {},
                                            onRenameClick = { nodeToRename = node },
                                            onDeleteClick = {
                                                if (confirmDelete) {
                                                    nodeToDelete = node
                                                } else {
                                                    viewModel.delete(listOf(node))
                                                }
                                            },
                                            onShareClick = {},
                                            onInfoClick = { selectedNodeForProperties = node }
                                        )
                                    }
                                }
                            }

                            ViewMode.LIST -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(state.items, key = { it.id }) { node ->
                                        StorageNodeItem(
                                            node = node,
                                            isSelected = selectedNodeIds.contains(node.id),
                                            isSelectionMode = selectedNodeIds.isNotEmpty(),
                                            isFavorite = false,
                                            allSelectedNodes = selectedItems,
                                            onClick = {
                                                if (node.isDirectory) {
                                                    viewModel.navigateInto(node)
                                                } else {
                                                    viewModel.downloadFile(node, downloadsDir)
                                                }
                                            },
                                            onToggleSelection = {
                                                viewModel.toggleSelection(node.id)
                                            },
                                            onToggleFavorite = {},
                                            onCopyClick = { viewModel.stageNodesForCopy(listOf(node)) },
                                            onMoveClick = { viewModel.stageNodesForMove(listOf(node)) },
                                            onRenameClick = { nodeToRename = node },
                                            onDeleteClick = {
                                                if (confirmDelete) {
                                                    nodeToDelete = node
                                                } else {
                                                    viewModel.delete(listOf(node))
                                                }
                                            },
                                            onShareClick = {},
                                            onInfoClick = { selectedNodeForProperties = node },
                                            onDownloadClick = { viewModel.downloadFile(node, downloadsDir) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Sort Bottom Sheet
    if (showSortBottomSheet) {
        SortBottomSheet(
            currentOrder = sortOrder,
            onSortOrderChange = { newOrder ->
                viewModel.setSortOrder(newOrder)
            },
            onDismiss = { showSortBottomSheet = false }
        )
    }

    // Create Remote Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirmCreate = { folderName ->
                viewModel.createDirectory(folderName)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    // Rename Remote Node Dialog
    nodeToRename?.let { node ->
        com.Hrshd1eux.filemanager.feature.browser.components.RenameDialog(
            node = node,
            onConfirmRename = { newName ->
                viewModel.rename(node, newName)
                nodeToRename = null
            },
            onDismiss = { nodeToRename = null }
        )
    }

    // Single Delete Dialog
    nodeToDelete?.let { node ->
        DeleteConfirmationDialog(
            node = node,
            onConfirmDelete = {
                viewModel.delete(listOf(node))
                nodeToDelete = null
            },
            onDismiss = { nodeToDelete = null }
        )
    }

    // Bulk Delete Dialog
    bulkNodesToDelete?.let { nodes ->
        if (nodes.isNotEmpty()) {
            DeleteConfirmationDialog(
                node = nodes.first(),
                onConfirmDelete = {
                    viewModel.delete(nodes)
                    bulkNodesToDelete = null
                },
                onDismiss = { bulkNodesToDelete = null }
            )
        }
    }

    // Remote File Properties Sheet
    selectedNodeForProperties?.let { node ->
        FilePropertiesSheet(
            node = node,
            onDismiss = { selectedNodeForProperties = null }
        )
    }

    // Active Transfer Progress Dialog
    val currentActiveOp = activeOperation
    val currentProgress = activeProgress
    if (currentActiveOp != null && currentProgress != null) {
        OperationProgressDialog(
            title = currentActiveOp.title,
            progress = currentProgress,
            onCancel = { viewModel.cancelActiveOperation() }
        )
    }
}
