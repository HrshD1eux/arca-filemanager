package com.Hrshd1eux.filemanager.feature.category

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
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
    onNavigateToDirectory: (path: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CategoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    var showSortSheet by remember { mutableStateOf(false) }
    var nodeToRename by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var inspectedNode by remember { mutableStateOf<StorageNode?>(null) }

    LaunchedEffect(categoryName, rootPath) {
        viewModel.loadCategory(categoryName, rootPath)
    }

    val categoryColor = getCategoryColor(category)
    val categoryIcon = getCategoryIcon(category)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val successState = uiState as? CategoryUiState.Success
                        if (successState != null) {
                            Text(
                                text = "${successState.fileCount} items • ${FileFormatter.formatFileSize(successState.totalSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                    // View Mode Toggle (Grid / List)
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (viewMode == ViewMode.GRID) "Switch to List View" else "Switch to Grid View"
                        )
                    }

                    // Sort Action
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort"
                        )
                    }

                    // Refresh Action
                    IconButton(onClick = { viewModel.refresh() }) {
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                            categoryColor = categoryColor,
                            onItemClick = { node ->
                                handleItemClick(
                                    context = context,
                                    node = node,
                                    onNavigateToArchiveViewer = onNavigateToArchiveViewer
                                )
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
                            categoryColor = categoryColor,
                            onItemClick = { node ->
                                handleItemClick(
                                    context = context,
                                    node = node,
                                    onNavigateToArchiveViewer = onNavigateToArchiveViewer
                                )
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

    // Delete Confirmation Dialog
    nodeToDelete?.let { node ->
        DeleteConfirmationDialog(
            node = node,
            onConfirmDelete = {
                viewModel.deleteFile(node)
                nodeToDelete = null
            },
            onDismiss = { nodeToDelete = null }
        )
    }

    // Properties Bottom Sheet
    inspectedNode?.let { node ->
        FilePropertiesSheet(
            node = node,
            onDismiss = { inspectedNode = null }
        )
    }
}

private fun handleItemClick(
    context: Context,
    node: StorageNode,
    onNavigateToArchiveViewer: (archivePath: String, title: String) -> Unit
) {
    if (FileTypeResolver.canPreviewArchive(node.name)) {
        onNavigateToArchiveViewer(node.path, node.name)
    } else {
        FileIntentDispatcher.view(context, node)
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
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "No ${category.displayName} Found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "No matching files were found in local storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onRefresh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
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
                text = "Failed to load ${category.displayName}",
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
                Text("Retry")
            }
        }
    }
}

@Composable
private fun CategoryListView(
    items: List<StorageNode>,
    categoryColor: Color,
    onItemClick: (StorageNode) -> Unit,
    onOpenWith: (StorageNode) -> Unit,
    onShare: (StorageNode) -> Unit,
    onSendTo: (StorageNode) -> Unit,
    onShowInFolder: (StorageNode) -> Unit,
    onRename: (StorageNode) -> Unit,
    onDelete: (StorageNode) -> Unit,
    onProperties: (StorageNode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.path }) { node ->
            CategoryListItemRow(
                node = node,
                categoryColor = categoryColor,
                onClick = { onItemClick(node) },
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

@Composable
private fun CategoryListItemRow(
    node: StorageNode,
    categoryColor: Color,
    onClick: () -> Unit,
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
    val itemIcon = getFileItemIcon(node)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = itemIcon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(22.dp)
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

                Text(
                    text = File(node.path).parent ?: node.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val meta = "${FileFormatter.formatFileSize(node.size)} • ${FileFormatter.formatDateTime(node.lastModified)}"
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

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
                        text = { Text("Open with") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenWith()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Send to…") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onSendTo()
                        }
                    )
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

@Composable
private fun CategoryGridView(
    items: List<StorageNode>,
    categoryColor: Color,
    onItemClick: (StorageNode) -> Unit,
    onOpenWith: (StorageNode) -> Unit,
    onShare: (StorageNode) -> Unit,
    onSendTo: (StorageNode) -> Unit,
    onShowInFolder: (StorageNode) -> Unit,
    onRename: (StorageNode) -> Unit,
    onDelete: (StorageNode) -> Unit,
    onProperties: (StorageNode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.path }) { node ->
            CategoryGridItemCard(
                node = node,
                categoryColor = categoryColor,
                onClick = { onItemClick(node) },
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

@Composable
private fun CategoryGridItemCard(
    node: StorageNode,
    categoryColor: Color,
    onClick: () -> Unit,
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
    val itemIcon = getFileItemIcon(node)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(categoryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = itemIcon,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Box {
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
                            text = { Text("Open with") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpenWith()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send to…") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onSendTo()
                            }
                        )
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = FileFormatter.formatFileSize(node.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
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

private fun getFileItemIcon(node: StorageNode): ImageVector {
    val name = node.name
    return when {
        FileTypeResolver.isImage(name) -> Icons.Default.Image
        FileTypeResolver.isVideo(name) -> Icons.Default.VideoLibrary
        FileTypeResolver.isAudio(name) -> Icons.Default.Audiotrack
        FileTypeResolver.isDocument(name) -> Icons.Default.Description
        FileTypeResolver.isArchive(name) -> Icons.Default.FolderZip
        FileTypeResolver.isPackage(name) -> Icons.Default.Android
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}
