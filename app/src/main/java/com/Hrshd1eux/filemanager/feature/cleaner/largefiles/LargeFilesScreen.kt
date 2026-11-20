package com.Hrshd1eux.filemanager.feature.cleaner.largefiles

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.Hrshd1eux.filemanager.core.designsystem.SelectionHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.cleaner.model.LargeFileThreshold
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileShareHelper
import com.Hrshd1eux.filemanager.feature.browser.components.BulkDeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFilesScreen(
    rootPath: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LargeFilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val threshold by viewModel.threshold.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val activeProgress by viewModel.activeProgress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var inspectedNode by remember { mutableStateOf<StorageNode?>(null) }
    var showSelectionOverflowMenu by remember { mutableStateOf(false) }
    var showBatchPropertiesDialog by remember { mutableStateOf(false) }

    val isSelectionMode = selectedIds.isNotEmpty()

    LaunchedEffect(rootPath) {
        viewModel.setRootPath(rootPath)
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
            TopAppBar(
                title = {
                    Text(
                        text = if (isSelectionMode) "${selectedIds.size} Selected" else "Large Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) viewModel.clearSelection() else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        val state = uiState as? LargeFilesUiState.Success
                        val allSelected = state != null && state.files.isNotEmpty() && selectedIds.size >= state.files.size

                        IconButton(onClick = {
                            if (allSelected) viewModel.clearSelection() else viewModel.selectAll()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All"
                            )
                        }
                        IconButton(onClick = {
                            if (state != null) {
                                val selectedNodes = state.files.filter { selectedIds.contains(it.id) }
                                FileShareHelper.shareNodes(context, selectedNodes)
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Box {
                            IconButton(onClick = { showSelectionOverflowMenu = !showSelectionOverflowMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(
                                expanded = showSelectionOverflowMenu,
                                onDismissRequest = { showSelectionOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Properties") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Info, contentDescription = null)
                                    },
                                    onClick = {
                                        showSelectionOverflowMenu = false
                                        showBatchPropertiesDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Selection") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Deselect, contentDescription = null)
                                    },
                                    onClick = {
                                        showSelectionOverflowMenu = false
                                        viewModel.clearSelection()
                                    }
                                )
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Threshold Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LargeFileThreshold.entries.forEach { t ->
                    val isSelected = threshold == t
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setThreshold(t) },
                        label = { Text(t.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            when (val state = uiState) {
                is LargeFilesUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Scanning for large files...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is LargeFilesUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Large Files Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No files exceeding ${threshold.displayName} were detected on your storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is LargeFilesUiState.Success -> {
                    // Total Banner
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.files.size} Large Files Found",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = FileFormatter.formatFileSize(state.totalBytes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.files, key = { it.id }) { file ->
                            LargeFileItemRow(
                                file = file,
                                isSelected = selectedIds.contains(file.id),
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(file.id)
                                    } else {
                                        inspectedNode = file
                                    }
                                },
                                onToggleSelection = { viewModel.toggleSelection(file.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Active Operation Progress Dialog
    if (activeProgress != null) {
        OperationProgressDialog(
            progress = activeProgress!!,
            onCancel = { /* Managed by engine */ }
        )
    }

    // Selected Files Properties Dialog
    if (showBatchPropertiesDialog && uiState is LargeFilesUiState.Success) {
        val state = uiState as LargeFilesUiState.Success
        val selectedNodes = state.files.filter { selectedIds.contains(it.id) }
        val totalSize = selectedNodes.sumOf { it.size }

        AlertDialog(
            onDismissRequest = { showBatchPropertiesDialog = false },
            title = {
                Text(
                    if (selectedNodes.size == 1) "File Properties" else "Selected Files Properties",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedNodes.size == 1) {
                        val file = selectedNodes.first()
                        Text("Name: ${file.name}", fontWeight = FontWeight.SemiBold)
                        Text("Size: ${FileFormatter.formatFileSize(file.size)}")
                        Text("Path: ${file.path}")
                    } else {
                        Text("Selected Files: ${selectedNodes.size}", fontWeight = FontWeight.SemiBold)
                        Text("Total Size: ${FileFormatter.formatFileSize(totalSize)}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchPropertiesDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        val state = uiState
        if (state is LargeFilesUiState.Success) {
            val selectedNodes = state.files.filter { selectedIds.contains(it.id) }
            BulkDeleteConfirmationDialog(
                selectedNodes = selectedNodes,
                onConfirmDelete = { permanent ->
                    showDeleteConfirmDialog = false
                    viewModel.deleteSelected(permanent = permanent)
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }
    }

    // File Properties Sheet
    if (inspectedNode != null) {
        FilePropertiesSheet(
            node = inspectedNode!!,
            onDismiss = { inspectedNode = null }
        )
    }
}

@Composable
private fun LargeFileItemRow(
    file: StorageNode,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {
                if (isSelectionMode) {
                    SelectionHaptics.playSelectionFeedback(context, view)
                }
                onClick()
            }),
        shape = RoundedCornerShape(12.dp),
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        onToggleSelection()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = resolveFileIcon(file.name),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.path} • ${FileFormatter.formatDateTime(file.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = FileFormatter.formatFileSize(file.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun resolveFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "heic" -> Icons.Default.Image
        "mp4", "mkv", "mov", "avi", "webm" -> Icons.Default.Movie
        "mp3", "m4a", "flac", "wav", "aac" -> Icons.Default.MusicNote
        "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Description
        else -> Icons.Default.Description
    }
}
