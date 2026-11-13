package com.Hrshd1eux.filemanager.feature.drop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.drop.qr.QrCodeGenerator
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcaDropScreen(
    initialReceiveDirectory: String? = null,
    autoStart: Boolean = false,
    onNavigateBack: () -> Unit,
    onNavigateToDirectory: (path: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArcaDropViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showReceivePicker by remember { mutableStateOf(false) }
    var showShareTextDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialReceiveDirectory) {
        if (!initialReceiveDirectory.isNullOrBlank()) {
            viewModel.setReceiveDirectory(initialReceiveDirectory)
        }
        viewModel.refreshLanIp()
        if (autoStart && !state.isRunning) {
            viewModel.startServer(context)
        }
    }

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPickedUris(context, uris)
        }
    }

    val qrBitmap = remember(state.qrUrl) {
        state.qrUrl?.let { url -> QrCodeGenerator.generateBitmap(url, sizePx = 560) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ARCA Drop",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isRunning) "Wi-Fi Browser Portal Active" else "Local Wi-Fi Browser Transfer",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isRunning) VaultEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    IconButton(onClick = { viewModel.refreshLanIp() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh network address"
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
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Server Status + Primary Start/Stop + QR Code Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        0.5.dp,
                        if (state.isRunning) VaultEmerald.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        (if (state.isRunning) VaultEmerald else MaterialTheme.colorScheme.primary)
                                            .copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = if (state.isRunning) VaultEmerald else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.isRunning) "Transfer Portal Running" else "Ready to Start ARCA Drop",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (state.isRunning) {
                                        "${state.activeConnections} active • Sent ${FileFormatter.formatFileSize(state.bytesSent)} • Received ${FileFormatter.formatFileSize(state.bytesReceived)}"
                                    } else {
                                        "Send & receive files, folders, and text with any PC/phone browser over Wi-Fi."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (state.isRunning) {
                            if (state.ipAddress == null) {
                                HotspotGuidanceBanner()
                            } else {
                                // QR + Link + PIN display
                                if (qrBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White)
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "ARCA Drop QR Code",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(200.dp)
                                        )
                                    }
                                }

                                val displayUrl = state.baseUrl ?: ""
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Browser URL",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = displayUrl,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = {
                                            copyToClipboard(context, "ARCA Drop URL", state.qrUrl ?: displayUrl)
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Session PIN",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = state.pin,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        IconButton(onClick = {
                                            copyToClipboard(context, "ARCA Drop PIN", state.pin)
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy PIN")
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (state.isRunning) viewModel.stopServer(context)
                                else viewModel.startServer(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.isRunning) "Stop Sharing" else "Start ARCA Drop")
                        }

                        Text(
                            text = "Note: Local browser links use plaintext HTTP inside your LAN protected by your 6-digit PIN. Use home Wi-Fi or your Mobile Hotspot rather than untrusted public networks.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Wi-Fi Troubleshooting & Hotspot Fix Card
            item {
                ArcaDropTroubleshootingCard()
            }

            // 3. Upload Destination Folder Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save Received Files To",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.receiveDirectoryPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { showReceivePicker = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Change save folder")
                        }
                    }
                }
            }

            // 3. Shared Files & Shared Text Queue
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Shared to Browser (${state.sharedItems.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Add files or text anytime — live browser reloads pick them up",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (state.sharedItems.isNotEmpty() || !state.sharedText.isNullOrBlank()) {
                                IconButton(onClick = { viewModel.clearSharedQueue() }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear shared queue")
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { pickFilesLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Files", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { showShareTextDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (state.sharedText.isNullOrBlank()) "Share Text" else "Edit Text", maxLines = 1)
                            }
                        }

                        if (!state.sharedText.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "Shared snippet: ${state.sharedText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                )
                            }
                        }

                        state.sharedItems.take(25).forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.relativePath,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = FileFormatter.formatFileSize(item.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeSharedItem(item.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Remove item",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        if (state.sharedItems.size > 25) {
                            Text(
                                text = "+ ${state.sharedItems.size - 25} more queued items",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 4. Received Files & Texts from Browser
            if (state.receivedFiles.isNotEmpty() || state.receivedTexts.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Received on Phone",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { onNavigateToDirectory(state.receiveDirectoryPath) }) {
                                    Text("Open Folder")
                                }
                            }

                            state.receivedTexts.forEach { snippet ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = snippet.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(onClick = {
                                            copyToClipboard(context, "Received Text", snippet.text)
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy received text")
                                        }
                                    }
                                }
                            }

                            state.receivedFiles.take(15).forEach { received ->
                                Text(
                                    text = "• ${received.name} (${FileFormatter.formatFileSize(received.sizeBytes)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 5. Recent Activity Logs
            if (state.logs.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Transfer Log",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            state.logs.take(8).forEach { log ->
                                Text(
                                    text = "• ${log.message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (log.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReceivePicker) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Select Receive Folder",
            confirmButtonText = "Save Here",
            onDestinationSelected = { node, _ ->
                showReceivePicker = false
                viewModel.setReceiveDirectory(node.path)
            },
            onDismiss = { showReceivePicker = false }
        )
    }

    if (showShareTextDialog) {
        var textInput by remember { mutableStateOf(state.sharedText.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showShareTextDialog = false },
            title = { Text("Share Text or URL", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Text snippet for connected browser") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setSharedText(textInput)
                    showShareTextDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ArcaDropTroubleshootingCard(
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Having trouble connecting on Wi-Fi?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (expanded) "Tap to collapse tips" else "Tap for Wi-Fi AP isolation & hotspot fix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TroubleshootingItem(
                        title = "1. Router AP Client Isolation (Most Common)",
                        description = "Campus, hotel, office, and some home Wi-Fi routers block devices from communicating with each other for guest security." +
                            "\n\n💡 1-Tap Solution: Turn on Android's Mobile Hotspot on this phone and connect your computer/tablet directly to it. Transfers will be direct and fast, consuming ZERO cellular data."
                    )
                    TroubleshootingItem(
                        title = "2. Check Browser URL (HTTP vs HTTPS)",
                        description = "Make sure the browser URL begins with http:// and NOT https://. Modern browsers often autofill https://, which causes connection refused errors on local LAN IPs."
                    )
                    TroubleshootingItem(
                        title = "3. Wi-Fi Band & VPN",
                        description = "Ensure both devices are on the same Wi-Fi SSID/frequency (2.4 GHz vs 5 GHz). If a VPN is active on either phone or computer, temporarily pause it to allow direct LAN traffic."
                    )
                }
            }
        }
    }
}

@Composable
private fun TroubleshootingItem(
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HotspotGuidanceBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "No local Wi-Fi or Hotspot IP detected. Connect to the same Wi-Fi router as your computer, or turn on Mobile Hotspot and connect your computer to it — ARCA Drop updates automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
