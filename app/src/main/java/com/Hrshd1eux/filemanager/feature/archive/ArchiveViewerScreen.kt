package com.Hrshd1eux.filemanager.feature.archive

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.archive.components.ArchiveAddItemsDialog
import com.Hrshd1eux.filemanager.feature.archive.components.ArchivePropertiesSheet
import com.Hrshd1eux.filemanager.feature.archive.components.ArchiveTestDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    archivePath: String,
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchiveViewerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEntryPaths by viewModel.selectedEntryPaths.collectAsStateWithLifecycle()
    val activeProgress by viewModel.activeProgress.collectAsStateWithLifecycle()
    val testReport by viewModel.testReport.collectAsStateWithLifecycle()
    val archiveProperties by viewModel.archiveProperties.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showExtractPicker by remember { mutableStateOf(false) }
    var showAddPicker by remember { mutableStateOf(false) }
    val localProvider = remember { LocalFileSystemProvider() }

    LaunchedEffect(archivePath) {
        viewModel.loadArchive(archivePath)
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler {
        if (!viewModel.navigateUp()) {
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifEmpty { File(archivePath).name },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            if (uiState is ArchiveViewerUiState.Success) {
                if (selectedEntryPaths.isNotEmpty()) {
                    // Selected Items Contextual Extraction Bar
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedEntryPaths.size} selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Row {
                                Button(
                                    onClick = { showExtractPicker = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Extract Selected")
                                }
                            }
                        }
                    }
                } else {
                    // Persistent 4 Actions: Extract, Add, Test, Properties
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = false,
                            onClick = { showExtractPicker = true },
                            icon = { Icon(Icons.Default.Unarchive, contentDescription = "Extract") },
                            label = { Text("Extract") },
                            colors = NavigationBarItemDefaults.colors(
                                unselectedIconColor = ZipOrange,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { showAddPicker = true },
                            icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                            label = { Text("Add") },
                            colors = NavigationBarItemDefaults.colors(
                                unselectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { viewModel.testArchive() },
                            icon = { Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = "Test") },
                            label = { Text("Test") },
                            colors = NavigationBarItemDefaults.colors(
                                unselectedIconColor = MaterialTheme.colorScheme.tertiary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { viewModel.loadArchiveProperties() },
                            icon = { Icon(Icons.Outlined.Info, contentDescription = "Properties") },
                            label = { Text("Properties") },
                            colors = NavigationBarItemDefaults.colors(
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ArchiveViewerUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ZipOrange)
                    }
                }
                is ArchiveViewerUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Error Opening Archive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ArchiveViewerUiState.Success -> {
                    // Archive Summary Header Banner
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ZipOrange.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${state.totalFilesCount} Files in Archive",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Compressed: ${FileFormatter.formatFileSize(state.totalCompressedBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = FileFormatter.formatFileSize(state.totalUncompressedBytes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ZipOrange
                                )
                                Text(
                                    text = "uncompressed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Breadcrumbs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Archive Root",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (state.breadcrumbs.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                            color = if (state.breadcrumbs.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { viewModel.navigateToBreadcrumb("") }
                        )

                        var accumulatedPath = ""
                        state.breadcrumbs.forEach { segment ->
                            accumulatedPath = if (accumulatedPath.isEmpty()) segment else "$accumulatedPath/$segment"
                            val currentStepPath = accumulatedPath
                            val isLast = currentStepPath == state.currentVirtualPath

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )

                            Text(
                                text = segment,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { viewModel.navigateToBreadcrumb(currentStepPath) }
                            )
                        }
                    }

                    if (state.items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "This folder is empty.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.items, key = { it.path }) { entry ->
                                ArchiveEntryRow(
                                    entry = entry,
                                    isSelected = selectedEntryPaths.contains(entry.path),
                                    isSelectionMode = selectedEntryPaths.isNotEmpty(),
                                    onClick = {
                                        if (entry.isDirectory) {
                                            viewModel.navigateInto(entry)
                                        } else {
                                            viewModel.toggleSelection(entry.path)
                                        }
                                    },
                                    onToggleSelection = { viewModel.toggleSelection(entry.path) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Extract Destination Picker Dialog
    if (showExtractPicker) {
        val isSelective = selectedEntryPaths.isNotEmpty()
        DestinationPickerDialog(
            storageProvider = localProvider,
            title = if (isSelective) "Extract ${selectedEntryPaths.size} Selected Items to..." else "Extract All to...",
            confirmButtonText = "Extract Here",
            onDestinationSelected = { destNode ->
                showExtractPicker = false
                if (isSelective) {
                    val extractTargetDir = File(destNode.path)
                    viewModel.extractSelected(extractTargetDir)
                } else {
                    val extractTargetDir = File(destNode.path, File(archivePath).nameWithoutExtension)
                    viewModel.extractAll(extractTargetDir)
                }
            },
            onDismiss = { showExtractPicker = false }
        )
    }

    // Add Items Source Picker Dialog
    if (showAddPicker) {
        ArchiveAddItemsDialog(
            storageProvider = localProvider,
            onItemsSelected = { sourceFiles ->
                showAddPicker = false
                viewModel.addFiles(sourceFiles)
            },
            onDismiss = { showAddPicker = false }
        )
    }

    // Archive Test Dialog
    val currentTestReport = testReport
    if (currentTestReport != null) {
        ArchiveTestDialog(
            report = currentTestReport,
            onDismiss = { viewModel.dismissTestReport() }
        )
    }

    // Archive Properties Sheet
    val currentProperties = archiveProperties
    if (currentProperties != null) {
        ArchivePropertiesSheet(
            properties = currentProperties,
            onDismiss = { viewModel.dismissArchiveProperties() }
        )
    }

    // Active Operation Progress Dialog
    if (activeProgress != null) {
        OperationProgressDialog(
            progress = activeProgress!!,
            onCancel = { /* Managed by engine */ }
        )
    }
}

@Composable
private fun ArchiveEntryRow(
    entry: ArchiveEntry,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            if (!entry.isDirectory || isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (entry.isDirectory) FolderYellow.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Default.Folder else resolveEntryIcon(entry.name),
                    contentDescription = null,
                    tint = if (entry.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isDirectory) "Folder" else "${FileFormatter.formatFileSize(entry.uncompressedSize)} • ${FileFormatter.formatDateTime(entry.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.isDirectory) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            } else if (entry.compressionRatio > 0.05f) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "-${(entry.compressionRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun resolveEntryIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
        "mp4", "mkv", "mov", "avi" -> Icons.Default.Movie
        "mp3", "m4a", "flac", "wav" -> Icons.Default.MusicNote
        else -> Icons.Default.Description
    }
}
