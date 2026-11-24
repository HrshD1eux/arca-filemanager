package com.Hrshd1eux.filemanager.feature.category

import android.content.Context
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioPlaybackState
import com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.Hrshd1eux.filemanager.feature.archive.components.CompressDialog
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.SelectionHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.core.thumbnail.FileThumbnailView
import com.Hrshd1eux.filemanager.feature.browser.components.BulkDeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.browser.components.RenameDialog
import com.Hrshd1eux.filemanager.feature.browser.components.SortBottomSheet
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryName: String,
    rootPath: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToArchiveViewer: (archivePath: String, title: String) -> Unit = { _, _ -> },
    onNavigateToImageViewer: (imagePath: String, categoryName: String?, rootPath: String?) -> Unit = { _, _, _ -> },
    onNavigateToVideoViewer: (videoPath: String, categoryName: String?, rootPath: String?) -> Unit = { _, _, _ -> },
    onNavigateToPreview: (filePath: String, categoryName: String?, rootPath: String?) -> Unit = onNavigateToImageViewer,
    onNavigateToDirectory: (path: String) -> Unit = {},
    onNavigateToInstalledApps: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: CategoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val groupByDate by viewModel.groupByDate.collectAsStateWithLifecycle()
    val selectedNodes by viewModel.selectedNodes.collectAsStateWithLifecycle()
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchFocusRequester = remember { FocusRequester() }

    val audioController = remember { AudioPlaybackController.getInstance(context) }
    val audioPlaybackState by audioController.playbackState.collectAsStateWithLifecycle()
    val isAudioActive = audioPlaybackState !is AudioPlaybackState.Idle

    val isSelectionMode = selectedNodes.isNotEmpty()

    // Handle system back navigation: exit selection mode first, then search, or pop back
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }
    BackHandler(enabled = isSearching && !isSelectionMode) {
        viewModel.closeSearch()
    }

    var showSortSheet by remember { mutableStateOf(false) }
    var apkToInstall by remember { mutableStateOf<File?>(null) }
    var nodeToRename by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var inspectedNode by remember { mutableStateOf<StorageNode?>(null) }
    var showCopyDestinationPicker by remember { mutableStateOf(false) }
    var showMoveDestinationPicker by remember { mutableStateOf(false) }
    var showSelectionOverflowMenu by remember { mutableStateOf(false) }
    var showStandardOverflowMenu by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showBatchPropertiesDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(categoryName, rootPath) {
        viewModel.loadCategory(categoryName, rootPath)
    }

    val categoryColor = getCategoryColor(category)
    val categoryIcon = getCategoryIcon(category)
    val localizedCategoryName = when (category) {
        CategoryFilter.IMAGES -> stringResource(R.string.category_images)
        CategoryFilter.VIDEOS -> stringResource(R.string.category_videos)
        CategoryFilter.AUDIO -> stringResource(R.string.category_audio)
        CategoryFilter.DOCUMENTS -> stringResource(R.string.category_documents)
        CategoryFilter.DOWNLOADS -> stringResource(R.string.category_downloads)
        CategoryFilter.ARCHIVES -> stringResource(R.string.category_archives)
        CategoryFilter.APKS -> stringResource(R.string.category_apks)
        CategoryFilter.OTHER,
        CategoryFilter.ALL -> stringResource(R.string.category_all_files)
        else -> category.displayName
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isSelectionMode) {
                // Contextual Multi-Selection TopAppBar
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.selection_count, selectedNodes.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_clear_selection)
                            )
                        }
                    },
                    actions = {
                        val allSelected = (uiState as? CategoryUiState.Success)?.items?.let { items ->
                            items.isNotEmpty() && selectedNodes.size >= items.size
                        } ?: false
                        // Select All / Deselect All Toggle
                        IconButton(onClick = {
                            SelectionHaptics.playSelectionFeedback(context, view)
                            viewModel.toggleSelectAll()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) stringResource(R.string.action_deselect_all) else stringResource(R.string.action_select_all)
                            )
                        }
                        // Share Selected
                        IconButton(onClick = {
                            FileIntentDispatcher.shareMultiple(context, selectedNodes.toList())
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share)
                            )
                        }
                        // Delete Selected
                        IconButton(onClick = { showBulkDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        // 3-Dot Overflow Menu
                        Box {
                            IconButton(onClick = { showSelectionOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.action_more)
                                )
                            }
                            DropdownMenu(
                                expanded = showSelectionOverflowMenu,
                                onDismissRequest = { showSelectionOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_copy_to)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showSelectionOverflowMenu = false
                                        showCopyDestinationPicker = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_move_to)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showSelectionOverflowMenu = false
                                        showMoveDestinationPicker = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Compress (.zip)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Archive,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showSelectionOverflowMenu = false
                                        showCompressDialog = true
                                    }
                                )
                                if (selectedNodes.size == 1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_rename)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DriveFileRenameOutline,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showSelectionOverflowMenu = false
                                            nodeToRename = selectedNodes.first()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_properties)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showSelectionOverflowMenu = false
                                        if (selectedNodes.size == 1) {
                                            inspectedNode = selectedNodes.first()
                                        } else {
                                            showBatchPropertiesDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else if (isSearching) {
                LaunchedEffect(Unit) {
                    runCatching { searchFocusRequester.requestFocus() }
                }
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.category_search_hint, localizedCategoryName),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search"
                                        )
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
                                .focusRequester(searchFocusRequester)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeSearch() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit search"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            } else {
                // Standard Category TopAppBar
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = localizedCategoryName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val successState = uiState as? CategoryUiState.Success
                            if (successState != null) {
                                val summaryText = if (successState.fileCount == 1) {
                                    stringResource(R.string.category_header_summary_single, FileFormatter.formatFileSize(successState.totalSizeBytes))
                                } else {
                                    stringResource(R.string.category_header_summary, successState.fileCount, FileFormatter.formatFileSize(successState.totalSizeBytes))
                                }
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        val configuration = LocalConfiguration.current
                        val isWideScreen = configuration.screenWidthDp >= 600

                        // Search Action (Primary, always directly visible)
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search)
                            )
                        }

                        if (isWideScreen) {
                            if (category == CategoryFilter.APKS && onNavigateToInstalledApps != null) {
                                IconButton(onClick = onNavigateToInstalledApps) {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = stringResource(R.string.category_installed_apps),
                                        tint = categoryColor
                                    )
                                }
                            }
                            // View Mode Toggle (Grid / List)
                            IconButton(onClick = { viewModel.toggleViewMode() }) {
                                Icon(
                                    imageVector = if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = if (viewMode == ViewMode.GRID) stringResource(R.string.action_switch_to_list) else stringResource(R.string.action_switch_to_grid)
                                )
                            }

                            // Sort Action
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.action_sort)
                                )
                            }

                            // Refresh Action
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.action_refresh)
                                )
                            }
                        } else {
                            // Compact/Phone mode: collapse secondary tools into 3-dot overflow menu
                            Box {
                                IconButton(onClick = { showStandardOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_more)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showStandardOverflowMenu,
                                    onDismissRequest = { showStandardOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (viewMode == ViewMode.GRID) stringResource(R.string.action_switch_to_list)
                                                else stringResource(R.string.action_switch_to_grid)
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showStandardOverflowMenu = false
                                            viewModel.toggleViewMode()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_sort)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showStandardOverflowMenu = false
                                            showSortSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_refresh)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showStandardOverflowMenu = false
                                            viewModel.refresh()
                                        }
                                    )
                                    if (category == CategoryFilter.APKS && onNavigateToInstalledApps != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.category_installed_apps)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Apps,
                                                    contentDescription = null,
                                                    tint = categoryColor
                                                )
                                            },
                                            onClick = {
                                                showStandardOverflowMenu = false
                                                onNavigateToInstalledApps()
                                            }
                                        )
                                    }
                                }
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
            if (category == CategoryFilter.APKS && onNavigateToInstalledApps != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onNavigateToInstalledApps() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.category_installed_apps),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Inspect system packages, app storage, and extract APKs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            // Sort Controls Header Bar (when files are loaded)
            if (uiState is CategoryUiState.Success) {
                CategorySortControlBar(
                    sortOrder = sortOrder,
                    groupByDate = groupByDate,
                    onToggleGroupByDate = {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        viewModel.toggleGroupByDate()
                        coroutineScope.launch {
                            runCatching { listState.scrollToItem(0) }
                            runCatching { gridState.scrollToItem(0) }
                        }
                    },
                    onToggleDirection = {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        viewModel.toggleSortDirection()
                        coroutineScope.launch {
                            runCatching { listState.scrollToItem(0) }
                            runCatching { gridState.scrollToItem(0) }
                        }
                    },
                    onOpenSortSheet = { showSortSheet = true }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is CategoryUiState.Loading -> {
                        CategoryLoadingView(
                            category = category,
                            categoryColor = categoryColor
                        )
                    }

                    is CategoryUiState.Empty -> {
                        CategoryEmptyView(
                            category = category,
                            categoryColor = categoryColor,
                            categoryIcon = categoryIcon,
                            emptyMessage = state.message,
                            isSearchFilterEmpty = state.isSearchFilterEmpty,
                            onClearSearch = { viewModel.setSearchQuery("") },
                            onRefresh = { viewModel.refresh() }
                        )
                    }

                    is CategoryUiState.Error -> {
                        CategoryErrorView(
                            category = category,
                            errorMessage = state.message,
                            onRetry = { viewModel.refresh() }
                        )
                    }

                    is CategoryUiState.Success -> {
                        if (viewMode == ViewMode.GRID) {
                            CategoryGridView(
                                items = state.items,
                                dateGroups = state.dateGroups,
                                groupByDate = groupByDate,
                                categoryColor = categoryColor,
                                selectedNodes = selectedNodes,
                                isSelectionMode = isSelectionMode,
                                state = gridState,
                                onToggleGroupSelection = { group ->
                                    SelectionHaptics.playSelectionFeedback(context, view)
                                    viewModel.toggleGroupSelection(group)
                                },
                                onItemClick = { node ->
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(node)
                                    } else {
                                        handleItemClick(
                                            context = context,
                                            node = node,
                                            categoryName = category.name,
                                            rootPath = rootPath,
                                            onNavigateToArchiveViewer = onNavigateToArchiveViewer,
                                            onNavigateToPreview = onNavigateToPreview,
                                            audioItems = state.items.filter { !it.isDirectory && FileTypeResolver.isAudio(it.name) },
                                            onInstallApk = { apkToInstall = it }
                                        )
                                    }
                                },
                                onItemLongClick = { node ->
                                    if (!isSelectionMode) {
                                        viewModel.startSelection(node)
                                    } else {
                                        viewModel.toggleSelection(node)
                                    }
                                },
                                onCopy = {
                                    viewModel.startSelection(it)
                                    showCopyDestinationPicker = true
                                },
                                onMove = {
                                    viewModel.startSelection(it)
                                    showMoveDestinationPicker = true
                                },
                                onOpenWith = { FileIntentDispatcher.openWith(context, it) },
                                onShare = { FileIntentDispatcher.share(context, it) },
                                onSendTo = { FileIntentDispatcher.sendTo(context, it) },
                                onShowInFolder = { onNavigateToDirectory(File(it.path).parent ?: "") },
                                onRename = { nodeToRename = it },
                                onDelete = { nodeToDelete = it },
                                onProperties = { inspectedNode = it }
                            )
                        } else {
                            CategoryListView(
                                items = state.items,
                                dateGroups = state.dateGroups,
                                groupByDate = groupByDate,
                                categoryColor = categoryColor,
                                selectedNodes = selectedNodes,
                                isSelectionMode = isSelectionMode,
                                state = listState,
                                onToggleGroupSelection = { group ->
                                    SelectionHaptics.playSelectionFeedback(context, view)
                                    viewModel.toggleGroupSelection(group)
                                },
                                onItemClick = { node ->
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(node)
                                    } else {
                                        handleItemClick(
                                            context = context,
                                            node = node,
                                            categoryName = category.name,
                                            rootPath = rootPath,
                                            onNavigateToArchiveViewer = onNavigateToArchiveViewer,
                                            onNavigateToPreview = onNavigateToPreview,
                                            audioItems = state.items.filter { !it.isDirectory && FileTypeResolver.isAudio(it.name) },
                                            onInstallApk = { apkToInstall = it }
                                        )
                                    }
                                },
                                onItemLongClick = { node ->
                                    if (!isSelectionMode) {
                                        viewModel.startSelection(node)
                                    } else {
                                        viewModel.toggleSelection(node)
                                    }
                                },
                                onCopy = {
                                    viewModel.startSelection(it)
                                    showCopyDestinationPicker = true
                                },
                                onMove = {
                                    viewModel.startSelection(it)
                                    showMoveDestinationPicker = true
                                },
                                onOpenWith = { FileIntentDispatcher.openWith(context, it) },
                                onShare = { FileIntentDispatcher.share(context, it) },
                                onSendTo = { FileIntentDispatcher.sendTo(context, it) },
                                onShowInFolder = { onNavigateToDirectory(File(it.path).parent ?: "") },
                                onRename = { nodeToRename = it },
                                onDelete = { nodeToDelete = it },
                                onProperties = { inspectedNode = it }
                            )
                        }
                    }
                }
            }

            if (isAudioActive) {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Sort Bottom Sheet
    if (showSortSheet) {
        SortBottomSheet(
            currentOrder = sortOrder,
            onSortOrderChange = { viewModel.updateSortOrder(it) },
            onDismiss = { showSortSheet = false }
        )
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

    // Single Delete Confirmation Dialog
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

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteDialog && selectedNodes.isNotEmpty()) {
        BulkDeleteConfirmationDialog(
            selectedNodes = selectedNodes.toList(),
            onConfirmDelete = { permanent ->
                viewModel.deleteSelected(permanent = permanent)
                showBulkDeleteDialog = false
            },
            onDismiss = { showBulkDeleteDialog = false }
        )
    }

    // Copy Destination Picker Dialog
    if (showCopyDestinationPicker && selectedNodes.isNotEmpty()) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Copy ${selectedNodes.size} item(s) to",
            confirmButtonText = stringResource(R.string.action_copy_here),
            onDestinationSelected = { destination, verifyIntegrity ->
                showCopyDestinationPicker = false
                viewModel.copySelected(destination, verifyIntegrity)
            },
            onDismiss = { showCopyDestinationPicker = false }
        )
    }

    // Move Destination Picker Dialog
    if (showMoveDestinationPicker && selectedNodes.isNotEmpty()) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Move ${selectedNodes.size} item(s) to",
            confirmButtonText = stringResource(R.string.action_move_here),
            onDestinationSelected = { destination, verifyIntegrity ->
                showMoveDestinationPicker = false
                viewModel.moveSelected(destination, verifyIntegrity)
            },
            onDismiss = { showMoveDestinationPicker = false }
        )
    }

    // In-flight Operation Progress Dialog
    if (isOperating && operationProgress != null) {
        OperationProgressDialog(
            progress = operationProgress!!,
            title = "Processing Files…",
            onCancel = { /* background execution completes */ }
        )
    }

    apkToInstall?.let { file ->
        com.Hrshd1eux.filemanager.feature.apk.components.ApkInstallBottomSheet(
            apkFile = file,
            onDismiss = { apkToInstall = null }
        )
    }

    // File Properties Bottom Sheet
    inspectedNode?.let { node ->
        FilePropertiesSheet(
            node = node,
            onDismiss = { inspectedNode = null }
        )
    }

    // Compress Dialog
    if (showCompressDialog && selectedNodes.isNotEmpty()) {
        val defaultName = if (selectedNodes.size == 1) {
            selectedNodes.first().name.substringBeforeLast(".")
        } else {
            "archive_${System.currentTimeMillis()}"
        }
        val targetDir = StorageNode(
            id = "download",
            name = "Download",
            path = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath,
            size = 0L,
            lastModified = System.currentTimeMillis(),
            isDirectory = true
        )
        CompressDialog(
            sources = selectedNodes.toList(),
            defaultName = defaultName,
            onConfirmCreate = { _, archiveName, _ ->
                showCompressDialog = false
                viewModel.compressSelected(archiveName, targetDir)
            },
            onDismiss = { showCompressDialog = false }
        )
    }

    // Batch Properties Dialog
    if (showBatchPropertiesDialog && selectedNodes.isNotEmpty()) {
        val count = selectedNodes.size
        val totalBytes = selectedNodes.sumOf { it.size }
        val commonParent = remember(selectedNodes) {
            val parents = selectedNodes.mapNotNull { File(it.path).parent }.distinct()
            if (parents.size == 1) parents.first() else "Multiple folders (${parents.size})"
        }
        AlertDialog(
            onDismissRequest = { showBatchPropertiesDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.action_properties),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Selected Items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$count files",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = FileFormatter.formatFileSize(totalBytes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = commonParent,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchPropertiesDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

private fun handleItemClick(
    context: Context,
    node: StorageNode,
    categoryName: String,
    rootPath: String?,
    onNavigateToArchiveViewer: (archivePath: String, title: String) -> Unit,
    onNavigateToPreview: (filePath: String, categoryName: String?, rootPath: String?) -> Unit,
    audioItems: List<StorageNode> = emptyList(),
    onInstallApk: ((File) -> Unit)? = null
) {
    if (FileTypeResolver.canPreviewArchive(node.name)) {
        onNavigateToArchiveViewer(node.path, node.name)
    } else if (FileTypeResolver.isAudio(node.name) && categoryName.equals("AUDIO", ignoreCase = true)) {
        val audioList = if (audioItems.isNotEmpty()) audioItems else listOf(node)
        AudioPlaybackController.getInstance(context).playTrack(node, audioList)
    } else if (FileTypeResolver.isPackage(node.name) || node.name.endsWith(".apk", ignoreCase = true)) {
        if (onInstallApk != null) {
            onInstallApk(File(node.path))
        } else {
            FileIntentDispatcher.view(context, node)
        }
    } else {
        onNavigateToPreview(node.path, categoryName, rootPath)
    }
}

@Composable
private fun CategorySortControlBar(
    sortOrder: com.Hrshd1eux.filemanager.core.display.model.SortOrder,
    groupByDate: Boolean,
    onToggleGroupByDate: () -> Unit,
    onToggleDirection: () -> Unit,
    onOpenSortSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Sort Order Chip
        FilterChip(
            selected = true,
            onClick = onOpenSortSheet,
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            label = {
                Text(
                    text = "${stringResource(R.string.category_sorted_by)} ${sortOrder.field.displayName}",
                    style = MaterialTheme.typography.labelMedium
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(8.dp)
        )

        // 2. Ascending / Descending Toggle Chip
        AssistChip(
            onClick = onToggleDirection,
            leadingIcon = {
                Icon(
                    imageVector = if (sortOrder.direction == SortDirection.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            label = {
                Text(
                    text = if (sortOrder.direction == SortDirection.ASCENDING) stringResource(R.string.category_ascending) else stringResource(R.string.category_descending),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp)
        )

        // 3. Group by Date Toggle Chip
        FilterChip(
            selected = groupByDate,
            onClick = onToggleGroupByDate,
            leadingIcon = {
                Icon(
                    imageVector = if (groupByDate) Icons.Default.CheckCircle else Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.category_group_by_date),
                    style = MaterialTheme.typography.labelMedium
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun CategoryLoadingView(
    category: CategoryFilter,
    categoryColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = categoryColor,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanning ${category.displayName}…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryEmptyView(
    category: CategoryFilter,
    categoryColor: Color,
    categoryIcon: ImageVector,
    emptyMessage: String,
    isSearchFilterEmpty: Boolean = false,
    onClearSearch: () -> Unit = {},
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearchFilterEmpty) Icons.Default.SearchOff else categoryIcon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (isSearchFilterEmpty) "No Matching Results" else "No ${category.displayName} Found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = if (isSearchFilterEmpty) onClearSearch else onRefresh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (isSearchFilterEmpty) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSearchFilterEmpty) stringResource(R.string.action_clear) else stringResource(R.string.action_refresh))
            }
        }
    }
}

@Composable
private fun CategoryErrorView(
    category: CategoryFilter,
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Storage Inaccessible",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun CategoryDateGroupHeader(
    group: CategoryDateGroup,
    categoryColor: Color,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleGroupSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectionMode) {
                SelectionHaptics.playSelectionFeedback(context, view)
                onToggleGroupSelection()
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        onToggleGroupSelection()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            val localizedGroupTitle = when (group.type) {
                DateBucketType.TODAY -> stringResource(R.string.date_group_today)
                DateBucketType.YESTERDAY -> stringResource(R.string.date_group_yesterday)
                DateBucketType.THIS_WEEK -> stringResource(R.string.date_group_this_week)
                DateBucketType.THIS_MONTH -> stringResource(R.string.date_group_this_month)
                DateBucketType.UNKNOWN -> stringResource(R.string.date_group_unknown)
                DateBucketType.MONTH_YEAR, DateBucketType.YEAR -> group.title
            }

            Text(
                text = localizedGroupTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            val groupCountText = if (group.fileCount == 1) {
                stringResource(R.string.date_group_items_count_single, FileFormatter.formatFileSize(group.totalSizeBytes))
            } else {
                stringResource(R.string.date_group_items_count, group.fileCount, FileFormatter.formatFileSize(group.totalSizeBytes))
            }
            Text(
                text = groupCountText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryListView(
    items: List<StorageNode>,
    dateGroups: List<CategoryDateGroup>,
    groupByDate: Boolean,
    categoryColor: Color,
    selectedNodes: Set<StorageNode>,
    isSelectionMode: Boolean,
    onToggleGroupSelection: (CategoryDateGroup) -> Unit,
    onItemClick: (StorageNode) -> Unit,
    onItemLongClick: (StorageNode) -> Unit,
    onCopy: (StorageNode) -> Unit,
    onMove: (StorageNode) -> Unit,
    onOpenWith: (StorageNode) -> Unit,
    onShare: (StorageNode) -> Unit,
    onSendTo: (StorageNode) -> Unit,
    onShowInFolder: (StorageNode) -> Unit,
    onRename: (StorageNode) -> Unit,
    onDelete: (StorageNode) -> Unit,
    onProperties: (StorageNode) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (groupByDate && dateGroups.isNotEmpty()) {
            dateGroups.forEach { group ->
                stickyHeader(key = "header_${group.id}") {
                    val isGroupSelected = group.items.isNotEmpty() && group.items.all { item ->
                        selectedNodes.any { it.path == item.path }
                    }
                    CategoryDateGroupHeader(
                        group = group,
                        categoryColor = categoryColor,
                        isSelectionMode = isSelectionMode,
                        isSelected = isGroupSelected,
                        onToggleGroupSelection = { onToggleGroupSelection(group) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 4.dp)
                    )
                }
                items(group.items, key = { it.path }) { node ->
                    val isSelected = selectedNodes.any { it.path == node.path }
                    CategoryListItemRow(
                        node = node,
                        categoryColor = categoryColor,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = { onItemClick(node) },
                        onLongClick = { onItemLongClick(node) },
                        onCopy = { onCopy(node) },
                        onMove = { onMove(node) },
                        onOpenWith = { onOpenWith(node) },
                        onShare = { onShare(node) },
                        onSendTo = { onSendTo(node) },
                        onShowInFolder = { onShowInFolder(node) },
                        onRename = { onRename(node) },
                        onDelete = { onDelete(node) },
                        onProperties = { onProperties(node) }
                    )
                }
            }
        } else {
            items(items, key = { it.path }) { node ->
                val isSelected = selectedNodes.any { it.path == node.path }
                CategoryListItemRow(
                    node = node,
                    categoryColor = categoryColor,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onClick = { onItemClick(node) },
                    onLongClick = { onItemLongClick(node) },
                    onCopy = { onCopy(node) },
                    onMove = { onMove(node) },
                    onOpenWith = { onOpenWith(node) },
                    onShare = { onShare(node) },
                    onSendTo = { onSendTo(node) },
                    onShowInFolder = { onShowInFolder(node) },
                    onRename = { onRename(node) },
                    onDelete = { onDelete(node) },
                    onProperties = { onProperties(node) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryListItemRow(
    node: StorageNode,
    categoryColor: Color,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onSendTo: () -> Unit,
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
                categoryColor = categoryColor,
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // File Metadata: Name, Parent Folder, Size, and Date
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

                val formattedSize = FileFormatter.formatFileSize(node.size)
                val formattedDate = FileFormatter.formatDateTime(node.lastModified)
                Text(
                    text = "$formattedSize • $formattedDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }

            // Actions dropdown
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_with)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpenWith()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_show_in_folder)) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShowInFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_properties)) },
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

@Composable
private fun CategoryGridView(
    items: List<StorageNode>,
    dateGroups: List<CategoryDateGroup>,
    groupByDate: Boolean,
    categoryColor: Color,
    selectedNodes: Set<StorageNode>,
    isSelectionMode: Boolean,
    onToggleGroupSelection: (CategoryDateGroup) -> Unit,
    onItemClick: (StorageNode) -> Unit,
    onItemLongClick: (StorageNode) -> Unit,
    onCopy: (StorageNode) -> Unit,
    onMove: (StorageNode) -> Unit,
    onOpenWith: (StorageNode) -> Unit,
    onShare: (StorageNode) -> Unit,
    onSendTo: (StorageNode) -> Unit,
    onShowInFolder: (StorageNode) -> Unit,
    onRename: (StorageNode) -> Unit,
    onDelete: (StorageNode) -> Unit,
    onProperties: (StorageNode) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState()
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (groupByDate && dateGroups.isNotEmpty()) {
            dateGroups.forEach { group ->
                item(
                    key = "header_${group.id}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    val isGroupSelected = group.items.isNotEmpty() && group.items.all { item ->
                        selectedNodes.any { it.path == item.path }
                    }
                    CategoryDateGroupHeader(
                        group = group,
                        categoryColor = categoryColor,
                        isSelectionMode = isSelectionMode,
                        isSelected = isGroupSelected,
                        onToggleGroupSelection = { onToggleGroupSelection(group) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 4.dp)
                    )
                }
                items(group.items, key = { it.path }) { node ->
                    val isSelected = selectedNodes.any { it.path == node.path }
                    CategoryGridItemCard(
                        node = node,
                        categoryColor = categoryColor,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = { onItemClick(node) },
                        onLongClick = { onItemLongClick(node) },
                        onCopy = { onCopy(node) },
                        onMove = { onMove(node) },
                        onOpenWith = { onOpenWith(node) },
                        onShare = { onShare(node) },
                        onSendTo = { onSendTo(node) },
                        onShowInFolder = { onShowInFolder(node) },
                        onRename = { onRename(node) },
                        onDelete = { onDelete(node) },
                        onProperties = { onProperties(node) }
                    )
                }
            }
        } else {
            items(items, key = { it.path }) { node ->
                val isSelected = selectedNodes.any { it.path == node.path }
                CategoryGridItemCard(
                    node = node,
                    categoryColor = categoryColor,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onClick = { onItemClick(node) },
                    onLongClick = { onItemLongClick(node) },
                    onCopy = { onCopy(node) },
                    onMove = { onMove(node) },
                    onOpenWith = { onOpenWith(node) },
                    onShare = { onShare(node) },
                    onSendTo = { onSendTo(node) },
                    onShowInFolder = { onShowInFolder(node) },
                    onRename = { onRename(node) },
                    onDelete = { onDelete(node) },
                    onProperties = { onProperties(node) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryGridItemCard(
    node: StorageNode,
    categoryColor: Color,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onSendTo: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Centered Thumbnail
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    FileThumbnailView(
                        node = node,
                        categoryColor = categoryColor,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                // Selection Checkbox (Top Left)
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            SelectionHaptics.playSelectionFeedback(context, view)
                            onClick()
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(24.dp)
                    )
                }

                // Options Menu (Top Right)
                if (!isSelectionMode) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(
                            onClick = { showMenu = !showMenu },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_with)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpenWith()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_copy)) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onCopy()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onMove()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onShare()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_show_in_folder)) },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onShowInFolder()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_properties)) },
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

            Spacer(modifier = Modifier.height(6.dp))

            // File Name
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // File Size & Modification Date
            val formattedSize = FileFormatter.formatFileSize(node.size)
            val formattedDate = FileFormatter.formatDateTime(node.lastModified)
            Text(
                text = "$formattedSize • $formattedDate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun getCategoryColor(category: CategoryFilter): Color {
    return when (category) {
        CategoryFilter.IMAGES -> Color(0xFF00ACC1) // Cyan
        CategoryFilter.VIDEOS -> Color(0xFF8E24AA) // Purple
        CategoryFilter.AUDIO -> Color(0xFFFF7043) // Coral / Orange
        CategoryFilter.DOCUMENTS -> Color(0xFF1E88E5) // Blue
        CategoryFilter.DOWNLOADS -> Color(0xFFE53935) // Red
        CategoryFilter.ARCHIVES -> Color(0xFFFB8C00) // Amber
        CategoryFilter.APKS -> Color(0xFF43A047) // Green
        CategoryFilter.OTHER -> Color(0xFF78909C) // Slate / Blue Grey
        CategoryFilter.ALL -> Color(0xFF1E88E5)
    }
}

private fun getCategoryIcon(category: CategoryFilter): ImageVector {
    return when (category) {
        CategoryFilter.IMAGES -> Icons.Default.Image
        CategoryFilter.VIDEOS -> Icons.Default.VideoLibrary
        CategoryFilter.AUDIO -> Icons.Default.Audiotrack
        CategoryFilter.DOCUMENTS -> Icons.Default.Description
        CategoryFilter.DOWNLOADS -> Icons.Default.Download
        CategoryFilter.ARCHIVES -> Icons.Default.FolderZip
        CategoryFilter.APKS -> Icons.Default.Android
        CategoryFilter.OTHER -> Icons.Default.Category
        CategoryFilter.ALL -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
