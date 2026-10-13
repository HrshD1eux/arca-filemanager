package com.Hrshd1eux.filemanager.feature.browser

import java.io.File
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import com.Hrshd1eux.filemanager.feature.browser.components.BrowserTabBar
import com.Hrshd1eux.filemanager.feature.browser.components.DualPaneBrowserView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.feature.archive.components.ArchiveTestDialog
import com.Hrshd1eux.filemanager.feature.archive.components.CompressDialog
import com.Hrshd1eux.filemanager.feature.checksum.components.ChecksumDialog
import com.Hrshd1eux.filemanager.feature.archive.components.ExtractOptionsBottomSheet
import com.Hrshd1eux.filemanager.feature.operations.components.OperationsQueueSheet
import com.Hrshd1eux.filemanager.feature.browser.components.BulkDeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.ContextualActionBar
import com.Hrshd1eux.filemanager.feature.browser.components.CreateFolderDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.browser.components.RenameDialog
import com.Hrshd1eux.filemanager.core.actions.QuickActionType
import com.Hrshd1eux.filemanager.feature.archive.components.ArchiveAddItemsDialog
import com.Hrshd1eux.filemanager.feature.browser.components.EncryptChoiceDialog
import com.Hrshd1eux.filemanager.feature.browser.components.QuickActionBottomSheet
import com.Hrshd1eux.filemanager.feature.browser.components.SelectionBottomBar
import com.Hrshd1eux.filemanager.feature.browser.components.StagedMoveFloatingBar
import com.Hrshd1eux.filemanager.feature.browser.components.StagedTransferFloatingBar
import com.Hrshd1eux.filemanager.feature.properties.BulkPropertiesSheet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropManager
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropPayload

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DensityMedium
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.feature.browser.components.SortBottomSheet
import com.Hrshd1eux.filemanager.feature.browser.components.StorageNodeCompactItem
import com.Hrshd1eux.filemanager.feature.browser.components.StorageNodeGridItem
import com.Hrshd1eux.filemanager.feature.browser.components.StorageNodeItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    initialPath: String?,
    title: String,
    onNavigateBack: () -> Unit,
    onNavigateToSearch: (rootPath: String?) -> Unit = {},
    onNavigateToArchiveViewer: (archivePath: String, title: String) -> Unit = { _, _ -> },
    onNavigateToImageViewer: (imagePath: String, categoryName: String?, directoryPath: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: FileBrowserViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeProgress by viewModel.activeProgress.collectAsStateWithLifecycle()
    val activeOperation by viewModel.activeOperation.collectAsStateWithLifecycle()
    val waitingQueue by viewModel.waitingQueue.collectAsStateWithLifecycle()
    val completedHistory by viewModel.completedHistory.collectAsStateWithLifecycle()
    val testReport by viewModel.testReport.collectAsStateWithLifecycle()
    val isTestingArchive by viewModel.isTestingArchive.collectAsStateWithLifecycle()
    val testProgress by viewModel.testProgress.collectAsStateWithLifecycle()
    val testingArchiveName by viewModel.testingArchiveName.collectAsStateWithLifecycle()
    val selectedNodeIds by viewModel.selectedNodeIds.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsStateWithLifecycle()
    val isDualPaneMode by viewModel.isDualPaneMode.collectAsStateWithLifecycle()
    val isTabsMode by viewModel.isTabsMode.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val stagedTransfer by viewModel.stagedTransfer.collectAsStateWithLifecycle()
    val stagedNodesToMove by viewModel.stagedNodesToMove.collectAsStateWithLifecycle()
    val lastMoveDestination by viewModel.lastDestinationRepository.lastMoveDestination.collectAsStateWithLifecycle()
    val enabledArchiveFormats by viewModel.archivePreferencesRepository.enabledFormats.collectAsStateWithLifecycle()
    val defaultArchiveFormat by viewModel.archivePreferencesRepository.defaultFormat.collectAsStateWithLifecycle()
    val defaultCompressionLevel by viewModel.archivePreferencesRepository.defaultLevel.collectAsStateWithLifecycle()
    val confirmDelete by viewModel.appSettingsRepository.confirmDelete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showOperationsQueueSheet by remember { mutableStateOf(false) }

    var selectedNodeForProperties by remember { mutableStateOf<StorageNode?>(null) }
    var bulkNodesForProperties by remember { mutableStateOf<List<StorageNode>?>(null) }
    var nodeToCopy by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToMove by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToRename by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToCompress by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToExtract by remember { mutableStateOf<StorageNode?>(null) }
    var nodeForCustomExtract by remember { mutableStateOf<StorageNode?>(null) }
    var customExtractResolution by remember { mutableStateOf(ConflictResolution.RENAME_AUTO) }
    var customExtractVerify by remember { mutableStateOf(false) }
    var showCustomExtractPicker by remember { mutableStateOf(false) }
    var nodeForChecksums by remember { mutableStateOf<StorageNode?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }

    var quickActionNode by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToEncrypt by remember { mutableStateOf<StorageNode?>(null) }
    var archiveToAddItems by remember { mutableStateOf<StorageNode?>(null) }

    var bulkNodesToCopy by remember { mutableStateOf<List<StorageNode>?>(null) }
    var bulkNodesToMove by remember { mutableStateOf<List<StorageNode>?>(null) }
    var bulkNodesToDelete by remember { mutableStateOf<List<StorageNode>?>(null) }
    var bulkNodesToCompress by remember { mutableStateOf<List<StorageNode>?>(null) }
    var bulkNodesToEncrypt by remember { mutableStateOf<List<StorageNode>?>(null) }

    var activeOperationTitle by remember { mutableStateOf("Processing Files") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
        viewModel.refresh(hasPermission)
    }

    LaunchedEffect(initialPath) {
        val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
        viewModel.initialize(initialPath, hasPermission, context)
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
                if (stagedNodesToMove != null) {
                    viewModel.clearStagedMove()
                } else if (isDualPaneMode) {
                    viewModel.toggleDualPaneMode()
                } else if (isTabsMode && tabs.size > 1) {
                    val activeTab = tabs.find { it.id == activeTabId }
                    if (activeTab != null && activeTab.isClosable) {
                        viewModel.closeTab(activeTab.id, hasPermission)
                    } else {
                        onNavigateBack()
                    }
                } else {
                    onNavigateBack()
                }
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
            if (!isDualPaneMode && uiState is BrowserUiState.Success && selectedNodeIds.isEmpty()) {
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
            if (!isDualPaneMode && selectedNodeIds.isNotEmpty() && uiState is BrowserUiState.Success) {
                val allItems = (uiState as? BrowserUiState.Success)?.items ?: emptyList()
                val selectedItems = allItems.filter { selectedNodeIds.contains(it.id) }

                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    SelectionBottomBar(
                        selectedCount = selectedNodeIds.size,
                        lastMoveDestination = lastMoveDestination?.takeIf { dest ->
                            selectedItems.none { it.path == dest.path }
                        },
                        onMoveToRecent = { target ->
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.startMove(
                                sources = selectedItems,
                                destination = target,
                                hasPermission = hasPermission
                            )
                        },
                        onMove = { bulkNodesToMove = selectedItems },
                        onCopy = { bulkNodesToCopy = selectedItems },
                        onCompress = { bulkNodesToCompress = selectedItems },
                        onDelete = {
                            if (confirmDelete) {
                                bulkNodesToDelete = selectedItems
                            } else {
                                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                activeOperationTitle = "Deleting ${selectedItems.size} Items"
                                viewModel.startDelete(
                                    targets = selectedItems,
                                    hasPermission = hasPermission
                                )
                            }
                        },
                        onEncrypt = {
                            if (selectedItems.size == 1) {
                                nodeToEncrypt = selectedItems.first()
                            } else {
                                bulkNodesToEncrypt = selectedItems
                            }
                        },
                        onShare = { FileIntentDispatcher.shareMultiple(context, selectedItems) },
                        onRename = { nodeToRename = selectedItems.firstOrNull() },
                        onProperties = {
                            if (selectedItems.size == 1) {
                                selectedNodeForProperties = selectedItems.first()
                            } else {
                                bulkNodesForProperties = selectedItems
                            }
                        }
                    )
                }
            } else if (!isDualPaneMode && stagedTransfer != null && uiState is BrowserUiState.Success) {
                val successState = uiState as BrowserUiState.Success
                val currentFolder = StorageNode(
                    id = successState.currentPath,
                    name = successState.currentTitle,
                    path = successState.currentPath,
                    isDirectory = true
                )
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    StagedTransferFloatingBar(
                        transfer = stagedTransfer!!,
                        currentFolder = currentFolder,
                        onCommitTransfer = {
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.commitStagedTransfer(
                                destination = currentFolder,
                                hasPermission = hasPermission
                            )
                        },
                        onCancel = {
                            viewModel.clearStagedTransfer()
                        }
                    )
                }
            }
        },
        topBar = {
            if (selectedNodeIds.isNotEmpty() && uiState is BrowserUiState.Success) {
                val allItems = (uiState as BrowserUiState.Success).items

                ContextualActionBar(
                    selectedCount = selectedNodeIds.size,
                    totalCount = allItems.size,
                    onCloseSelection = { viewModel.clearSelection() },
                    onSelectAllToggle = { viewModel.selectAll(allItems) }
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
                        val pendingCount = (if (activeOperation != null) 1 else 0) + waitingQueue.size
                        if (pendingCount > 0) {
                            IconButton(onClick = { showOperationsQueueSheet = true }) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text("$pendingCount") }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Active Operations ($pendingCount)",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

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
                        IconButton(onClick = { viewModel.toggleDualPaneMode() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = if (isDualPaneMode) "Exit Dual-pane mode" else "Dual-pane mode",
                                tint = if (isDualPaneMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = { viewModel.toggleTabsMode(context) }) {
                            Icon(
                                imageVector = Icons.Default.Tab,
                                contentDescription = if (isTabsMode) "Exit Tabs mode" else "Tabs mode",
                                tint = if (isTabsMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
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
        if (isDualPaneMode) {
            val rightViewModel: FileBrowserViewModel = viewModel(key = "right_pane_browser_vm")
            DualPaneBrowserView(
                leftViewModel = viewModel,
                rightViewModel = rightViewModel,
                onNavigateToArchiveViewer = onNavigateToArchiveViewer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTabsMode && tabs.isNotEmpty()) {
                    BrowserTabBar(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        onTabSelected = { tab ->
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.selectTab(tab.id, hasPermission)
                        },
                        onTabClosed = { tab ->
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.closeTab(tab.id, hasPermission)
                        },
                        onAddTab = {
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            val currentPath = (uiState as? BrowserUiState.Success)?.currentPath ?: viewModel.storageProvider.getRootNode().path
                            val currentName = (uiState as? BrowserUiState.Success)?.currentTitle ?: "Tab"
                            viewModel.addTab(currentPath, currentName, hasPermission = hasPermission)
                        }
                    )
                }

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
                        val allItems = state.items
                        val selectedItems = allItems.filter { selectedNodeIds.contains(it.id) }
                        val onDropIntoFolder: (StorageNode, DragDropPayload) -> Unit = { targetFolder, payload ->
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.handleDroppedPayload(
                                payload = payload,
                                destinationFolder = targetFolder,
                                hasPermission = hasPermission,
                                context = context
                            )
                        }

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
                                            onDropIntoFolder = onDropIntoFolder,
                                            onClick = {
                                                if (node.isDirectory) {
                                                    val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                                    viewModel.navigateInto(node, hasPermission)
                                                } else if (node.extension.equals("zip", ignoreCase = true)) {
                                                    onNavigateToArchiveViewer(node.path, node.name)
                                                } else {
                                                    FileIntentDispatcher.view(context, node)
                                                }
                                            },
                                            onToggleSelection = {
                                                viewModel.toggleSelection(node.id)
                                            },
                                            onQuickActionClick = {
                                                quickActionNode = node
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
                                            allSelectedNodes = selectedItems,
                                            onDropIntoFolder = onDropIntoFolder,
                                            onClick = {
                                                if (node.isDirectory) {
                                                    val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                                    viewModel.navigateInto(node, hasPermission)
                                                } else if (FileTypeResolver.isImage(node.name)) {
                                                    onNavigateToImageViewer(node.path, null, File(node.path).parent)
                                                } else if (FileTypeResolver.canPreviewArchive(node.name)) {
                                                    onNavigateToArchiveViewer(node.path, node.name)
                                                } else {
                                                    FileIntentDispatcher.view(context, node)
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
                                            onCompressClick = {
                                                nodeToCompress = node
                                            },
                                            onExtractClick = {
                                                nodeToExtract = node
                                            },
                                            onTestArchiveClick = {
                                                viewModel.testArchive(node)
                                            },
                                            onRenameClick = {
                                                nodeToRename = node
                                            },
                                            onDeleteClick = {
                                                if (confirmDelete) {
                                                    nodeToDelete = node
                                                } else {
                                                    val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                                    activeOperationTitle = "Deleting ${node.name}"
                                                    viewModel.startDelete(targets = listOf(node), hasPermission = hasPermission)
                                                }
                                            },
                                            onShareClick = {
                                                FileIntentDispatcher.share(context, node)
                                            },
                                            onOpenWithClick = {
                                                FileIntentDispatcher.openWith(context, node)
                                            },
                                            onSendToClick = {
                                                FileIntentDispatcher.sendTo(context, node)
                                            },
                                            onChecksumClick = {
                                                nodeForChecksums = node
                                            },
                                            onInfoClick = {
                                                selectedNodeForProperties = node
                                            },
                                            onQuickActionClick = {
                                                quickActionNode = node
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
                                            allSelectedNodes = selectedItems,
                                            onDropIntoFolder = onDropIntoFolder,
                                            onClick = {
                                                if (node.isDirectory) {
                                                    val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                                    viewModel.navigateInto(node, hasPermission)
                                                } else if (FileTypeResolver.isImage(node.name)) {
                                                    onNavigateToImageViewer(node.path, null, File(node.path).parent)
                                                } else if (FileTypeResolver.canPreviewArchive(node.name)) {
                                                    onNavigateToArchiveViewer(node.path, node.name)
                                                } else {
                                                    FileIntentDispatcher.view(context, node)
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
                                            onCompressClick = {
                                                nodeToCompress = node
                                            },
                                            onExtractClick = {
                                                nodeToExtract = node
                                            },
                                            onTestArchiveClick = {
                                                viewModel.testArchive(node)
                                            },
                                            onRenameClick = {
                                                nodeToRename = node
                                            },
                                            onDeleteClick = {
                                                if (confirmDelete) {
                                                    nodeToDelete = node
                                                } else {
                                                    val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                                                    activeOperationTitle = "Deleting ${node.name}"
                                                    viewModel.startDelete(targets = listOf(node), hasPermission = hasPermission)
                                                }
                                            },
                                            onShareClick = {
                                                FileIntentDispatcher.share(context, node)
                                            },
                                            onOpenWithClick = {
                                                FileIntentDispatcher.openWith(context, node)
                                            },
                                            onSendToClick = {
                                                FileIntentDispatcher.sendTo(context, node)
                                            },
                                            onChecksumClick = {
                                                nodeForChecksums = node
                                            },
                                            onInfoClick = {
                                                selectedNodeForProperties = node
                                            },
                                            onQuickActionClick = {
                                                quickActionNode = node
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
}

    // Single Copy Destination Picker
    val targetToCopy = nodeToCopy
    if (targetToCopy != null) {
        DestinationPickerDialog(
            storageProvider = viewModel.storageProvider,
            title = "Copy to...",
            confirmButtonText = "Copy Here",
            excludedPaths = if (targetToCopy.isDirectory) setOf(targetToCopy.path) else emptySet(),
            onStageForMove = {
                val toStage = listOf(targetToCopy)
                nodeToCopy = null
                viewModel.stageNodesForCopy(toStage)
            },
            onDestinationSelected = { destination, verifyIntegrity ->
                nodeToCopy = null
                activeOperationTitle = "Copying Files"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.startCopy(
                    sources = listOf(targetToCopy),
                    destination = destination,
                    verifyIntegrity = verifyIntegrity,
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
            favoritesRepository = viewModel.favoritesRepository,
            recentsRepository = viewModel.recentsRepository,
            onStageForMove = {
                val toStage = targetBulkCopy
                bulkNodesToCopy = null
                viewModel.stageNodesForCopy(toStage)
            },
            onDestinationSelected = { destination, verifyIntegrity ->
                bulkNodesToCopy = null
                activeOperationTitle = "Copying ${targetBulkCopy.size} Items"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.startCopy(
                    sources = targetBulkCopy,
                    destination = destination,
                    verifyIntegrity = verifyIntegrity,
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
            favoritesRepository = viewModel.favoritesRepository,
            recentsRepository = viewModel.recentsRepository,
            lastMoveDestination = lastMoveDestination?.takeIf { it.path != targetToMove.path },
            onStageForMove = {
                val toStage = listOf(targetToMove)
                nodeToMove = null
                viewModel.stageNodesForMove(toStage)
            },
            onDestinationSelected = { destination, _ ->
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
            favoritesRepository = viewModel.favoritesRepository,
            recentsRepository = viewModel.recentsRepository,
            lastMoveDestination = lastMoveDestination?.takeIf { dest ->
                targetBulkMove.none { it.path == dest.path }
            },
            onStageForMove = {
                val toStage = targetBulkMove
                bulkNodesToMove = null
                viewModel.stageNodesForMove(toStage)
            },
            onDestinationSelected = { destination, _ ->
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

    // Single Compress Dialog
    val targetToCompress = nodeToCompress
    if (targetToCompress != null) {
        CompressDialog(
            sources = listOf(targetToCompress),
            defaultName = "${targetToCompress.name}${defaultArchiveFormat.defaultExtension}",
            enabledFormats = enabledArchiveFormats,
            defaultFormat = defaultArchiveFormat,
            defaultLevel = defaultCompressionLevel,
            onConfirmCreate = { profile, archiveName, password ->
                nodeToCompress = null
                activeOperationTitle = "Compressing to $archiveName"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.compressFiles(
                    sources = listOf(targetToCompress),
                    profile = profile,
                    archiveName = archiveName,
                    password = password,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { nodeToCompress = null }
        )
    }

    // Bulk Compress Dialog
    val targetBulkCompress = bulkNodesToCompress
    if (targetBulkCompress != null) {
        val defaultExt = defaultArchiveFormat.defaultExtension
        val defaultZipName = if (targetBulkCompress.size == 1) {
            "${targetBulkCompress.first().name}$defaultExt"
        } else {
            "archive$defaultExt"
        }
        CompressDialog(
            sources = targetBulkCompress,
            defaultName = defaultZipName,
            enabledFormats = enabledArchiveFormats,
            defaultFormat = defaultArchiveFormat,
            defaultLevel = defaultCompressionLevel,
            onConfirmCreate = { profile, archiveName, password ->
                bulkNodesToCompress = null
                activeOperationTitle = "Compressing ${targetBulkCompress.size} Items"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                viewModel.compressFiles(
                    sources = targetBulkCompress,
                    profile = profile,
                    archiveName = archiveName,
                    password = password,
                    hasPermission = hasPermission
                )
            },
            onDismiss = { bulkNodesToCompress = null }
        )
    }

    // Extract Options Bottom Sheet
    val targetToExtract = nodeToExtract
    if (targetToExtract != null) {
        ExtractOptionsBottomSheet(
            archiveName = targetToExtract.name,
            isSelective = false,
            onExtractToSubfolder = { resolution, verifyIntegrity ->
                nodeToExtract = null
                activeOperationTitle = "Extracting Archive"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                val parentDir = File(targetToExtract.path).parentFile ?: File("/storage/emulated/0")
                val targetDir = File(parentDir, File(targetToExtract.path).nameWithoutExtension)
                viewModel.extractArchive(
                    archiveNode = targetToExtract,
                    destinationDir = targetDir,
                    conflictResolution = resolution,
                    verifyIntegrity = verifyIntegrity,
                    hasPermission = hasPermission
                )
            },
            onExtractHere = { resolution, verifyIntegrity ->
                nodeToExtract = null
                activeOperationTitle = "Extracting Archive"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                val parentDir = File(targetToExtract.path).parentFile ?: File("/storage/emulated/0")
                viewModel.extractArchive(
                    archiveNode = targetToExtract,
                    destinationDir = parentDir,
                    conflictResolution = resolution,
                    verifyIntegrity = verifyIntegrity,
                    hasPermission = hasPermission
                )
            },
            onExtractToCustom = { resolution, verifyIntegrity ->
                val node = targetToExtract
                nodeToExtract = null
                nodeForCustomExtract = node
                customExtractResolution = resolution
                customExtractVerify = verifyIntegrity
                showCustomExtractPicker = true
            },
            onDismiss = { nodeToExtract = null }
        )
    }

    // Custom Extract Destination Picker Dialog
    if (showCustomExtractPicker && nodeForCustomExtract != null) {
        val currentExtractTarget = nodeForCustomExtract!!
        DestinationPickerDialog(
            storageProvider = viewModel.storageProvider,
            title = "Extract to...",
            confirmButtonText = "Extract Here",
            favoritesRepository = viewModel.favoritesRepository,
            recentsRepository = viewModel.recentsRepository,
            onDestinationSelected = { dest, _ ->
                showCustomExtractPicker = false
                activeOperationTitle = "Extracting Archive"
                val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                val targetDir = File(dest.path, File(currentExtractTarget.path).nameWithoutExtension)
                viewModel.extractArchive(
                    archiveNode = currentExtractTarget,
                    destinationDir = targetDir,
                    conflictResolution = customExtractResolution,
                    verifyIntegrity = customExtractVerify,
                    hasPermission = hasPermission
                )
                nodeForCustomExtract = null
            },
            onDismiss = {
                showCustomExtractPicker = false
                nodeForCustomExtract = null
            }
        )
    }

    // Operations Queue Bottom Sheet
    if (showOperationsQueueSheet) {
        OperationsQueueSheet(
            activeOperation = activeOperation,
            waitingQueue = waitingQueue,
            completedHistory = completedHistory,
            onCancelOperation = { id -> viewModel.cancelOperation(id) },
            onCancelAll = { viewModel.cancelAllOperations() },
            onClearCompleted = { viewModel.clearCompletedOperations() },
            onDismiss = { showOperationsQueueSheet = false }
        )
    }

    // Archive Integrity Test Dialog
    if (isTestingArchive || testReport != null) {
        ArchiveTestDialog(
            archiveName = testingArchiveName,
            isTesting = isTestingArchive,
            progress = testProgress,
            report = testReport,
            onCancel = { viewModel.cancelArchiveTest() },
            onDismiss = { viewModel.dismissArchiveTest() }
        )
    }

    // Checksum Dialog
    val checksumTargetNode = nodeForChecksums
    if (checksumTargetNode != null) {
        ChecksumDialog(
            node = checksumTargetNode,
            onDismiss = { nodeForChecksums = null }
        )
    }

    // Active Operation Progress Dialog (Fallback popup if queue sheet closed)
    val currentProgress = activeProgress
    if (currentProgress != null && !showOperationsQueueSheet) {
        OperationProgressDialog(
            progress = currentProgress,
            title = activeOperation?.title ?: activeOperationTitle,
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

    // Quick Action System Bottom Sheet
    quickActionNode?.let { node ->
        QuickActionBottomSheet(
            node = node,
            onActionClick = { action ->
                quickActionNode = null
                when (action) {
                    QuickActionType.OPEN, QuickActionType.OPEN_FOLDER -> {
                        if (node.isDirectory) {
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.navigateInto(node, hasPermission)
                        } else {
                            FileIntentDispatcher.view(context, node)
                        }
                    }
                    QuickActionType.OPEN_ARCHIVE -> {
                        onNavigateToArchiveViewer(node.path, node.name)
                    }
                    QuickActionType.MOVE -> nodeToMove = node
                    QuickActionType.COPY -> nodeToCopy = node
                    QuickActionType.COMPRESS -> nodeToCompress = node
                    QuickActionType.ENCRYPT -> nodeToEncrypt = node
                    QuickActionType.SHARE -> FileIntentDispatcher.share(context, node)
                    QuickActionType.PROPERTIES -> selectedNodeForProperties = node
                    QuickActionType.EXTRACT -> nodeToExtract = node
                    QuickActionType.TEST -> viewModel.testArchive(node)
                    QuickActionType.ADD -> archiveToAddItems = node
                }
            },
            onDismiss = { quickActionNode = null }
        )
    }

    // Encrypt Choice Dialog
    nodeToEncrypt?.let { node ->
        EncryptChoiceDialog(
            node = node,
            onEncryptToVault = {
                nodeToEncrypt = null
                viewModel.encryptIntoVault(context, File(node.path))
            },
            onEncryptToArchive = {
                nodeToEncrypt = null
                nodeToCompress = node
            },
            onDismiss = { nodeToEncrypt = null }
        )
    }

    // Bulk Encrypt Choice Dialog
    bulkNodesToEncrypt?.let { nodes ->
        EncryptChoiceDialog(
            title = "Encrypt ${nodes.size} items",
            onEncryptToVault = {
                val files = nodes.map { File(it.path) }
                bulkNodesToEncrypt = null
                viewModel.clearSelection()
                viewModel.encryptIntoVault(context, files)
            },
            onEncryptToArchive = {
                bulkNodesToEncrypt = null
                bulkNodesToCompress = nodes
            },
            onDismiss = { bulkNodesToEncrypt = null }
        )
    }

    // Archive Add Items Dialog
    archiveToAddItems?.let { archiveNode ->
        ArchiveAddItemsDialog(
            storageProvider = viewModel.storageProvider,
            onItemsSelected = { files ->
                archiveToAddItems = null
                viewModel.addFilesToArchive(File(archiveNode.path), files)
            },
            onDismiss = { archiveToAddItems = null }
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
