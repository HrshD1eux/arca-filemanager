package com.Hrshd1eux.filemanager.feature.cleaner.storage

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.cleaner.model.AnalyzerSortBy
import com.Hrshd1eux.filemanager.core.cleaner.model.CleanupCandidate
import com.Hrshd1eux.filemanager.core.cleaner.model.DeletionTruthfulness
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateGroup
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateMatchLevel
import com.Hrshd1eux.filemanager.core.cleaner.model.EmptyFolderItem
import com.Hrshd1eux.filemanager.core.cleaner.model.LargeFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.model.OldFileThreshold
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.AppTypeFilter
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.feature.cleaner.apps.InstalledAppsViewModel
import com.Hrshd1eux.filemanager.feature.cleaner.apps.components.AppDetailsBottomSheet
import com.Hrshd1eux.filemanager.feature.cleaner.apps.components.ShareApkBottomSheet
import com.Hrshd1eux.filemanager.feature.cleaner.storage.components.SafeCleanupReviewSheet
import com.Hrshd1eux.filemanager.feature.cleaner.storage.components.SystemStorageDetailsBottomSheet
import com.Hrshd1eux.filemanager.feature.home.HomeViewModel

/**
 * Storage Analyzer 2.0 Screen (Phase B — Milestones 1.4.11 – 1.4.20).
 *
 * Implements 6 comprehensive analysis views:
 * 1. Overview (Storage breakdown, system storage, installed apps, non-overlapping categories, diagnostic alerts)
 * 2. Large Files (Configurable thresholds from 25MB to 1GB, sorting by size & date)
 * 3. Duplicate Candidates (Staged detection: size -> metadata -> SHA-256 hash confirmation vs possible matches)
 * 4. Old Files (Age thresholds 3M–2Y, modification date disclaimer)
 * 5. Archives (All supported archive formats with size and metadata)
 * 6. Empty Folders (Zero-content directories)
 *
 * Safe Cleanup Integration (1.4.15 & 1.4.16):
 * - Explicit item selection and review modal before any deletion.
 * - Honest flash memory wear-leveling and modification date disclaimers.
 * - Pre-flight revalidation ensuring target existence and stability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstalledApps: () -> Unit,
    onNavigateToCategory: (CategoryFilter) -> Unit,
    onNavigateToLargeFiles: () -> Unit,
    onNavigateToDuplicateFinder: () -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel(),
    appsViewModel: InstalledAppsViewModel = viewModel(),
    analyzer2ViewModel: StorageAnalyzer2ViewModel = viewModel()
) {
    val context = LocalContext.current
    val storageBreakdown by homeViewModel.storageBreakdown.collectAsStateWithLifecycle()
    val appsUiState by appsViewModel.uiState.collectAsStateWithLifecycle()
    val analyzerUiState by analyzer2ViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCleanupReviewSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        analyzer2ViewModel.userMessage.collect { message ->
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
                        text = "Storage Analyzer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
                    IconButton(
                        onClick = {
                            homeViewModel.refreshStorageBreakdown()
                            appsViewModel.loadApps(forceRefresh = true)
                            analyzer2ViewModel.refreshCurrentTab()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Breakdown"
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
            if (analyzerUiState.selectedIds.isNotEmpty() && analyzerUiState.activeTab != StorageAnalyzerTab.OVERVIEW) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                            text = "${analyzerUiState.selectedIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = { showCleanupReviewSheet = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Review Cleanup")
                        }
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
            // Scrollable Tab Row for 6 views
            ScrollableTabRow(
                selectedTabIndex = analyzerUiState.activeTab.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider() }
            ) {
                StorageAnalyzerTab.entries.forEach { tab ->
                    Tab(
                        selected = analyzerUiState.activeTab == tab,
                        onClick = { analyzer2ViewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (analyzerUiState.activeTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Scanning indicator
            if (analyzerUiState.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Inaccessible directories honest diagnostic notice
            val diag = analyzerUiState.diagnostics
            if (diag != null && diag.hasInaccessiblePaths) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${diag.inaccessibleFoldersCount} system-restricted paths (such as /Android/data) were excluded honestly from calculation without guessing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // View Content Switcher
            when (analyzerUiState.activeTab) {
                StorageAnalyzerTab.OVERVIEW -> {
                    OverviewTabContent(
                        storageBreakdown = storageBreakdown,
                        appsUiState = appsUiState,
                        onOpenSystemStorage = { appsViewModel.toggleSystemStorageDetails(true) },
                        onNavigateToInstalledApps = onNavigateToInstalledApps,
                        onNavigateToCategory = onNavigateToCategory,
                        onSelectTab = { analyzer2ViewModel.selectTab(it) },
                        onSelectAppForDetails = { appsViewModel.selectAppForDetails(it) },
                        onViewUserApps = {
                            appsViewModel.setTypeFilter(AppTypeFilter.USER)
                            onNavigateToInstalledApps()
                        },
                        onViewSystemApps = {
                            appsViewModel.setTypeFilter(AppTypeFilter.SYSTEM)
                            onNavigateToInstalledApps()
                        }
                    )
                }
                StorageAnalyzerTab.LARGE_FILES -> {
                    LargeFilesTabContent(
                        uiState = analyzerUiState,
                        onThresholdChange = { analyzer2ViewModel.setLargeFileThreshold(it) },
                        onSortChange = { analyzer2ViewModel.setSortBy(it) },
                        onToggleSelection = { analyzer2ViewModel.toggleSelection(it) },
                        onSelectAll = { analyzer2ViewModel.selectAll() },
                        onClearSelection = { analyzer2ViewModel.clearSelection() },
                        onReviewCleanup = { showCleanupReviewSheet = true }
                    )
                }
                StorageAnalyzerTab.DUPLICATES -> {
                    DuplicatesTabContent(
                        uiState = analyzerUiState,
                        onToggleSelection = { analyzer2ViewModel.toggleSelection(it) },
                        onSelectAll = { analyzer2ViewModel.selectAll() },
                        onClearSelection = { analyzer2ViewModel.clearSelection() },
                        onReviewCleanup = { showCleanupReviewSheet = true }
                    )
                }
                StorageAnalyzerTab.OLD_FILES -> {
                    OldFilesTabContent(
                        uiState = analyzerUiState,
                        onThresholdChange = { analyzer2ViewModel.setOldFileThreshold(it) },
                        onSortChange = { analyzer2ViewModel.setSortBy(it) },
                        onToggleSelection = { analyzer2ViewModel.toggleSelection(it) },
                        onSelectAll = { analyzer2ViewModel.selectAll() },
                        onClearSelection = { analyzer2ViewModel.clearSelection() },
                        onReviewCleanup = { showCleanupReviewSheet = true }
                    )
                }
                StorageAnalyzerTab.ARCHIVES -> {
                    ArchivesTabContent(
                        uiState = analyzerUiState,
                        onSortChange = { analyzer2ViewModel.setSortBy(it) },
                        onToggleSelection = { analyzer2ViewModel.toggleSelection(it) },
                        onSelectAll = { analyzer2ViewModel.selectAll() },
                        onClearSelection = { analyzer2ViewModel.clearSelection() },
                        onReviewCleanup = { showCleanupReviewSheet = true }
                    )
                }
                StorageAnalyzerTab.EMPTY_FOLDERS -> {
                    EmptyFoldersTabContent(
                        uiState = analyzerUiState,
                        onToggleSelection = { analyzer2ViewModel.toggleSelection(it) },
                        onSelectAll = { analyzer2ViewModel.selectAll() },
                        onClearSelection = { analyzer2ViewModel.clearSelection() },
                        onReviewCleanup = { showCleanupReviewSheet = true }
                    )
                }
            }
        }
    }

    // Safe Cleanup Review Bottom Sheet (1.4.15 & 1.4.16)
    if (showCleanupReviewSheet) {
        val candidates = analyzer2ViewModel.getSelectedCleanupCandidates()
        SafeCleanupReviewSheet(
            candidates = candidates,
            onConfirmDelete = { targets ->
                showCleanupReviewSheet = false
                analyzer2ViewModel.executeSafeCleanup(targets)
            },
            onOpenItem = { candidate ->
                if (candidate.isDirectory) {
                    Toast.makeText(context, "Directory: ${candidate.path}", Toast.LENGTH_SHORT).show()
                } else {
                    FileIntentDispatcher.view(context, candidate.toStorageNode())
                }
            },
            onDismiss = { showCleanupReviewSheet = false }
        )
    }

    // App Details Bottom Sheet
    if (appsUiState.selectedAppForDetails != null) {
        AppDetailsBottomSheet(
            app = appsUiState.selectedAppForDetails!!,
            onDismiss = { appsViewModel.selectAppForDetails(null) },
            onCopied = { appsViewModel.emitMessage(it) },
            onShareApk = { app ->
                appsViewModel.selectAppForDetails(null)
                appsViewModel.selectAppForShare(app)
            }
        )
    }

    // Share APK Bottom Sheet
    if (appsUiState.selectedAppForShare != null) {
        ShareApkBottomSheet(
            app = appsUiState.selectedAppForShare!!,
            onDismiss = { appsViewModel.selectAppForShare(null) }
        )
    }

    // System Storage Details Bottom Sheet
    if (appsUiState.showSystemStorageDetails) {
        SystemStorageDetailsBottomSheet(
            breakdown = appsUiState.systemStorageBreakdown,
            onDismiss = { appsViewModel.toggleSystemStorageDetails(false) },
            onViewSystemApps = {
                appsViewModel.toggleSystemStorageDetails(false)
                appsViewModel.setTypeFilter(AppTypeFilter.SYSTEM)
                onNavigateToInstalledApps()
            }
        )
    }
}

// =============================================================================
// TAB 1: OVERVIEW
// =============================================================================

@Composable
private fun OverviewTabContent(
    storageBreakdown: com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown,
    appsUiState: com.Hrshd1eux.filemanager.feature.cleaner.apps.InstalledAppsUiState,
    onOpenSystemStorage: () -> Unit,
    onNavigateToInstalledApps: () -> Unit,
    onNavigateToCategory: (CategoryFilter) -> Unit,
    onSelectTab: (StorageAnalyzerTab) -> Unit,
    onSelectAppForDetails: (InstalledAppInfo) -> Unit,
    onViewUserApps: () -> Unit,
    onViewSystemApps: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // 1. Device Storage Overview Card
        item {
            val usedFormatted = FileFormatter.formatFileSize(storageBreakdown.usedBytes)
            val totalFormatted = FileFormatter.formatFileSize(storageBreakdown.totalBytes)
            val freeFormatted = FileFormatter.formatFileSize(storageBreakdown.freeBytes)
            val percentInt = (storageBreakdown.usedPercentage * 100).toInt()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shared Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$usedFormatted / $totalFormatted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { storageBreakdown.usedPercentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {}
                    )

                    if (storageBreakdown.totalBytes > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$freeFormatted free",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$percentInt% used",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2. System & OS Storage Inspection Card
        item {
            val sysBreakdown = appsUiState.systemStorageBreakdown
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSystemStorage),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System & OS Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "OS: ${sysBreakdown.formattedAndroidOsSize} • System Apps: ${sysBreakdown.formattedSystemAppsSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Inspect",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Installed Apps Section
        item {
            InstalledAppsStorageSection(
                report = appsUiState.report,
                isLoading = appsUiState.isLoading,
                onViewUserApps = onViewUserApps,
                onViewSystemApps = onViewSystemApps,
                onViewAllApps = onNavigateToInstalledApps,
                onAppClick = onSelectAppForDetails
            )
        }

        // 4. Storage Categories Breakdown (Non-overlapping strict partition)
        item {
            Text(
                text = "Storage Categories (Zero Double-Counting)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    storageBreakdown.categories.forEachIndexed { index, catSize ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    catSize.category.searchCategory?.let { onNavigateToCategory(it) }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(catSize.category.color)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${catSize.category.displayName} (${catSize.fileCount} files)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = FileFormatter.formatFileSize(catSize.byteCount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < storageBreakdown.categories.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }

        // 5. Quick Tools Hub
        item {
            Text(
                text = "Analysis & Safe Cleanup Views",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    QuickToolItem(
                        icon = Icons.Default.CleaningServices,
                        title = "Large Files",
                        subtitle = "Find files exceeding configurable size thresholds",
                        onClick = { onSelectTab(StorageAnalyzerTab.LARGE_FILES) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    QuickToolItem(
                        icon = Icons.Default.ContentCopy,
                        title = "Duplicate Candidates",
                        subtitle = "Staged size & SHA-256 hash match detection",
                        onClick = { onSelectTab(StorageAnalyzerTab.DUPLICATES) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    QuickToolItem(
                        icon = Icons.Default.History,
                        title = "Old Files",
                        subtitle = "Inspect files untouched for 3M to 2Y with context",
                        onClick = { onSelectTab(StorageAnalyzerTab.OLD_FILES) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    QuickToolItem(
                        icon = Icons.Default.Archive,
                        title = "Archive Files",
                        subtitle = "Browse all zip, rar, 7z, tar, gz, iso archives",
                        onClick = { onSelectTab(StorageAnalyzerTab.ARCHIVES) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    QuickToolItem(
                        icon = Icons.Default.FolderOpen,
                        title = "Empty Folders",
                        subtitle = "Tidy zero-content directories safely",
                        onClick = { onSelectTab(StorageAnalyzerTab.EMPTY_FOLDERS) }
                    )
                }
            }
        }
    }
}

// =============================================================================
// TAB 2: LARGE FILES
// =============================================================================

@Composable
private fun LargeFilesTabContent(
    uiState: StorageAnalyzer2UiState,
    onThresholdChange: (LargeFileThreshold) -> Unit,
    onSortChange: (AnalyzerSortBy) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onReviewCleanup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Threshold Filters Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LargeFileThreshold.entries.forEach { th ->
                FilterChip(
                    selected = uiState.largeFileThreshold == th,
                    onClick = { onThresholdChange(th) },
                    label = { Text("≥ ${th.displayName}") }
                )
            }
        }

        // Sorting Row & Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uiState.largeFiles.size} items",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.selectedIds.isNotEmpty()) {
                    TextButton(onClick = onClearSelection) {
                        Text("Clear (${uiState.selectedIds.size})")
                    }
                } else if (uiState.largeFiles.isNotEmpty()) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                }
            }
        }

        if (uiState.largeFiles.isEmpty() && !uiState.isScanning) {
            EmptyViewPlaceholder(
                icon = Icons.Default.CleaningServices,
                title = "No Large Files",
                description = "No files found exceeding ${uiState.largeFileThreshold.displayName}."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.largeFiles, key = { it.id }) { item ->
                    CleanupItemCard(
                        candidate = item,
                        isSelected = uiState.selectedIds.contains(item.id),
                        onToggle = { onToggleSelection(item.id) }
                    )
                }
            }
        }
    }
}

// =============================================================================
// TAB 3: DUPLICATES
// =============================================================================

@Composable
private fun DuplicatesTabContent(
    uiState: StorageAnalyzer2UiState,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onReviewCleanup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Duplicate disclaimer banner
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = DeletionTruthfulness.DUPLICATE_CERTAINTY_NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Summary bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uiState.duplicateResult.groups.size} groups • ${FileFormatter.formatFileSize(uiState.duplicateResult.totalWastedBytes)} wasted",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.selectedIds.isNotEmpty()) {
                    TextButton(onClick = onClearSelection) {
                        Text("Clear (${uiState.selectedIds.size})")
                    }
                } else if (uiState.duplicateResult.groups.isNotEmpty()) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select Duplicates")
                    }
                }
            }
        }

        if (uiState.duplicateResult.groups.isEmpty() && !uiState.isScanning) {
            EmptyViewPlaceholder(
                icon = Icons.Default.ContentCopy,
                title = "No Duplicates Found",
                description = "No identical duplicate files were found in storage."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.duplicateResult.groups, key = { it.hash }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        selectedIds = uiState.selectedIds,
                        onToggle = onToggleSelection
                    )
                }
            }
        }
    }
}

// =============================================================================
// TAB 4: OLD FILES
// =============================================================================

@Composable
private fun OldFilesTabContent(
    uiState: StorageAnalyzer2UiState,
    onThresholdChange: (OldFileThreshold) -> Unit,
    onSortChange: (AnalyzerSortBy) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onReviewCleanup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Threshold Filters Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OldFileThreshold.entries.forEach { th ->
                FilterChip(
                    selected = uiState.oldFileThreshold == th,
                    onClick = { onThresholdChange(th) },
                    label = { Text("≥ ${th.displayName}") }
                )
            }
        }

        // Truthful Modification Date Disclaimer (1.4.12)
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = DeletionTruthfulness.MODIFICATION_DATE_DISCLAIMER,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // Summary bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uiState.oldFiles.size} items",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.selectedIds.isNotEmpty()) {
                    TextButton(onClick = onClearSelection) {
                        Text("Clear (${uiState.selectedIds.size})")
                    }
                } else if (uiState.oldFiles.isNotEmpty()) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                }
            }
        }

        if (uiState.oldFiles.isEmpty() && !uiState.isScanning) {
            EmptyViewPlaceholder(
                icon = Icons.Default.History,
                title = "No Old Files",
                description = "No files found unedited for ${uiState.oldFileThreshold.displayName}."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.oldFiles, key = { it.id }) { item ->
                    CleanupItemCard(
                        candidate = item,
                        isSelected = uiState.selectedIds.contains(item.id),
                        onToggle = { onToggleSelection(item.id) }
                    )
                }
            }
        }
    }
}

// =============================================================================
// TAB 5: ARCHIVES
// =============================================================================

@Composable
private fun ArchivesTabContent(
    uiState: StorageAnalyzer2UiState,
    onSortChange: (AnalyzerSortBy) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onReviewCleanup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uiState.archives.size} archive packages",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.selectedIds.isNotEmpty()) {
                    TextButton(onClick = onClearSelection) {
                        Text("Clear (${uiState.selectedIds.size})")
                    }
                } else if (uiState.archives.isNotEmpty()) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                }
            }
        }

        if (uiState.archives.isEmpty() && !uiState.isScanning) {
            EmptyViewPlaceholder(
                icon = Icons.Default.Archive,
                title = "No Archives Found",
                description = "No zip, rar, 7z, tar, or compressed files were found in storage."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.archives, key = { it.id }) { item ->
                    CleanupItemCard(
                        candidate = item,
                        isSelected = uiState.selectedIds.contains(item.id),
                        onToggle = { onToggleSelection(item.id) }
                    )
                }
            }
        }
    }
}

// =============================================================================
// TAB 6: EMPTY FOLDERS
// =============================================================================

@Composable
private fun EmptyFoldersTabContent(
    uiState: StorageAnalyzer2UiState,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onReviewCleanup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Notice banner
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Empty folders contain 0 files and 0 subdirectories. Removing them tidies filesystem structure safely without losing data.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Summary bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${uiState.emptyFolders.size} empty directories",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.selectedIds.isNotEmpty()) {
                    TextButton(onClick = onClearSelection) {
                        Text("Clear (${uiState.selectedIds.size})")
                    }
                } else if (uiState.emptyFolders.isNotEmpty()) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                }
            }
        }

        if (uiState.emptyFolders.isEmpty() && !uiState.isScanning) {
            EmptyViewPlaceholder(
                icon = Icons.Default.FolderOpen,
                title = "No Empty Folders",
                description = "All directories contain files or subfolders."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.emptyFolders, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSelection(item.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = uiState.selectedIds.contains(item.id),
                                onCheckedChange = { onToggleSelection(item.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = FolderYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// REUSABLE COMPONENTS
// =============================================================================

@Composable
private fun CleanupItemCard(
    candidate: CleanupCandidate,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = resolveFileIcon(candidate.name),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${FileFormatter.formatDateTime(candidate.lastModified)} • ${candidate.path}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = FileFormatter.formatFileSize(candidate.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Group Header with match level badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = if (group.matchLevel == DuplicateMatchLevel.CONFIRMED_HASH_MATCH) {
                        VaultEmerald
                    } else {
                        Color(0xFFFFA000)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (group.matchLevel == DuplicateMatchLevel.CONFIRMED_HASH_MATCH) "SHA-256 Confirmed" else "Possible Match",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${group.files.size} copies",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${FileFormatter.formatFileSize(group.wastedBytes)} wasted",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // File items
            group.files.forEachIndexed { index, node ->
                val isSelected = selectedIds.contains(node.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(node.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggle(node.id) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (index == 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[Original]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = node.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = FileFormatter.formatFileSize(node.size),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToolItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyViewPlaceholder(
    icon: ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun InstalledAppsStorageSection(
    report: com.Hrshd1eux.filemanager.core.storage.analyzer.InstalledAppsReport,
    isLoading: Boolean,
    onViewUserApps: () -> Unit,
    onViewSystemApps: () -> Unit,
    onViewAllApps: () -> Unit,
    onAppClick: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = VaultEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Installed Applications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isLoading) "Scanning packages..." else "${report.userAppCount} user • ${report.systemAppCount} system",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isLoading && report.totalMeasurableBytes > 0L) {
                    Text(
                        text = FileFormatter.formatFileSize(report.totalMeasurableBytes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VaultEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).clickable(onClick = onViewUserApps),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultEmerald.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "User Apps",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = VaultEmerald
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${report.userAppCount} installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).clickable(onClick = onViewSystemApps),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "System Apps",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${report.systemAppCount} pre-installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val topApps = report.apps
                .filter { it.isUserApp }
                .sortedByDescending { it.storageMeasurement.totalBytes ?: -1L }
                .take(3)

            if (topApps.isNotEmpty()) {
                topApps.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppClick(app) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = app.storageMeasurement.formattedTotalSize,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (app.storageMeasurement.isAccessible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = onViewAllApps,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage All Apps (${report.totalAppCount})")
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun resolveFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "heic" -> Icons.Default.Image
        "mp4", "mkv", "mov", "avi", "webm" -> Icons.Default.Movie
        "mp3", "m4a", "flac", "wav", "aac" -> Icons.Default.MusicNote
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "apk" -> Icons.Default.Archive
        else -> Icons.Default.Description
    }
}
