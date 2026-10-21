package com.Hrshd1eux.filemanager.feature.home

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.permission.StoragePermissionManager
import com.Hrshd1eux.filemanager.feature.home.components.FavoritesSheet
import com.Hrshd1eux.filemanager.feature.home.components.FirstRunStorageExplanationDialog
import com.Hrshd1eux.filemanager.feature.home.components.HomeCategoriesGrid
import com.Hrshd1eux.filemanager.feature.home.components.HomeQuickNavGroup
import com.Hrshd1eux.filemanager.feature.home.components.HomeStorageCard
import com.Hrshd1eux.filemanager.feature.home.components.HomeStorageFoldersList
import com.Hrshd1eux.filemanager.feature.home.components.NetworkStatusDialog
import com.Hrshd1eux.filemanager.feature.home.components.RecentsSheet
import com.Hrshd1eux.filemanager.feature.network.components.NetworkConnectionsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBrowser: (initialPath: String?, title: String) -> Unit,
    onNavigateToSearch: (rootPath: String?) -> Unit,
    onNavigateToCategory: (CategoryFilter) -> Unit = {},
    onNavigateToStorageAnalyzer: () -> Unit = {},
    onNavigateToInstalledApps: () -> Unit = {},
    onNavigateToLargeFiles: (rootPath: String?) -> Unit = {},
    onNavigateToDuplicateFinder: (rootPath: String?) -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToNetworkBrowser: (connectionId: String, title: String) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPrivacy: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showMenu by remember { mutableStateOf(false) }
    var showFavoritesSheet by remember { mutableStateOf(false) }
    var showRecentsSheet by remember { mutableStateOf(false) }
    var showNetworkSheet by remember { mutableStateOf(false) }
    var showClearRecentsDialog by remember { mutableStateOf(false) }

    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val storageBreakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val lockSettings by viewModel.lockSettings.collectAsStateWithLifecycle()
    val firstRunExplanationCompleted by viewModel.firstRunExplanationCompleted.collectAsStateWithLifecycle()

    var hasAllFilesAccess by remember {
        mutableStateOf(StoragePermissionManager.hasAllFilesAccess(context))
    }

    val allFilesPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = StoragePermissionManager.hasAllFilesAccess(context)
        hasAllFilesAccess = granted
        if (granted) {
            viewModel.refreshStorageBreakdown()
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = StoragePermissionManager.hasAllFilesAccess(context)
        hasAllFilesAccess = granted
        if (granted) {
            viewModel.refreshStorageBreakdown()
        }
    }

    val launchPermissionRequest: () -> Unit = {
        if (StoragePermissionManager.isAllFilesAccessSupported()) {
            allFilesPermissionLauncher.launch(StoragePermissionManager.createManageAllFilesIntent(context))
        } else {
            val legacy = StoragePermissionManager.getRequiredLegacyPermissions()
            if (legacy.isNotEmpty()) {
                legacyPermissionLauncher.launch(legacy)
            } else {
                allFilesPermissionLauncher.launch(StoragePermissionManager.createManageAllFilesIntent(context))
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = StoragePermissionManager.hasAllFilesAccess(context)
                if (granted != hasAllFilesAccess) {
                    hasAllFilesAccess = granted
                    if (granted) {
                        viewModel.refreshStorageBreakdown()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.home_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { onNavigateToSearch(null) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Storage Analyzer") },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToStorageAnalyzer()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Installed Apps") },
                                leadingIcon = {
                                    Icon(Icons.Default.Apps, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToInstalledApps()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Large Files") },
                                leadingIcon = {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToLargeFiles(null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate Finder") },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToDuplicateFinder(null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Backup Profiles") },
                                leadingIcon = {
                                    Icon(Icons.Default.Backup, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.settings_title)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.about_title)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToAbout()
                                }
                            )
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Storage Overview Card (Google Files style: Storage 86.4 GB / 128 GB with Progress bar)
            item {
                Spacer(modifier = Modifier.height(2.dp))
                HomeStorageCard(
                    breakdown = storageBreakdown,
                    title = if (hasAllFilesAccess) "Storage" else "Storage • Limited Access",
                    hasAllFilesAccess = hasAllFilesAccess,
                    onGrantPermissionClick = launchPermissionRequest,
                    onCardClick = onNavigateToStorageAnalyzer
                )
            }

            // 2. Quick Navigation Group: Favorites, Recent, Private Vault, Network
            item {
                HomeQuickNavGroup(
                    favoriteCount = favorites.size,
                    recentCount = recents.size,
                    showVault = !lockSettings.isVaultHidden,
                    onFavoritesClick = { showFavoritesSheet = true },
                    onRecentsClick = { showRecentsSheet = true },
                    onVaultClick = onNavigateToVault,
                    onNetworkClick = { showNetworkSheet = true }
                )
            }

            // 3. Categories Grid: Images, Videos, Audio, Documents, Downloads, Archives, APKs, Other
            item {
                HomeCategoriesGrid(
                    onCategoryClick = onNavigateToCategory
                )
            }

            // 4. Storage Standard Folders: Download, Documents, Pictures, Movies
            item {
                HomeStorageFoldersList(
                    onFolderClick = { path, title ->
                        onNavigateToBrowser(path, title)
                    }
                )
            }

            // 5. Offline Status Footer Banner
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HomeOfflineStatusBanner(onNavigateToPrivacy = onNavigateToPrivacy)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Favorites BottomSheet
    if (showFavoritesSheet) {
        FavoritesSheet(
            favorites = favorites,
            onItemClick = { item ->
                showFavoritesSheet = false
                onNavigateToBrowser(item.path, item.displayName)
            },
            onRemoveFavorite = { path ->
                viewModel.removeFavorite(path)
            },
            onDismiss = { showFavoritesSheet = false }
        )
    }

    // Recents BottomSheet
    if (showRecentsSheet) {
        RecentsSheet(
            recents = recents,
            onItemClick = { item ->
                showRecentsSheet = false
                onNavigateToBrowser(item.path, item.displayName)
            },
            onRemoveRecent = { path ->
                viewModel.removeRecent(path)
            },
            onClearAll = {
                showClearRecentsDialog = true
            },
            onDismiss = { showRecentsSheet = false }
        )
    }

    // Network Connections Sheet
    if (showNetworkSheet) {
        NetworkConnectionsSheet(
            onDismiss = { showNetworkSheet = false },
            onConnectToRemote = { config ->
                showNetworkSheet = false
                onNavigateToNetworkBrowser(config.id, config.name)
            }
        )
    }

    // Clear Recents Confirmation Dialog
    if (showClearRecentsDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecentsDialog = false },
            title = { Text("Clear Recent History?") },
            text = { Text("All items will be removed from your recent access history. Your actual files will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRecents()
                        showClearRecentsDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecentsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // First-Run Storage Explanation Dialog (shown once when storage permission is not yet granted)
    if (!hasAllFilesAccess && !firstRunExplanationCompleted) {
        FirstRunStorageExplanationDialog(
            onGrantAccess = {
                viewModel.setFirstRunExplanationCompleted(true)
                launchPermissionRequest()
            },
            onContinueWithLimitedAccess = {
                viewModel.setFirstRunExplanationCompleted(true)
            },
            onReadPrivacyStatement = onNavigateToPrivacy
        )
    }
}

@Composable
private fun HomeOfflineStatusBanner(
    onNavigateToPrivacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onNavigateToPrivacy)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = VaultEmerald,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "ARCA • 100% Offline & Private (View Privacy Statement)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}
