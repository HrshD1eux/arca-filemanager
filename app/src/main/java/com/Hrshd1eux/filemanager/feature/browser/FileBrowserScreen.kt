package com.Hrshd1eux.filemanager.feature.browser

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.permission.StoragePermissionManager
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem

import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet

import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.Hrshd1eux.filemanager.core.storage.util.FileShareHelper
import com.Hrshd1eux.filemanager.feature.browser.components.BulkDeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.ContextualActionBar
import com.Hrshd1eux.filemanager.feature.browser.components.CreateFolderDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.browser.components.RenameDialog
import com.Hrshd1eux.filemanager.feature.properties.BulkPropertiesSheet

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DensityMedium
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.feature.browser.components.SortBottomSheet
import com.Hrshd1eux.filemanager.feature.browser.components.StorageNodeCompactItem
import com.Hrshd1eux.filemanager.feature.browser.components.StorageNodeGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    initialPath: String?,
    title: String,
    onNavigateBack: () -> Unit,
    onNavigateToSearch: (rootPath: String?) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FileBrowserViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeProgress by viewModel.activeProgress.collectAsStateWithLifecycle()
    val selectedNodeIds by viewModel.selectedNodeIds.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedNodeForProperties by remember { mutableStateOf<StorageNode?>(null) }
    var bulkNodesForProperties by remember { mutableStateOf<List<StorageNode>?>(null) }
    var nodeToCopy by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToMove by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToRename by remember { mutableStateOf<StorageNode?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }

    var bulkNodesToCopy by remember { mutableStateOf<List<StorageNode>?>(null) }
    var bulkNodesToMove by remember { mutableStateOf<List<StorageNode>?>(null) }
    var bulkNodesToDelete by remember { mutableStateOf<List<StorageNode>?>(null) }

    var activeOperationTitle by remember { mutableStateOf("Processing Files") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
        viewModel.refresh(hasPermission)
    }

    LaunchedEffect(initialPath) {
        val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
        viewModel.initialize(initialPath, hasPermission)
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler {
        if (selectedNodeIds.isNotEmpty()) {
            viewModel.clearSelection()
        } else {
            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
            val handled = viewModel.navigateUp(hasPermission)
            if (!handled) {
                onNavigateBack()
            }
        }
    }

    val currentTitle = when (val state = uiState) {
        is BrowserUiState.Success -> state.currentTitle
        else -> title
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState is BrowserUiState.Success && selectedNodeIds.isEmpty()) {
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
        topBar = {
            if (selectedNodeIds.isNotEmpty() && uiState is BrowserUiState.Success) {
                val allItems = (uiState as BrowserUiState.Success).items
                val selectedItems = allItems.filter { selectedNodeIds.contains(it.id) }

                ContextualActionBar(
                    selectedCount = selectedNodeIds.size,
                    totalCount = allItems.size,
                    onCloseSelection = { viewModel.clearSelection() },
                    onSelectAllToggle = { viewModel.selectAll(allItems) },
                    onBulkCopy = { bulkNodesToCopy = selectedItems },
                    onBulkMove = { bulkNodesToMove = selectedItems },
                    onBulkDelete = { bulkNodesToDelete = selectedItems },
                    onShare = { FileShareHelper.shareNodes(context, selectedItems) },
                    onRename = { nodeToRename = selectedItems.firstOrNull() },
                    onProperties = {
                        if (selectedItems.size == 1) {
                            selectedNodeForProperties = selectedItems.first()
                        } else {
                            bulkNodesForProperties = selectedItems
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            val handled = viewModel.navigateUp(hasPermission)
                            if (!handled) {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        val isCurrentFavorite = (uiState as? BrowserUiState.Success)?.let { viewModel.isFavorite(it.currentPath) } ?: false
                        IconButton(onClick = { viewModel.toggleCurrentFolderFavorite() }) {
                            Icon(
                                imageVector = if (isCurrentFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (isCurrentFavorite) "Remove Bookmark" else "Bookmark Folder",
                                tint = if (isCurrentFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = {
                            val currentPath = (uiState as? BrowserUiState.Success)?.breadcrumbs?.lastOrNull()?.path ?: initialPath
                            onNavigateToSearch(currentPath)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                        IconButton(onClick = { showSortBottomSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort"
                            )
                        }
                        IconButton(onClick = { viewModel.cycleViewMode() }) {
                            val icon = when (viewMode) {
                                ViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                ViewMode.COMPACT -> Icons.Default.DensityMedium
                                ViewMode.GRID -> Icons.Default.GridView
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "View mode: ${viewMode.displayName}"
                            )
                        }
                        IconButton(onClick = {
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.refresh(hasPermission)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
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
            when (val state = uiState) {
                is BrowserUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is BrowserUiState.PermissionRequired -> {
                    PermissionDeniedView(
                        reason = state.reason,
                        onGrantPermission = {
                            val intent = StoragePermissionManager.createManageAllFilesIntent(context)
                            permissionLauncher.launch(intent)
                        }
                    )
                }

                is BrowserUiState.Error -> {
                    ErrorView(
                        message = state.message,
                        onRetry = {
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.refresh(hasPermission)
                        }
                    )
                }

                is BrowserUiState.Success -> {
                    BreadcrumbBar(
                        breadcrumbs = state.breadcrumbs,
                        onBreadcrumbClick = { item ->
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.navigateToBreadcrumb(item, hasPermission)
                        }
                    )

                    if (state.items.isEmpty()) {
                        EmptyFolderView()
                    } else {
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
                                            onClick = {
                                                if (node.isDirectory) {
                                                    val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                                    viewModel.navigateInto(node, hasPermission)
                                                }
                                            },
                                            onToggleSelection = {
                                                viewModel.toggleSelection(node.id)
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
                                            isFavorite = viewModel.isFavorite(node.path),
                                            onClick = {
                                                if (node.isDirectory) {
                                                    val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                                    viewModel.navigateInto(node, hasPermission)
                                                }
                                            },
                                            onToggleSelection = {
                                                viewModel.toggleSelection(node.id)
                                            },
                                            onToggleFavorite = {
                                                viewModel.toggleFavorite(node)
                                            },
                                            onCopyClick = {
                                                nodeToCopy = node
                                            },
                                            onMoveClick = {
                                                nodeToMove = node
                                            },
                                            onRenameClick = {
                                                nodeToRename = node
                                            },
                                            onDeleteClick = {
                                                nodeToDelete = node
                                            },
                                            onShareClick = {
                                                FileShareHelper.shareNodes(context, listOf(node))
                                            },
                                            onInfoClick = {
                                                selectedNodeForProperties = node
                                            }
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
                                            isFavorite = viewModel.isFavorite(node.path),
                                            onClick = {
                                                if (node.isDirectory) {
                                                    val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                                    viewModel.navigateInto(node, hasPermission)
                                                }
                                            },
                                            onToggleSelection = {
                                                viewModel.toggleSelection(node.id)
                                            },
                                            onToggleFavorite = {
                                                viewModel.toggleFavorite(node)
                                            },
                                            onCopyClick = {
                                                nodeToCopy = node
                                            },
                                            onMoveClick = {
                                                nodeToMove = node
                                            },
                                            onRenameClick = {
                                                nodeToRename = node
                                            },
                                            onDeleteClick = {
                                                nodeToDelete = node
                                            },
                                            onShareClick = {
                                                FileShareHelper.shareNodes(context, listOf(node))
                                            },
                                            onInfoClick = {
                                                selectedNodeForProperties = node
                                            }
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

    // Single Copy Destination Picker
    val targetToCopy = nodeToCopy
    if (targetToCopy != null) {
        DestinationPickerDialog(
            storageProvider = viewModel.storageProvider,
            title = "Copy to...",
            confirmButtonText = "Copy Here",
            excludedPaths = if (targetToCopy.isDirectory) setOf(targetToCopy.path) else emptySet(),
            onDestinationSelected = { destination ->
                nodeToCopy = null
                activeOperationTitle = "Copying Files"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.startCopy(
                    sources = listOf(targetToCopy),
                    destination = destination,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { nodeToCopy = null }
        )
    }

    // Bulk Copy Destination Picker
    val targetBulkCopy = bulkNodesToCopy
    if (targetBulkCopy != null) {
        DestinationPickerDialog(
            storageProvider = viewModel.storageProvider,
            title = "Copy ${targetBulkCopy.size} Items to...",
            confirmButtonText = "Copy Here",
            excludedPaths = targetBulkCopy.filter { it.isDirectory }.map { it.path }.toSet(),
            onDestinationSelected = { destination ->
                bulkNodesToCopy = null
                activeOperationTitle = "Copying ${targetBulkCopy.size} Items"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.startCopy(
                    sources = targetBulkCopy,
                    destination = destination,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { bulkNodesToCopy = null }
        )
    }

    // Single Move Destination Picker
    val targetToMove = nodeToMove
    if (targetToMove != null) {
        DestinationPickerDialog(
            storageProvider = viewModel.storageProvider,
            title = "Move to...",
            confirmButtonText = "Move Here",
            excludedPaths = if (targetToMove.isDirectory) setOf(targetToMove.path) else emptySet(),
            onDestinationSelected = { destination ->
                nodeToMove = null
                activeOperationTitle = "Moving Files"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.startMove(
                    sources = listOf(targetToMove),
                    destination = destination,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { nodeToMove = null }
        )
    }

    // Bulk Move Destination Picker
    val targetBulkMove = bulkNodesToMove
    if (targetBulkMove != null) {
        DestinationPickerDialog(
            storageProvider = viewModel.storageProvider,
            title = "Move ${targetBulkMove.size} Items to...",
            confirmButtonText = "Move Here",
            excludedPaths = targetBulkMove.filter { it.isDirectory }.map { it.path }.toSet(),
            onDestinationSelected = { destination ->
                bulkNodesToMove = null
                activeOperationTitle = "Moving ${targetBulkMove.size} Items"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.startMove(
                    sources = targetBulkMove,
                    destination = destination,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { bulkNodesToMove = null }
        )
    }

    // Rename Dialog
    val targetToRename = nodeToRename
    if (targetToRename != null) {
        RenameDialog(
            node = targetToRename,
            onConfirmRename = { newName ->
                nodeToRename = null
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.rename(
                    target = targetToRename,
                    newName = newName,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { nodeToRename = null }
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        val currentPath = (uiState as? BrowserUiState.Success)?.breadcrumbs?.lastOrNull()?.path
            ?: initialPath
            ?: viewModel.storageProvider.getRootNode().path

        CreateFolderDialog(
            onConfirmCreate = { folderName ->
                showCreateFolderDialog = false
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.createDirectory(
                    parentPath = currentPath,
                    dirName = folderName,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    // Single Delete Confirmation Dialog
    val targetToDelete = nodeToDelete
    if (targetToDelete != null) {
        DeleteConfirmationDialog(
            node = targetToDelete,
            onConfirmDelete = {
                nodeToDelete = null
                activeOperationTitle = "Deleting Files"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.startDelete(
                    targets = listOf(targetToDelete),
                    hasPermission = hasPermission
                )
            },
            onDismiss = { nodeToDelete = null }
        )
    }

    // Bulk Delete Confirmation Dialog
    val targetBulkDelete = bulkNodesToDelete
    if (targetBulkDelete != null) {
        BulkDeleteConfirmationDialog(
            selectedNodes = targetBulkDelete,
            onConfirmDelete = {
                bulkNodesToDelete = null
                activeOperationTitle = "Deleting ${targetBulkDelete.size} Items"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.startDelete(
                    targets = targetBulkDelete,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { bulkNodesToDelete = null }
        )
    }

    // Active Operation Progress Dialog
    val currentProgress = activeProgress
    if (currentProgress != null) {
        OperationProgressDialog(
            progress = currentProgress,
            title = activeOperationTitle,
            onCancel = { viewModel.cancelActiveOperation() }
        )
    }

    // Properties Bottom Sheet (Single Node)
    val inspectedNode = selectedNodeForProperties
    if (inspectedNode != null) {
        FilePropertiesSheet(
            node = inspectedNode,
            onDismiss = { selectedNodeForProperties = null }
        )
    }

    // Bulk Properties Bottom Sheet (Multiple Nodes)
    val bulkInspectedNodes = bulkNodesForProperties
    if (bulkInspectedNodes != null) {
        BulkPropertiesSheet(
            nodes = bulkInspectedNodes,
            onDismiss = { bulkNodesForProperties = null }
        )
    }

    // Sort Bottom Sheet
    if (showSortBottomSheet) {
        SortBottomSheet(
            currentOrder = sortOrder,
            onSortOrderChange = { newOrder ->
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.setSortOrder(newOrder, hasPermission)
            },
            showHiddenFiles = showHiddenFiles,
            onShowHiddenFilesChange = {
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.toggleShowHiddenFiles(hasPermission)
            },
            onDismiss = { showSortBottomSheet = false }
        )
    }
}

@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<BreadcrumbItem>,
    onBreadcrumbClick: (BreadcrumbItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(breadcrumbs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            breadcrumbs.forEachIndexed { index, item ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !item.isCurrent) { onBreadcrumbClick(item) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageNodeItem(
    node: StorageNode,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (node.name.startsWith(".")) 0.65f else 1.0f)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        onClick()
                    }
                },
                onLongClick = onToggleSelection
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (node.isDirectory) FolderYellow.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (node.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val metadataText = if (node.isDirectory) {
                    FileFormatter.formatDateTime(node.lastModified)
                } else {
                    "${FileFormatter.formatFileSize(node.size)} • ${FileFormatter.formatDateTime(node.lastModified)}"
                }

                Text(
                    text = metadataText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy to...") },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onCopyClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to...") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onMoveClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
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
                                text = { Text("Share") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onShareClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
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
                            text = { Text("Properties") },
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

@Composable
private fun EmptyFolderView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                text = "This folder is empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No files or subdirectories found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionDeniedView(
    reason: String,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Storage Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onGrantPermission,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant All Files Access")
                }
            }
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Error Loading Directory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Retry")
            }
        }
    }
}
