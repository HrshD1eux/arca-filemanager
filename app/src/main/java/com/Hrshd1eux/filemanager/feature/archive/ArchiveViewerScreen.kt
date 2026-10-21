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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Info
import com.Hrshd1eux.filemanager.feature.archive.components.PasswordPromptDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.Hrshd1eux.filemanager.core.designsystem.SelectionHaptics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.feature.archive.components.ArchiveAddItemsDialog
import com.Hrshd1eux.filemanager.feature.archive.components.ArchivePropertiesSheet
import com.Hrshd1eux.filemanager.feature.archive.components.ArchiveTestDialog
import com.Hrshd1eux.filemanager.feature.archive.components.ExtractOptionsBottomSheet
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.checksum.components.ChecksumDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import com.Hrshd1eux.filemanager.core.dragdrop.DragDropManager
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

    var showExtractOptionsSheet by remember { mutableStateOf(false) }
    var showExtractCustomPicker by remember { mutableStateOf(false) }
    var activeConflictResolution by remember { mutableStateOf(ConflictResolution.RENAME_AUTO) }
    var activeExtractVerify by remember { mutableStateOf(false) }
    var showAddPicker by remember { mutableStateOf(false) }
    var showArchiveChecksums by remember { mutableStateOf(false) }
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
                    Column {
                        Text(
                            text = title.ifEmpty { File(archivePath).name },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val success = uiState as? ArchiveViewerUiState.Success
                        if (success?.formatBadge != null) {
                            Text(
                                text = success.formatBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
            val successState = uiState as? ArchiveViewerUiState.Success
            if (successState != null) {
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
                                    onClick = { showExtractOptionsSheet = true },
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
                            onClick = { showExtractOptionsSheet = true },
                            icon = { Icon(Icons.Default.Unarchive, contentDescription = "Extract") },
                            label = { Text("Extract") },
                            colors = NavigationBarItemDefaults.colors(
                                unselectedIconColor = ZipOrange,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            enabled = !successState.isReadOnly,
                            onClick = { showAddPicker = true },
                            icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                            label = { Text(if (successState.isReadOnly) "Add (N/A)" else "Add") },
                            colors = NavigationBarItemDefaults.colors(
                                unselectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
                is ArchiveViewerUiState.PasswordRequired -> {
                    var showPrompt by remember { mutableStateOf(true) }
                    if (showPrompt) {
                        PasswordPromptDialog(
                            archiveName = state.archiveName,
                            onUnlock = { password ->
                                showPrompt = false
                                viewModel.unlockArchive(password)
                            },
                            onDismiss = {
                                showPrompt = false
                                onNavigateBack()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = ZipOrange,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Encrypted Archive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message ?: "This archive is password-protected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showPrompt = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Enter Password")
                            }
                        }
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
                                if (state.formatBadge != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = state.formatBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
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
                                    archivePath = archivePath,
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

    // Extract Options Bottom Sheet
    if (showExtractOptionsSheet) {
        val isSelective = selectedEntryPaths.isNotEmpty()
        ExtractOptionsBottomSheet(
            archiveName = File(archivePath).name,
            isSelective = isSelective,
            selectedCount = selectedEntryPaths.size,
            onExtractToSubfolder = { resolution, verifyIntegrity ->
                showExtractOptionsSheet = false
                val parentDir = File(archivePath).parentFile ?: File("/storage/emulated/0")
                val targetDir = File(parentDir, File(archivePath).nameWithoutExtension)
                viewModel.extractAll(
                    destinationDir = targetDir,
                    conflictResolution = resolution,
                    verifyIntegrity = verifyIntegrity
                )
            },
            onExtractHere = { resolution, verifyIntegrity ->
                showExtractOptionsSheet = false
                val parentDir = File(archivePath).parentFile ?: File("/storage/emulated/0")
                if (isSelective) {
                    viewModel.extractSelected(
                        destinationDir = parentDir,
                        conflictResolution = resolution,
                        verifyIntegrity = verifyIntegrity
                    )
                } else {
                    viewModel.extractAll(
                        destinationDir = parentDir,
                        conflictResolution = resolution,
                        verifyIntegrity = verifyIntegrity
                    )
                }
            },
            onExtractToCustom = { resolution, verifyIntegrity ->
                showExtractOptionsSheet = false
                activeConflictResolution = resolution
                activeExtractVerify = verifyIntegrity
                showExtractCustomPicker = true
            },
            onDismiss = { showExtractOptionsSheet = false }
        )
    }

    // Extract Destination Picker Dialog (Custom Directory)
    if (showExtractCustomPicker) {
        val isSelective = selectedEntryPaths.isNotEmpty()
        DestinationPickerDialog(
            storageProvider = localProvider,
            title = if (isSelective) "Extract ${selectedEntryPaths.size} Selected Items to..." else "Extract All to...",
            confirmButtonText = "Extract Here",
            onDestinationSelected = { destNode, _ ->
                showExtractCustomPicker = false
                if (isSelective) {
                    val extractTargetDir = File(destNode.path)
                    viewModel.extractSelected(
                        destinationDir = extractTargetDir,
                        conflictResolution = activeConflictResolution,
                        verifyIntegrity = activeExtractVerify
                    )
                } else {
                    val extractTargetDir = File(destNode.path, File(archivePath).nameWithoutExtension)
                    viewModel.extractAll(
                        destinationDir = extractTargetDir,
                        conflictResolution = activeConflictResolution,
                        verifyIntegrity = activeExtractVerify
                    )
                }
            },
            onDismiss = { showExtractCustomPicker = false }
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
            archiveName = File(archivePath).name,
            isTesting = false,
            progress = null,
            report = currentTestReport,
            onCancel = {},
            onDismiss = { viewModel.dismissTestReport() }
        )
    }

    // Archive Properties Sheet
    val currentProperties = archiveProperties
    if (currentProperties != null) {
        ArchivePropertiesSheet(
            properties = currentProperties,
            onDismiss = { viewModel.dismissArchiveProperties() },
            onVerifyChecksums = {
                viewModel.dismissArchiveProperties()
                showArchiveChecksums = true
            }
        )
    }

    // Archive Checksum & File Verification Dialog
    if (showArchiveChecksums) {
        val archiveNodeFile = File(archivePath)
        ChecksumDialog(
            node = StorageNode(
                id = archiveNodeFile.absolutePath,
                path = archiveNodeFile.absolutePath,
                name = archiveNodeFile.name,
                size = archiveNodeFile.length(),
                lastModified = archiveNodeFile.lastModified(),
                isDirectory = false
            ),
            onDismiss = { showArchiveChecksums = false }
        )
    }

    // Active Operation Progress Dialog
    if (activeProgress != null) {
        OperationProgressDialog(
            progress = activeProgress!!,
            title = "Extracting Archive",
            onCancel = { viewModel.cancelActiveOperation() }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchiveEntryRow(
    archivePath: String,
    entry: ArchiveEntry,
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
                    onCheckedChange = {
                        SelectionHaptics.playSelectionFeedback(context, view)
                        onToggleSelection()
                    }
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
                    )
                    .dragAndDropSource {
                        detectTapGestures(
                            onLongPress = {
                                startTransfer(
                                    DragAndDropTransferData(
                                        clipData = DragDropManager.createArchiveEntryClipData(
                                            archivePath = archivePath,
                                            entryPath = entry.path
                                        )
                                    )
                                )
                            }
                        )
                    },
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
