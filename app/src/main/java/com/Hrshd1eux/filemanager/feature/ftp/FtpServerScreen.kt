package com.Hrshd1eux.filemanager.feature.ftp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import com.Hrshd1eux.filemanager.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.ftp.server.FtpActivityLog
import com.Hrshd1eux.filemanager.core.ftp.server.FtpServerConfig
import com.Hrshd1eux.filemanager.core.storage.permission.StoragePermissionManager
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.foundation.layout.PaddingValues
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpServerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FtpServerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFolderPicker by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSameNetworkDialog by remember { mutableStateOf(false) }
    var showStoragePermissionDialog by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshNetwork(context)
    }

    LaunchedEffect(state.isRunning, state.errorMessage) {
        isStarting = false
    }

    LaunchedEffect(isStarting) {
        if (isStarting) {
            kotlinx.coroutines.delay(4000)
            isStarting = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ftp_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNetwork(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.ftp_guide_title))
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
            // 1. Primary Server Status & Control Card
            item {
                Spacer(modifier = Modifier.height(2.dp))
                FtpServerStatusCard(
                    isRunning = state.isRunning,
                    isStarting = isStarting,
                    ftpUrl = state.ftpUrl,
                    ipAddress = state.ipAddress,
                    port = state.port,
                    isNetworkAvailable = state.isNetworkAvailable,
                    clientsCount = state.connectedClientsCount,
                    bytesSent = state.bytesSent,
                    bytesReceived = state.bytesReceived,
                    onStartClick = {
                        if (!isStarting && !state.isRunning) {
                            val hasPermission = StoragePermissionManager.hasAllFilesAccess(context)
                            if (!hasPermission) {
                                showStoragePermissionDialog = true
                            } else {
                                showSameNetworkDialog = true
                            }
                        }
                    },
                    onStopClick = { viewModel.stopServer(context) },
                    onCopyUrl = {
                        val clip = ClipData.newPlainText("FTP URL", state.ftpUrl)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.ftp_address_copied_toast), Toast.LENGTH_SHORT).show()
                    },
                    onShareUrl = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.ftp_share_intent_title))
                            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.ftp_share_intent_text, state.ftpUrl))
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.ftp_share_address)))
                    }
                )
            }

            // Error banner if server failed to start
            if (state.errorMessage != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = state.errorMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            if (state.errorMessage?.contains("in use", ignoreCase = true) == true) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        viewModel.updatePort(state.port + 1)
                                        viewModel.startServer(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.ftp_try_port_btn, state.port + 1))
                                }
                            }
                        }
                    }
                }
            }

            // Storage Permission Warning Banner if All Files Access is not granted
            if (!state.isRunning && !StoragePermissionManager.hasAllFilesAccess(context)) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ftp_storage_limited_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.ftp_storage_limited_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(StoragePermissionManager.createManageAllFilesIntent(context))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(R.string.ftp_grant_button), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // 2. Configuration & Access Settings Card
            item {
                FtpServerSettingsCard(
                    config = state.config,
                    isServerRunning = state.isRunning,
                    onUpdatePort = { viewModel.updatePort(it) },
                    onUpdateUsername = { viewModel.updateUsername(it) },
                    onUpdatePassword = { viewModel.updatePassword(it) },
                    onUpdateAnonymous = { viewModel.updateAnonymous(it) },
                    onSelectDefaultRoot = { viewModel.updateRootDirectory(FtpServerConfig.defaultRootPath) },
                    onPickCustomRoot = { showFolderPicker = true },
                    onUpdateReadOnly = { viewModel.updateReadOnly(it) },
                    onUpdateShareApk = { viewModel.updateShareApk(it) }
                )
            }

            // 3. Quick Connection Guide for Desktop & Mobile
            item {
                FtpConnectionGuideCard(ftpUrl = state.ftpUrl)
            }

            // 4. Live Activity Log (Active during server session)
            if (state.activityLogs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.ftp_recent_activity),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(state.activityLogs, key = { it.id }) { log ->
                    FtpActivityLogRow(log = log)
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Folder Picker Dialog for selecting custom root directory
    if (showFolderPicker) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = stringResource(R.string.ftp_select_accessible_folder),
            confirmButtonText = stringResource(R.string.ftp_share_this_folder),
            onDestinationSelected = { destination, _ ->
                viewModel.updateRootDirectory(destination.path)
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    // Storage Permission Dialog
    if (showStoragePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showStoragePermissionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.FolderShared,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.ftp_all_files_required_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.ftp_all_files_required_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStoragePermissionDialog = false
                        runCatching {
                            context.startActivity(StoragePermissionManager.createManageAllFilesIntent(context))
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_grant_permission))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStoragePermissionDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Same Network Confirmation Dialog
    if (showSameNetworkDialog) {
        AlertDialog(
            onDismissRequest = { showSameNetworkDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.ftp_same_network_title))
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.ftp_same_network_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSameNetworkDialog = false
                        isStarting = true
                        viewModel.startServer(context)
                    }
                ) {
                    Text(stringResource(R.string.ftp_start_server))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSameNetworkDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Connection Help Dialog
    if (showHelpDialog) {
        FtpConnectionHelpDialog(
            ftpUrl = state.ftpUrl,
            onDismiss = { showHelpDialog = false }
        )
    }
}

@Composable
private fun FtpServerStatusCard(
    isRunning: Boolean,
    isStarting: Boolean = false,
    ftpUrl: String,
    ipAddress: String?,
    port: Int,
    isNetworkAvailable: Boolean,
    clientsCount: Int,
    bytesSent: Long,
    bytesReceived: Long,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onCopyUrl: () -> Unit,
    onShareUrl: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                VaultEmerald.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isRunning) VaultEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(if (isRunning) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(if (isRunning) VaultEmerald else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isRunning) stringResource(R.string.ftp_status_active) else stringResource(R.string.ftp_status_stopped),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) VaultEmerald else MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isNetworkAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isNetworkAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isNetworkAvailable) stringResource(R.string.ftp_network_wifi) else stringResource(R.string.ftp_network_none),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNetworkAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (isRunning) {
                // Large Prominent URL Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, VaultEmerald.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.ftp_address_label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ftpUrl,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = VaultEmerald
                    )
                }

                // Action Buttons: Copy URL & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyUrl,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ftp_copy_address))
                    }

                    OutlinedButton(
                        onClick = onShareUrl,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_share))
                    }
                }

                // Live Session Telemetry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$clientsCount",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ftp_connected_clients),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = FileFormatter.formatFileSize(bytesSent),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ftp_data_sent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = FileFormatter.formatFileSize(bytesReceived),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ftp_data_received),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stop Server Button
                Button(
                    onClick = onStopClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ftp_stop_server), fontWeight = FontWeight.Bold)
                }
            } else {
                // Not running explanation
                Text(
                    text = stringResource(R.string.ftp_intro_card_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isNetworkAvailable) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.ftp_connect_prompt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Start Server Button
                Button(
                    onClick = onStartClick,
                    enabled = isNetworkAvailable && !isStarting && !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultEmerald),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isStarting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ftp_starting_server), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ftp_start_server), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FtpServerSettingsCard(
    config: FtpServerConfig,
    isServerRunning: Boolean,
    onUpdatePort: (Int) -> Unit,
    onUpdateUsername: (String) -> Unit,
    onUpdatePassword: (String) -> Unit,
    onUpdateAnonymous: (Boolean) -> Unit,
    onSelectDefaultRoot: () -> Unit,
    onPickCustomRoot: () -> Unit,
    onUpdateReadOnly: (Boolean) -> Unit,
    onUpdateShareApk: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.ftp_config_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Section 1: Folder Access & Permissions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ftp_accessible_folder_header),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = FolderYellow,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = config.rootDisplayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = config.rootDirectoryPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (!isServerRunning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onSelectDefaultRoot,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.ftp_internal_storage), fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = onPickCustomRoot,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.ftp_choose_folder), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Full Read & Write Access Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = VaultEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ftp_full_access_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ftp_full_access_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Section 2: Authentication
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ftp_auth_header),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ftp_anonymous_login),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ftp_anonymous_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = config.isAnonymousAllowed,
                        onCheckedChange = { if (!isServerRunning) onUpdateAnonymous(it) },
                        enabled = !isServerRunning
                    )
                }

                if (!config.isAnonymousAllowed) {
                    OutlinedTextField(
                        value = config.username,
                        onValueChange = onUpdateUsername,
                        label = { Text(stringResource(R.string.ftp_username)) },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                        enabled = !isServerRunning,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = config.password,
                        onValueChange = onUpdatePassword,
                        label = { Text(stringResource(R.string.ftp_password)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) stringResource(R.string.action_hide_password) else stringResource(R.string.action_show_password)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        enabled = !isServerRunning,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Section 3: Port
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ftp_port_header),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = config.port.toString(),
                    onValueChange = { str ->
                        val p = str.filter { it.isDigit() }.toIntOrNull()
                        if (p != null) onUpdatePort(p)
                    },
                    label = { Text(stringResource(R.string.ftp_port_number)) },
                    supportingText = { Text(stringResource(R.string.ftp_port_default_desc)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isServerRunning,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Section 4: APK Sharing Integration
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.ftp_apk_header),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = VaultEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.ftp_expose_apks_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.ftp_expose_apks_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = config.shareApkEnabled,
                        onCheckedChange = { if (!isServerRunning) onUpdateShareApk(it) },
                        enabled = !isServerRunning
                    )
                }
            }
        }
    }
}

@Composable
private fun FtpConnectionGuideCard(
    ftpUrl: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ftp_guide_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (expanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ftp_guide_wifi_req),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ftp_guide_windows_title),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.ftp_guide_windows_steps, ftpUrl),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ftp_guide_mac_title),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.ftp_guide_mac_steps, ftpUrl),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ftp_guide_mobile_title),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.ftp_guide_mobile_steps, ftpUrl),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FtpActivityLogRow(
    log: FtpActivityLog,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = log.formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FtpConnectionHelpDialog(
    ftpUrl: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.ftp_how_to_connect), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.ftp_guide_wifi_req),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Address: $ftpUrl",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = VaultEmerald
                )
                Text(
                    text = "• Windows: Open File Explorer, paste the address in the top bar, and press Enter.\n• Mac: In Finder, press Cmd + K, enter the address, and click Connect.\n• Other Mobile: Open any browser or file manager and enter the address.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_got_it))
            }
        }
    )
}
