package com.Hrshd1eux.filemanager.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.SelectionHaptics
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.DateFilter
import com.Hrshd1eux.filemanager.core.search.model.FileTypeScope
import com.Hrshd1eux.filemanager.core.search.model.MatchType
import com.Hrshd1eux.filemanager.core.search.model.SearchSortBy
import com.Hrshd1eux.filemanager.core.search.model.SizeFilter
import com.Hrshd1eux.filemanager.core.storage.apk.ApkInstaller
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.core.thumbnail.FileThumbnailView
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import com.Hrshd1eux.filemanager.feature.browser.components.BulkDeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.browser.components.RenameDialog
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioPlaybackState
import com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSearchScreen(
    rootPath: String?,
    initialCategory: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDirectory: (path: String) -> Unit,
    onNavigateToVault: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FileSearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val vaultLockManager = remember { VaultLockManager.getInstance(context) }
    val audioController = remember { AudioPlaybackController.getInstance(context) }
    val audioPlaybackState by audioController.playbackState.collectAsStateWithLifecycle()
    val isAudioActive = audioPlaybackState !is AudioPlaybackState.Idle

    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val sizeFilter by viewModel.sizeFilter.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()
    val matchType by viewModel.matchType.collectAsStateWithLifecycle()
    val matchCase by viewModel.matchCase.collectAsStateWithLifecycle()
    val fileTypeScope by viewModel.fileTypeScope.collectAsStateWithLifecycle()
    val extensionFilter by viewModel.extensionFilter.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val searchEverywhere by viewModel.searchEverywhere.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val selectedNodes by viewModel.selectedNodes.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
    val isSelectionMode = selectedNodes.isNotEmpty()

    var showSortMenu by remember { mutableStateOf(false) }
    var selectedNodeForProperties by remember { mutableStateOf<StorageNode?>(null) }
    var showCopyDestinationPicker by remember { mutableStateOf(false) }
    var showMoveDestinationPicker by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var nodeToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToRename by remember { mutableStateOf<StorageNode?>(null) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(rootPath, initialCategory) {
        viewModel.setRootPath(rootPath)
        if (initialCategory != null) {
            val matchedCategory = runCatching { CategoryFilter.valueOf(initialCategory) }.getOrNull()
            if (matchedCategory != null) {
                viewModel.onCategorySelect(matchedCategory)
            }
        }
        if (initialCategory == null) {
            focusRequester.requestFocus()
        }
    }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedNodes.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close selection"
                            )
                        }
                    },
                    actions = {
                        val allSelected = (uiState as? SearchUiState.Success)?.items?.let { items ->
                            items.isNotEmpty() && selectedNodes.size >= items.size
                        } ?: false

                        // Select All / Deselect All Toggle
                        IconButton(onClick = {
                            SelectionHaptics.playSelectionFeedback(context, view)
                            (uiState as? SearchUiState.Success)?.let {
                                viewModel.toggleSelectAll(it.items)
                            }
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect all" else "Select all"
                            )
                        }

                        // Copy Selected
                        IconButton(onClick = { showCopyDestinationPicker = true }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy selected"
                            )
                        }

                        // Move Selected
                        IconButton(onClick = { showMoveDestinationPicker = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = "Move selected"
                            )
                        }

                        // Share Selected
                        IconButton(onClick = {
                            FileIntentDispatcher.shareMultiple(context, selectedNodes.toList())
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share selected"
                            )
                        }

                        // Delete Selected
                        IconButton(onClick = { showBulkDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected",
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
                        OutlinedTextField(
                            value = query,
                            onValueChange = { newQuery ->
                                if (vaultLockManager.matchesSecretCode(newQuery)) {
                                    viewModel.clearQuery()
                                    onNavigateToVault()
                                } else {
                                    viewModel.onQueryChange(newQuery)
                                }
                            },
                            placeholder = {
                                Text(
                                    text = if (searchEverywhere) "Search Everywhere..." else "Search In Folder...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearQuery() }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
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
                        // Toggle View Mode (List vs Grid)
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (viewMode == SearchViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "Toggle View Mode"
                            )
                        }

                        // Sort By Menu
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
                                SearchSortBy.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.displayName,
                                                fontWeight = if (sortBy == option) FontWeight.Bold else FontWeight.Normal,
                                                color = if (sortBy == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortBy(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
            // Advanced Filter & Match Controls Bar
            if (!isSelectionMode) {
                AdvancedFilterBar(
                    searchEverywhere = searchEverywhere,
                    matchCase = matchCase,
                    matchType = matchType,
                    fileTypeScope = fileTypeScope,
                    selectedExtension = extensionFilter,
                    selectedCategory = category,
                    selectedSize = sizeFilter,
                    selectedDate = dateFilter,
                    onToggleSearchEverywhere = { viewModel.toggleSearchEverywhere() },
                    onToggleMatchCase = { viewModel.toggleMatchCase() },
                    onSelectMatchType = { viewModel.setMatchType(it) },
                    onSelectFileTypeScope = { viewModel.setFileTypeScope(it) },
                    onSelectExtension = { viewModel.setExtensionFilter(it) },
                    onCategorySelect = { viewModel.onCategorySelect(it) },
                    onSizeSelect = { viewModel.onSizeFilterSelect(it) },
                    onDateSelect = { viewModel.onDateFilterSelect(it) }
                )
            }

            // Search Content
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    SearchIdleView(
                        recentSearches = recentSearches,
                        onSelectRecent = { viewModel.onQueryChange(it) },
                        onRemoveRecent = { viewModel.removeRecentSearch(it) },
                        onClearRecent = { viewModel.clearRecentSearches() }
                    )
                }

                is SearchUiState.Searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is SearchUiState.Empty -> {
                    SearchEmptyView(query = query)
                }

                is SearchUiState.Success -> {
                    if (viewMode == SearchViewMode.LIST) {
                        SearchResultsList(
                            items = state.items,
                            selectedNodes = selectedNodes,
                            isSelectionMode = isSelectionMode,
                            onItemClick = { node ->
                                val file = File(node.path)
                                if (node.isDirectory || file.isDirectory) {
                                    onNavigateToDirectory(node.path)
                                } else if (FileTypeResolver.isAudio(node.name)) {
                                    val audioList = state.items.filter { !it.isDirectory && FileTypeResolver.isAudio(it.name) }
                                    audioController.playTrack(node, audioList)
                                } else if (node.name.endsWith(".apk", true) || FileTypeResolver.isPackage(node.name)) {
                                    ApkInstaller.install(context, file)
                                } else {
                                    FileIntentDispatcher.view(context, node)
                                }
                            },
                            onItemLongClick = { node ->
                                if (!isSelectionMode) {
                                    viewModel.startSelection(node)
                                } else {
                                    viewModel.toggleSelection(node)
                                }
                            },
                            onToggleSelection = { node ->
                                viewModel.toggleSelection(node)
                            },
                            onOpenWith = { FileIntentDispatcher.openWith(context, it) },
                            onCopy = {
                                viewModel.startSelection(it)
                                showCopyDestinationPicker = true
                            },
                            onMove = {
                                viewModel.startSelection(it)
                                showMoveDestinationPicker = true
                            },
                            onShare = { FileIntentDispatcher.share(context, it) },
                            onShowInFolder = { onNavigateToDirectory(File(it.path).parent ?: "") },
                            onRename = { nodeToRename = it },
                            onDelete = { nodeToDelete = it },
                            onProperties = { selectedNodeForProperties = it }
                        )
                    } else {
                        SearchResultsGrid(
                            items = state.items,
                            selectedNodes = selectedNodes,
                            isSelectionMode = isSelectionMode,
                            onItemClick = { node ->
                                val file = File(node.path)
                                if (node.isDirectory || file.isDirectory) {
                                    onNavigateToDirectory(node.path)
                                } else if (FileTypeResolver.isAudio(node.name)) {
                                    val audioList = state.items.filter { !it.isDirectory && FileTypeResolver.isAudio(it.name) }
                                    audioController.playTrack(node, audioList)
                                } else if (node.name.endsWith(".apk", true) || FileTypeResolver.isPackage(node.name)) {
                                    ApkInstaller.install(context, file)
                                } else {
                                    FileIntentDispatcher.view(context, node)
                                }
                            },
                            onItemLongClick = { node ->
                                if (!isSelectionMode) {
                                    viewModel.startSelection(node)
                                } else {
                                    viewModel.toggleSelection(node)
                                }
                            },
                            onToggleSelection = { node ->
                                viewModel.toggleSelection(node)
                            }
                        )
                    }
                }
            }

            if (isAudioActive) {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Rename Dialog
    nodeToRename?.let { node ->
        RenameDialog(
            node = node,
            onConfirmRename = { newName ->
                viewModel.renameFile(node, newName)
                nodeToRename = null
            },
            onDismiss = { nodeToRename = null }
        )
    }

    // Single Delete Dialog
    nodeToDelete?.let { node ->
        DeleteConfirmationDialog(
            node = node,
            onConfirmDelete = { permanent ->
                viewModel.deleteSingle(node, permanent = permanent)
                nodeToDelete = null
            },
            onDismiss = { nodeToDelete = null }
        )
    }

    // Bulk Delete Dialog
    if (showBulkDeleteDialog) {
        BulkDeleteConfirmationDialog(
            selectedNodes = selectedNodes.toList(),
            onConfirmDelete = { permanent ->
                viewModel.deleteSelected(permanent = permanent)
                showBulkDeleteDialog = false
            },
            onDismiss = { showBulkDeleteDialog = false }
        )
    }

    // Copy Destination Picker
    if (showCopyDestinationPicker) {
        DestinationPickerDialog(
            storageProvider = viewModel.storageProvider,
            title = "Copy to",
            confirmButtonText = "Copy Here",
            onDestinationSelected = { dest, integrity ->
                viewModel.copySelected(dest, integrity)
                showCopyDestinationPicker = false
            },
            onDismiss = { showCopyDestinationPicker = false }
        )
    }

    // Move Destination Picker
    if (showMoveDestinationPicker) {
        DestinationPickerDialog(
            storageProvider = viewModel.storageProvider,
            title = "Move to",
            confirmButtonText = "Move Here",
            onDestinationSelected = { dest, integrity ->
                viewModel.moveSelected(dest, integrity)
                showMoveDestinationPicker = false
            },
            onDismiss = { showMoveDestinationPicker = false }
        )
    }

    // Active Operation Progress Dialog
    if (isOperating) {
        operationProgress?.let { progress ->
            OperationProgressDialog(
                progress = progress,
                onCancel = { /* background cancellable */ }
            )
        }
    }

    // File Properties Bottom Sheet
    selectedNodeForProperties?.let { node ->
        FilePropertiesSheet(
            node = node,
            onDismiss = { selectedNodeForProperties = null }
        )
    }
}

@Composable
private fun AdvancedFilterBar(
    searchEverywhere: Boolean,
    matchCase: Boolean,
    matchType: MatchType,
    fileTypeScope: FileTypeScope,
    selectedExtension: String?,
    selectedCategory: CategoryFilter,
    selectedSize: SizeFilter,
    selectedDate: DateFilter,
    onToggleSearchEverywhere: () -> Unit,
    onToggleMatchCase: () -> Unit,
    onSelectMatchType: (MatchType) -> Unit,
    onSelectFileTypeScope: (FileTypeScope) -> Unit,
    onSelectExtension: (String?) -> Unit,
    onCategorySelect: (CategoryFilter) -> Unit,
    onSizeSelect: (SizeFilter) -> Unit,
    onDateSelect: (DateFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMatchTypeMenu by remember { mutableStateOf(false) }
    var showScopeMenu by remember { mutableStateOf(false) }

    val quickExtensions = listOf("pdf", "apk", "zip", "png", "jpg", "mp4", "docx")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Scope Toggle Chip (Everywhere vs Current Folder)
        FilterChip(
            selected = searchEverywhere,
            onClick = onToggleSearchEverywhere,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text(if (searchEverywhere) "Everywhere" else "This Folder") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Match Case (Aa) Chip
        FilterChip(
            selected = matchCase,
            onClick = onToggleMatchCase,
            label = { Text("Aa (Case)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        )

        // Match Type Dropdown Chip
        Box {
            FilterChip(
                selected = matchType != MatchType.CONTAINS,
                onClick = { showMatchTypeMenu = true },
                label = { Text("Match: ${matchType.displayName}") }
            )
            DropdownMenu(
                expanded = showMatchTypeMenu,
                onDismissRequest = { showMatchTypeMenu = false }
            ) {
                MatchType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = type.displayName,
                                fontWeight = if (matchType == type) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelectMatchType(type)
                            showMatchTypeMenu = false
                        }
                    )
                }
            }
        }

        // File vs Folder Scope Chip
        Box {
            FilterChip(
                selected = fileTypeScope != FileTypeScope.ALL,
                onClick = { showScopeMenu = true },
                label = { Text(fileTypeScope.displayName) }
            )
            DropdownMenu(
                expanded = showScopeMenu,
                onDismissRequest = { showScopeMenu = false }
            ) {
                FileTypeScope.entries.forEach { scope ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = scope.displayName,
                                fontWeight = if (fileTypeScope == scope) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelectFileTypeScope(scope)
                            showScopeMenu = false
                        }
                    )
                }
            }
        }

        // Quick Extension Chips
        quickExtensions.forEach { ext ->
            val isSelected = selectedExtension.equals(ext, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelectExtension(if (isSelected) null else ext) },
                label = { Text(".$ext") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }

        // Category Chips
        CategoryFilter.entries.filter { it != CategoryFilter.ALL }.forEach { cat ->
            val isSelected = selectedCategory == cat
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelect(cat) },
                label = { Text(cat.displayName) }
            )
        }

        // Size Chips
        SizeFilter.entries.filter { it != SizeFilter.ANY }.forEach { size ->
            val isSelected = selectedSize == size
            FilterChip(
                selected = isSelected,
                onClick = { onSizeSelect(size) },
                label = { Text(size.displayName) }
            )
        }

        // Date Chips
        DateFilter.entries.filter { it != DateFilter.ANY }.forEach { date ->
            val isSelected = selectedDate == date
            FilterChip(
                selected = isSelected,
                onClick = { onDateSelect(date) },
                label = { Text(date.displayName) }
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    items: List<StorageNode>,
    selectedNodes: Set<StorageNode>,
    isSelectionMode: Boolean,
    onItemClick: (StorageNode) -> Unit,
    onItemLongClick: (StorageNode) -> Unit,
    onToggleSelection: (StorageNode) -> Unit,
    onOpenWith: (StorageNode) -> Unit,
    onCopy: (StorageNode) -> Unit,
    onMove: (StorageNode) -> Unit,
    onShare: (StorageNode) -> Unit,
    onShowInFolder: (StorageNode) -> Unit,
    onRename: (StorageNode) -> Unit,
    onDelete: (StorageNode) -> Unit,
    onProperties: (StorageNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "${items.size} results found",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.path }) { node ->
                val isSelected = selectedNodes.any { it.path == node.path }
                SearchResultItem(
                    node = node,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelection(node)
                        } else {
                            onItemClick(node)
                        }
                    },
                    onLongClick = { onItemLongClick(node) },
                    onOpenWith = { onOpenWith(node) },
                    onCopy = { onCopy(node) },
                    onMove = { onMove(node) },
                    onShare = { onShare(node) },
                    onShowInFolder = { onShowInFolder(node) },
                    onRename = { onRename(node) },
                    onDelete = { onDelete(node) },
                    onProperties = { onProperties(node) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultsGrid(
    items: List<StorageNode>,
    selectedNodes: Set<StorageNode>,
    isSelectionMode: Boolean,
    onItemClick: (StorageNode) -> Unit,
    onItemLongClick: (StorageNode) -> Unit,
    onToggleSelection: (StorageNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "${items.size} results found",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.path }) { node ->
                val isSelected = selectedNodes.any { it.path == node.path }
                SearchResultGridItem(
                    node = node,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (isSelectionMode) onToggleSelection(node) else onItemClick(node)
                    },
                    onLongClick = { onItemLongClick(node) },
                    onToggleSelection = { onToggleSelection(node) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultItem(
    node: StorageNode,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenWith: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onShowInFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        SelectionHaptics.playSelectionFeedback(context, view)
                    }
                    onClick()
                },
                onLongClick = {
                    SelectionHaptics.playLongPressFeedback(context, view)
                    onLongClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox when in selection mode
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        onClick()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Thumbnail / Icon
            FileThumbnailView(
                node = node,
                categoryColor = if (node.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = File(node.path).parent ?: node.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = if (node.isDirectory) {
                    FileFormatter.formatDateTime(node.lastModified)
                } else {
                    "${FileFormatter.formatFileSize(node.size)} • ${FileFormatter.formatDateTime(node.lastModified)}"
                }

                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }

            // Single Item Options Menu
            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (!node.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Open with") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpenWith()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMove()
                            }
                        )
                        if (!node.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onShare()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Show in folder") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShowInFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Properties") },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onProperties()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultGridItem(
    node: StorageNode,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
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
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center
            ) {
                FileThumbnailView(
                    node = node,
                    categoryColor = if (node.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 36.dp,
                    shape = RoundedCornerShape(8.dp)
                )

                if (isSelectionMode) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelection() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (node.isDirectory) "Folder" else FileFormatter.formatFileSize(node.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchIdleView(
    recentSearches: List<String>,
    onSelectRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Recent Searches",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(onClick = onClearRecent) {
                    Text("Clear")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentSearches.forEach { search ->
                    InputChip(
                        selected = false,
                        onClick = { onSelectRecent(search) },
                        label = { Text(search) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onRemoveRecent(search) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Search Local Files",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type a filename, extension, or use advanced filters above",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SearchEmptyView(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No results found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (query.isNotEmpty()) "No files matching \"$query\"" else "Try adjusting your filters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
