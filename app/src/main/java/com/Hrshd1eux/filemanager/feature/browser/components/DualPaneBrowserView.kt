package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropPayload
import com.Hrshd1eux.filemanager.core.storage.permission.StoragePermissionManager
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.core.storage.util.FolderComparator
import com.Hrshd1eux.filemanager.core.storage.util.FolderComparisonReport
import com.Hrshd1eux.filemanager.feature.browser.BrowserUiState
import com.Hrshd1eux.filemanager.feature.browser.FileBrowserViewModel
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem
import java.io.File

enum class PaneSide { LEFT, RIGHT }

/**
 * Power-user Dual-Pane layout (1.2.7):
 * Renders Left and Right directory browser panes side-by-side with independent
 * navigation, 1-tap inter-pane copy/move, drag & drop, and folder comparison.
 */
@Composable
fun DualPaneBrowserView(
    leftViewModel: FileBrowserViewModel,
    rightViewModel: FileBrowserViewModel,
    onNavigateToArchiveViewer: (String, String) -> Unit,
    activePane: PaneSide = PaneSide.LEFT,
    onActivePaneChange: (PaneSide) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var comparisonReport by remember { mutableStateOf<FolderComparisonReport?>(null) }
    var showCreateFolderForPane by remember { mutableStateOf<PaneSide?>(null) }

    val leftState by leftViewModel.uiState.collectAsStateWithLifecycle()
    val rightState by rightViewModel.uiState.collectAsStateWithLifecycle()

    val leftSelectedIds by leftViewModel.selectedNodeIds.collectAsStateWithLifecycle()
    val rightSelectedIds by rightViewModel.selectedNodeIds.collectAsStateWithLifecycle()

    // Active nodes
    val leftSuccess = leftState as? BrowserUiState.Success
    val rightSuccess = rightState as? BrowserUiState.Success

    val leftSelectedNodes = leftSuccess?.items?.filter { leftSelectedIds.contains(it.id) } ?: emptyList()
    val rightSelectedNodes = rightSuccess?.items?.filter { rightSelectedIds.contains(it.id) } ?: emptyList()

    // Initialize Right Pane with secondary path if not initialized
    LaunchedEffect(Unit) {
        val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
        if (rightState is BrowserUiState.Loading) {
            val root = rightViewModel.storageProvider.getRootNode()
            val downloadDir = File(root.path, "Download")
            val downloadsDir = File(root.path, "Downloads")
            val initialRightPath = when {
                downloadDir.exists() && downloadDir.isDirectory -> downloadDir.absolutePath
                downloadsDir.exists() && downloadsDir.isDirectory -> downloadsDir.absolutePath
                else -> root.path
            }
            rightViewModel.initialize(initialRightPath, hasPermission)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Inter-Pane Power Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Inter-pane quick actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            val leftPath = leftSuccess?.currentPath
                            val rightPath = rightSuccess?.currentPath
                            if (leftPath != null && rightPath != null) {
                                leftViewModel.loadDirectory(rightPath, hasPermission)
                                rightViewModel.loadDirectory(leftPath, hasPermission)
                            }
                        }
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Swap Panes", tint = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = {
                            val leftItems = leftSuccess?.items ?: emptyList()
                            val rightItems = rightSuccess?.items ?: emptyList()
                            comparisonReport = FolderComparator.compareFolders(leftItems, rightItems)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compare", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Inter-pane Transfer Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leftSelectedNodes.isNotEmpty() && rightSuccess != null) {
                        val targetNode = StorageNode(
                            id = rightSuccess.currentPath,
                            name = rightSuccess.currentTitle,
                            path = rightSuccess.currentPath,
                            isDirectory = true
                        )
                        Button(
                            onClick = {
                                val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                                leftViewModel.startCopy(leftSelectedNodes, targetNode, hasPermission = hasPerm)
                                rightViewModel.refresh(hasPerm)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy (${leftSelectedNodes.size})", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        OutlinedButton(
                            onClick = {
                                val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                                leftViewModel.startMove(leftSelectedNodes, targetNode, hasPermission = hasPerm)
                                rightViewModel.refresh(hasPerm)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Move (${leftSelectedNodes.size})", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (rightSelectedNodes.isNotEmpty() && leftSuccess != null) {
                        val targetNode = StorageNode(
                            id = leftSuccess.currentPath,
                            name = leftSuccess.currentTitle,
                            path = leftSuccess.currentPath,
                            isDirectory = true
                        )
                        Button(
                            onClick = {
                                val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                                rightViewModel.startCopy(rightSelectedNodes, targetNode, hasPermission = hasPerm)
                                leftViewModel.refresh(hasPerm)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy (${rightSelectedNodes.size})", style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        OutlinedButton(
                            onClick = {
                                val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                                rightViewModel.startMove(rightSelectedNodes, targetNode, hasPermission = hasPerm)
                                leftViewModel.refresh(hasPerm)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Move (${rightSelectedNodes.size})", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Dual Panes View (Side by Side)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Left Pane
            SinglePaneColumn(
                title = leftSuccess?.currentTitle ?: "Left Pane",
                uiState = leftState,
                viewModel = leftViewModel,
                selectedIds = leftSelectedIds,
                selectedNodes = leftSelectedNodes,
                isActive = activePane == PaneSide.LEFT,
                onFocus = { onActivePaneChange(PaneSide.LEFT) },
                onNewFolder = { showCreateFolderForPane = PaneSide.LEFT },
                onNavigateToArchiveViewer = onNavigateToArchiveViewer,
                onDropFromOther = { payload ->
                    if (leftSuccess != null) {
                        val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                        val currentTarget = StorageNode(
                            id = leftSuccess.currentPath,
                            name = leftSuccess.currentTitle,
                            path = leftSuccess.currentPath,
                            isDirectory = true
                        )
                        leftViewModel.handleDroppedPayload(payload, currentTarget, hasPerm, context)
                        rightViewModel.refresh(hasPerm)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            VerticalDivider(modifier = Modifier.fillMaxHeight(), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Right Pane
            SinglePaneColumn(
                title = rightSuccess?.currentTitle ?: "Right Pane",
                uiState = rightState,
                viewModel = rightViewModel,
                selectedIds = rightSelectedIds,
                selectedNodes = rightSelectedNodes,
                isActive = activePane == PaneSide.RIGHT,
                onFocus = { onActivePaneChange(PaneSide.RIGHT) },
                onNewFolder = { showCreateFolderForPane = PaneSide.RIGHT },
                onNavigateToArchiveViewer = onNavigateToArchiveViewer,
                onDropFromOther = { payload ->
                    if (rightSuccess != null) {
                        val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                        val currentTarget = StorageNode(
                            id = rightSuccess.currentPath,
                            name = rightSuccess.currentTitle,
                            path = rightSuccess.currentPath,
                            isDirectory = true
                        )
                        rightViewModel.handleDroppedPayload(payload, currentTarget, hasPerm, context)
                        leftViewModel.refresh(hasPerm)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }

    // Comparison Modal
    val report = comparisonReport
    if (report != null && leftSuccess != null && rightSuccess != null) {
        FolderComparisonDialog(
            leftFolderName = leftSuccess.currentTitle,
            rightFolderName = rightSuccess.currentTitle,
            report = report,
            onSyncMissingToRight = {
                val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                val targetFolder = StorageNode(
                    id = rightSuccess.currentPath,
                    name = rightSuccess.currentTitle,
                    path = rightSuccess.currentPath,
                    isDirectory = true
                )
                val itemsToSync = report.items
                    .filter { it.status == com.Hrshd1eux.filemanager.core.storage.util.ComparisonStatus.UNIQUE_LEFT || it.status == com.Hrshd1eux.filemanager.core.storage.util.ComparisonStatus.MODIFIED }
                    .mapNotNull { it.leftNode }

                if (itemsToSync.isNotEmpty()) {
                    leftViewModel.startCopy(itemsToSync, targetFolder, hasPermission = hasPerm)
                    rightViewModel.refresh(hasPerm)
                }
            },
            onDismiss = { comparisonReport = null }
        )
    }

    // New Folder Dialog for either pane
    val paneToCreateFolder = showCreateFolderForPane
    if (paneToCreateFolder != null) {
        val targetVm = if (paneToCreateFolder == PaneSide.LEFT) leftViewModel else rightViewModel
        val targetPath = if (paneToCreateFolder == PaneSide.LEFT) leftSuccess?.currentPath else rightSuccess?.currentPath
        val rootPath = targetVm.storageProvider.getRootNode().path

        CreateFolderDialog(
            onConfirmCreate = { folderName ->
                showCreateFolderForPane = null
                val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                targetVm.createDirectory(
                    parentPath = targetPath ?: rootPath,
                    dirName = folderName,
                    hasPermission = hasPerm
                )
            },
            onDismiss = { showCreateFolderForPane = null }
        )
    }
}

@Composable
private fun SinglePaneColumn(
    title: String,
    uiState: BrowserUiState,
    viewModel: FileBrowserViewModel,
    selectedIds: Set<String>,
    selectedNodes: List<StorageNode>,
    isActive: Boolean,
    onFocus: () -> Unit,
    onNewFolder: () -> Unit,
    onNavigateToArchiveViewer: (String, String) -> Unit,
    onDropFromOther: (DragDropPayload) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .background(if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .clickable(onClick = onFocus)
    ) {
        // Pane Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNewFolder, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                            viewModel.refresh(hasPerm)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Content
        when (val state = uiState) {
            is BrowserUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            is BrowserUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            is BrowserUiState.PermissionRequired -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Storage permission required", style = MaterialTheme.typography.bodySmall)
                }
            }
            is BrowserUiState.Success -> {
                // Pane Breadcrumbs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.breadcrumbs.forEachIndexed { idx, crumb ->
                        if (idx > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
                        Text(
                            text = crumb.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (crumb.isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (crumb.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                                viewModel.navigateToBreadcrumb(crumb, hasPerm)
                            }
                        )
                    }
                }

                if (state.items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Empty folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(state.items, key = { it.id }) { node ->
                            StorageNodeCompactItem(
                                node = node,
                                isSelected = selectedIds.contains(node.id),
                                isSelectionMode = selectedIds.isNotEmpty(),
                                isFavorite = false,
                                allSelectedNodes = selectedNodes,
                                onDropIntoFolder = { targetFolder, payload ->
                                    val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                                    viewModel.handleDroppedPayload(payload, targetFolder, hasPerm, context)
                                },
                                onClick = {
                                    onFocus()
                                    if (node.isDirectory) {
                                        val hasPerm = StoragePermissionManager.hasAllFilesAccess(context)
                                        viewModel.navigateInto(node, hasPerm)
                                    } else if (FileTypeResolver.canPreviewArchive(node.name)) {
                                        onNavigateToArchiveViewer(node.path, node.name)
                                    }
                                },
                                onToggleSelection = {
                                    onFocus()
                                    viewModel.toggleSelection(node.id)
                                },
                                onToggleFavorite = {},
                                onCopyClick = {},
                                onMoveClick = {},
                                onRenameClick = {},
                                onDeleteClick = {},
                                onShareClick = {},
                                onInfoClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
