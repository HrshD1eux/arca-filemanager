package com.Hrshd1eux.filemanager.feature.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import com.Hrshd1eux.filemanager.BuildConfig
import com.Hrshd1eux.filemanager.core.update.ui.AppUpdateDialog
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.lock.manager.AppLockManager
import com.Hrshd1eux.filemanager.core.storage.permission.StoragePermissionManager
import com.Hrshd1eux.filemanager.feature.lock.ui.AppLockSettingsDialog
import com.Hrshd1eux.filemanager.feature.settings.components.ArchiveSettingsDialog
import com.Hrshd1eux.filemanager.feature.settings.components.ThemeSelectionDialog
import com.Hrshd1eux.filemanager.feature.vault.components.VaultLockSettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSecurityCenter: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsStateWithLifecycle()
    val confirmDelete by viewModel.confirmDelete.collectAsStateWithLifecycle()
    val hapticFeedback by viewModel.hapticFeedback.collectAsStateWithLifecycle()
    val soundFeedback by viewModel.soundFeedback.collectAsStateWithLifecycle()
    val enabledArchiveFormats by viewModel.enabledArchiveFormats.collectAsStateWithLifecycle()
    val defaultArchiveFormat by viewModel.defaultArchiveFormat.collectAsStateWithLifecycle()
    val defaultCompressionLevel by viewModel.defaultCompressionLevel.collectAsStateWithLifecycle()
    val vaultLockSettings by viewModel.vaultLockSettings.collectAsStateWithLifecycle()
    val hasAllFilesAccess by viewModel.hasAllFilesAccess.collectAsStateWithLifecycle()
    val hasUsageAccess by viewModel.hasUsageAccess.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        viewModel.refreshPermissions(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }

    var showAppLockDialog by remember { mutableStateOf(false) }
    var showUsageAccessDialog by remember { mutableStateOf(false) }

    val appLockManager = remember { AppLockManager.getInstance(context) }
    val appLockSettings by appLockManager.settings.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

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
                        text = stringResource(id = R.string.settings_title),
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
            item {
                Spacer(modifier = Modifier.height(2.dp))
                SettingsCategoryHeader(title = "Appearance")
            }

            item {
                SettingsCard {
                    val themeSubtitle = buildString {
                        append(themeMode.displayName)
                        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            append(" • Dynamic colors")
                        }
                    }
                    SettingsRow(
                        title = "Theme",
                        subtitle = themeSubtitle,
                        icon = Icons.Default.Palette,
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            item {
                SettingsCategoryHeader(title = "Files & Storage")
            }

            item {
                SettingsCard {
                    SettingsToggleRow(
                        title = "Show hidden files",
                        subtitle = "Display files starting with a dot (.)",
                        icon = Icons.Default.Visibility,
                        checked = showHiddenFiles,
                        onCheckedChange = { viewModel.setShowHiddenFiles(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Confirm before deletion",
                        subtitle = "Ask for confirmation before deleting items",
                        icon = Icons.Default.DeleteOutline,
                        checked = confirmDelete,
                        onCheckedChange = { viewModel.setConfirmDelete(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Haptic feedback",
                        subtitle = "Vibrate when selecting files and items",
                        icon = Icons.Default.Vibration,
                        checked = hapticFeedback,
                        onCheckedChange = { viewModel.setHapticFeedback(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Sound feedback",
                        subtitle = "Play subtle tactile audio click when selecting files",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        checked = soundFeedback,
                        onCheckedChange = { viewModel.setSoundFeedback(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsRow(
                        title = "All Files Access",
                        subtitle = if (hasAllFilesAccess) "Granted • Full storage management active" else "Not granted • Tap to configure in system settings",
                        icon = Icons.Default.Storage,
                        onClick = {
                            runCatching {
                                context.startActivity(StoragePermissionManager.createManageAllFilesIntent(context))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsRow(
                        title = "Usage Access",
                        subtitle = if (hasUsageAccess) "Granted • Storage Analyzer app stats enabled" else "Optional • Required for app size breakdown",
                        icon = Icons.Default.Security,
                        onClick = {
                            if (!hasUsageAccess) {
                                showUsageAccessDialog = true
                            } else {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsCategoryHeader(title = "Archives & Security")
            }

            item {
                SettingsCard {
                    SettingsRow(
                        title = "Backup Profiles & Sync",
                        subtitle = "Safe Copy, Incremental, and Mirror sync policies",
                        icon = Icons.Default.Backup,
                        onClick = onNavigateToBackup
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsRow(
                        title = "Archive Formats",
                        subtitle = "Default: ${defaultArchiveFormat.displayName} • ${enabledArchiveFormats.size} format(s) enabled",
                        icon = Icons.Default.Archive,
                        onClick = { showArchiveDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsRow(
                        title = "Security Center",
                        subtitle = "Inspect verified security posture & technical claims",
                        icon = Icons.Default.Shield,
                        onClick = onNavigateToSecurityCenter
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsRow(
                        title = "App Lock & Biometrics",
                        subtitle = if (appLockSettings.isConfigured) "${appLockSettings.mode.displayName} active" else "Disabled • Configure PIN to lock app",
                        icon = Icons.Default.Fingerprint,
                        onClick = { showAppLockDialog = true }
                    )
                }
            }

            item {
                SettingsCategoryHeader(title = "About & Privacy")
            }

            item {
                SettingsCard {
                    SettingsRow(
                        title = "About ARCA",
                        subtitle = "Version, open-source licenses, and legal info",
                        icon = Icons.Default.Info,
                        onClick = onNavigateToAbout
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsRow(
                        title = "Privacy Statement",
                        subtitle = "Zero telemetry, zero ads, local-first",
                        icon = Icons.Default.Security,
                        onClick = onNavigateToPrivacy
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsRow(
                        title = "App Updates & Internet Disclosure",
                        subtitle = "Internet is strictly used for user-initiated remote storage (SFTP, FTP, SMB, WebDAV) and official GitHub release checks. Zero telemetry or user data is ever transmitted.",
                        icon = Icons.Default.Download,
                        onClick = onNavigateToAbout
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentThemeMode = themeMode,
            isDynamicColorEnabled = dynamicColor,
            onThemeModeSelected = { viewModel.setThemeMode(it) },
            onDynamicColorToggled = { viewModel.setDynamicColor(it) },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showArchiveDialog) {
        ArchiveSettingsDialog(
            enabledFormats = enabledArchiveFormats,
            defaultFormat = defaultArchiveFormat,
            defaultLevel = defaultCompressionLevel,
            onToggleFormat = { format, enabled -> viewModel.toggleArchiveFormat(format, enabled) },
            onSelectDefaultFormat = { viewModel.setDefaultArchiveFormat(it) },
            onSelectDefaultLevel = { viewModel.setDefaultCompressionLevel(it) },
            onDismiss = { showArchiveDialog = false }
        )
    }



    if (showAppLockDialog) {
        AppLockSettingsDialog(
            appLockManager = appLockManager,
            settings = appLockSettings,
            onDismiss = { showAppLockDialog = false }
        )
    }

    if (showUsageAccessDialog) {
        com.Hrshd1eux.filemanager.feature.cleaner.apps.components.UsageAccessExplanationDialog(
            onDismissRequest = { showUsageAccessDialog = false }
        )
    }
}

@Composable
private fun SettingsCategoryHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
