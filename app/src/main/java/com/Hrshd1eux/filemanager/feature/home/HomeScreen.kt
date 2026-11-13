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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
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
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
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
    onNavigateToAbout: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToFtpServer: () -> Unit = {},
    onNavigateToArcaDrop: () -> Unit = {},
    onNavigateToPreview: (filePath: String, categoryName: String?, directoryPath: String?) -> Unit = { _, _, _ -> },
    onNavigateToArchiveViewer: (archivePath: String, title: String) -> Unit = { _, _ -> },
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToRecent: () -> Unit = {},
    onNavigateToNetwork: () -> Unit = {},
    modifier: Modifier = Modifier,
    onNavigateToPrivacy: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showMenu by remember { mutableStateOf(false) }

    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val storageBreakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val lockSettings by viewModel.lockSettings.collectAsStateWithLifecycle()
    val firstRunExplanationCompleted by viewModel.firstRunExplanationCompleted.collectAsStateWithLifecycle()
    val storageShortcuts by viewModel.storageShortcuts.collectAsStateWithLifecycle()

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
                                text = { Text(stringResource(R.string.menu_backup_profiles)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Backup, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_arca_drop)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToArcaDrop()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_transfer_pc)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Devices, contentDescription = null, tint = VaultEmerald)
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigateToFtpServer()
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
                    title = if (hasAllFilesAccess) stringResource(R.string.home_storage_title) else stringResource(R.string.home_storage_limited),
                    hasAllFilesAccess = hasAllFilesAccess,
                    onGrantPermissionClick = launchPermissionRequest,
                    onCardClick = onNavigateToStorageAnalyzer
                )
            }

            // 2. Quick Navigation Group: Favorites, Recent, Private Vault, Network, Internal Storage
            item {
                HomeQuickNavGroup(
                    favoriteCount = favorites.size,
                    recentCount = recents.size,
                    showVault = !lockSettings.isVaultHidden || !viewModel.isVaultConfigured,
                    onFavoritesClick = onNavigateToFavorites,
                    onRecentsClick = onNavigateToRecent,
                    onVaultClick = onNavigateToVault,
                    onNetworkClick = onNavigateToNetwork,
                    onInternalStorageClick = {
                        onNavigateToBrowser(android.os.Environment.getExternalStorageDirectory().absolutePath, context.getString(R.string.nav_internal_storage))
                    }
                )
            }

            // 3. Categories Grid: Images, Videos, Audio, Documents, Downloads, Archives, Apps & APKs, Trash
            item {
                HomeCategoriesGrid(
                    onCategoryClick = onNavigateToCategory,
                    onAppsClick = { onNavigateToCategory(CategoryFilter.APKS) },
                    onTrashClick = onNavigateToTrash
                )
            }

            // 4. Storage Folders: Download, Documents, Pictures, Movies + Custom Folders
            item {
                HomeStorageFoldersList(
                    shortcuts = storageShortcuts,
                    onFolderClick = { path, title ->
                        onNavigateToBrowser(path, title)
                    },
                    onAddShortcut = { name, path ->
                        viewModel.addStorageShortcut(name, path)
                    },
                    onEditShortcut = { id, name, path ->
                        viewModel.updateStorageShortcut(id, name, path)
                    },
                    onDeleteShortcut = { id ->
                        viewModel.removeStorageShortcut(id)
                    },
                    onResetDefaults = {
                        viewModel.resetStorageShortcuts()
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
            text = stringResource(R.string.home_privacy_banner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}
