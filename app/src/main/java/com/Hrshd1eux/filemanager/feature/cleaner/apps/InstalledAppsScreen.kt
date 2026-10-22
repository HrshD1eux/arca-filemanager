package com.Hrshd1eux.filemanager.feature.cleaner.apps

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.Hrshd1eux.filemanager.core.designsystem.SelectionHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.storage.model.AppTypeFilter
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.cleaner.apps.components.AppDetailsBottomSheet
import com.Hrshd1eux.filemanager.feature.cleaner.apps.components.InstalledAppItem
import com.Hrshd1eux.filemanager.feature.cleaner.apps.components.InstalledAppsSortBottomSheet
import com.Hrshd1eux.filemanager.feature.cleaner.apps.components.ShareApkBottomSheet
import com.Hrshd1eux.filemanager.feature.cleaner.storage.components.SystemStorageDetailsBottomSheet

/**
 * Screen displaying installed user and system applications with clean category separation,
 * per-app storage measurements, accuracy indicators, and operating-system storage inspection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToApkCategory: (() -> Unit)? = null,
    viewModel: InstalledAppsViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortSheet by remember { mutableStateOf(false) }

    // Resume observer to detect when user returns from granting Usage Access
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResumeCheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedPackageNames.size} selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection"
                            )
                        }
                    },
                    actions = {
                        val allSelected = uiState.filteredApps.isNotEmpty() &&
                                uiState.selectedPackageNames.containsAll(uiState.filteredApps.map { it.packageName })
                        IconButton(onClick = {
                            SelectionHaptics.playSelectionFeedback(context, view)
                            viewModel.selectAll()
                        }) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All"
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.exportSelectedApks(context) { chooserIntent ->
                                    context.startActivity(chooserIntent)
                                }
                            },
                            enabled = !uiState.isBatchExporting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Selected APKs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Installed Apps",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (!uiState.isLoading && uiState.report.totalAppCount > 0) {
                                val totalFormatted = FileFormatter.formatFileSize(uiState.report.totalMeasurableBytes)
                                Text(
                                    text = "${uiState.report.totalAppCount} apps • $totalFormatted measurable",
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
                        if (onNavigateToApkCategory != null) {
                            IconButton(onClick = onNavigateToApkCategory) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = "View APK Files",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // Inspect OS Storage button
                        IconButton(onClick = { viewModel.toggleSystemStorageDetails(true) }) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Inspect System Storage",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Package Name on demand toggle
                        IconButton(
                            onClick = { viewModel.toggleShowPackageNames() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Toggle Package Names",
                                tint = if (uiState.showPackageNames) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Sort button
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort"
                            )
                        }

                        // Refresh button
                        IconButton(onClick = { viewModel.loadApps(forceRefresh = true) }) {
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
            if (onNavigateToApkCategory != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onNavigateToApkCategory() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "View APK Installation Files",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scan and manage standalone .apk/.xapk installer files",
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

            // Category Separation Tabs: User Apps vs System Apps vs All Apps
            val tabOptions = listOf(
                AppTypeFilter.USER to "User Apps (${uiState.report.userAppCount})",
                AppTypeFilter.SYSTEM to "System Apps (${uiState.report.systemAppCount})",
                AppTypeFilter.ALL to "All Apps (${uiState.report.totalAppCount})"
            )
            val selectedTabIndex = tabOptions.indexOfFirst { it.first == uiState.typeFilter }.coerceAtLeast(0)

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabOptions.forEachIndexed { index, (filter, label) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.setTypeFilter(filter) },
                        text = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Search Input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search by app name or package...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear query"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Category & Storage Context Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uiState.typeFilter == AppTypeFilter.SYSTEM) Icons.Default.Storage else if (uiState.isUsageAccessGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (uiState.isUsageAccessGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (uiState.typeFilter) {
                            AppTypeFilter.SYSTEM -> {
                                "Visible System Apps: Packages in /system/app and /product. Package footprint is distinct from Android OS core storage."
                            }
                            AppTypeFilter.USER -> {
                                if (uiState.isUsageAccessGranted) "User Apps: Installed apps and user updates in /data/app • Enhanced stats active."
                                else "User Apps: Installed apps in /data/app • Data/Cache sandboxed by OS."
                            }
                            AppTypeFilter.ALL -> {
                                "${uiState.report.freshnessLabel} • Inaccessible measurements marked Unavailable (never 0 B)."
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.typeFilter == AppTypeFilter.SYSTEM) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Inspect OS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { viewModel.toggleSystemStorageDetails(true) }
                        )
                    } else if (!uiState.isUsageAccessGranted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            }
                        )
                    }
                }
            }

            // Batch Export Progress Card
            AnimatedVisibility(visible = uiState.isBatchExporting) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Exporting Selected APKs...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (uiState.batchExportPercent > 0) {
                                Text(
                                    text = "${uiState.batchExportPercent}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (uiState.batchExportPercent > 0) {
                            LinearProgressIndicator(
                                progress = { uiState.batchExportPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.batchExportProgress ?: "Preparing selected applications...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelBatchExport() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // Content List or Loading State
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (uiState.filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) {
                                    "No applications found matching '${uiState.searchQuery}'"
                                } else {
                                    "No applications found in this category"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.filteredApps,
                            key = { it.packageName }
                        ) { app ->
                            val isSelected = uiState.selectedPackageNames.contains(app.packageName)
                            InstalledAppItem(
                                app = app,
                                showPackageName = uiState.showPackageNames,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        SelectionHaptics.playSelectionFeedback(context, view)
                                        viewModel.toggleAppSelection(app.packageName)
                                    } else {
                                        viewModel.selectAppForDetails(app)
                                    }
                                },
                                onLongClick = {
                                    SelectionHaptics.playLongPressFeedback(context, view)
                                    viewModel.toggleAppSelection(app.packageName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // App Details Bottom Sheet
    if (uiState.selectedAppForDetails != null) {
        AppDetailsBottomSheet(
            app = uiState.selectedAppForDetails!!,
            onDismiss = { viewModel.selectAppForDetails(null) },
            onCopied = { viewModel.emitMessage(it) },
            onOpenedSettings = { viewModel.onOpenedSettings() },
            onRefresh = { viewModel.loadApps(forceRefresh = true) },
            onShareApk = { app ->
                viewModel.selectAppForDetails(null)
                viewModel.selectAppForShare(app)
            }
        )
    }

    // Share APK Bottom Sheet
    if (uiState.selectedAppForShare != null) {
        ShareApkBottomSheet(
            app = uiState.selectedAppForShare!!,
            onDismiss = { viewModel.selectAppForShare(null) }
        )
    }

    // System Storage Details Bottom Sheet
    if (uiState.showSystemStorageDetails) {
        SystemStorageDetailsBottomSheet(
            breakdown = uiState.systemStorageBreakdown,
            onDismiss = { viewModel.toggleSystemStorageDetails(false) },
            onViewSystemApps = {
                viewModel.toggleSystemStorageDetails(false)
                viewModel.setTypeFilter(AppTypeFilter.SYSTEM)
            }
        )
    }

    // Sort Bottom Sheet
    if (showSortSheet) {
        InstalledAppsSortBottomSheet(
            currentSortOrder = uiState.sortOrder,
            onSortSelected = { viewModel.setSortOrder(it) },
            onDismiss = { showSortSheet = false }
        )
    }
}
