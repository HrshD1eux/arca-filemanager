package com.Hrshd1eux.filemanager.feature.network.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepository
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepositoryImpl
import kotlinx.coroutines.launch

/**
 * BottomSheet UI for managing network connections matching the exact specification:
 *
 * Network
 *
 * + Add connection
 *
 * 🔒 NAS
 *    192.168.1.20
 *
 * 🔒 Server
 *    example.local
 *
 * Credentials stored securely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkConnectionsSheet(
    onDismiss: () -> Unit,
    onConnectToRemote: (NetworkServerConfig) -> Unit,
    modifier: Modifier = Modifier,
    repository: NetworkConnectionRepository? = null
) {
    val context = LocalContext.current
    val actualRepository = repository ?: remember(context) {
        NetworkConnectionRepositoryImpl(context.applicationContext)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val connections by actualRepository.connections.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var connectingId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Network",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Clear, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // + Add connection Action Button
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add connection",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status message
            AnimatedVisibility(visible = statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Connection List
            if (connections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No connections saved.\nTap '+ Add connection' to configure an SFTP server or NAS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(connections, key = { it.id }) { item ->
                        val isConnecting = connectingId == item.id
                        val isUnencrypted = item.protocol == NetworkProtocol.FTP ||
                                (item.protocol == NetworkProtocol.WEBDAV && !item.isImplicitTls && item.effectivePort != 443)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isConnecting) {
                                    scope.launch {
                                        connectingId = item.id
                                        statusMessage = "Connecting to ${item.name} (${item.host})..."
                                        val fullConfig = actualRepository.populateWithSecrets(item)
                                        val provider = com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry.createProvider(fullConfig)
                                        val connectResult = provider.connect()
                                        if (connectResult.isSuccess) {
                                            statusMessage = "Connected to ${item.name}!"
                                            onConnectToRemote(fullConfig)
                                        } else {
                                            statusMessage = "Connection failed: ${connectResult.exceptionOrNull()?.message}"
                                        }
                                        connectingId = null
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUnencrypted) {
                                        Color(0xFFFFF8E1).copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Lock / Warning Icon
                                        if (isUnencrypted) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Unencrypted Connection",
                                                tint = Color(0xFFE65100),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Encrypted Connection",
                                                tint = VaultEmerald,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            // Connection Name
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            // Host Address & Security note
                                            Text(
                                                text = if (isUnencrypted) "${item.host} • ⚠ Plaintext" else item.host,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isUnencrypted) Color(0xFFBF360C) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isConnecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        // Protocol tag
                                        val tagColor = if (isUnencrypted) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = tagColor.copy(alpha = 0.12f),
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = when (item.protocol) {
                                                    NetworkProtocol.SFTP -> "SFTP (SSH)"
                                                    NetworkProtocol.FTPS -> "FTPS (TLS)"
                                                    NetworkProtocol.FTP -> "⚠ FTP"
                                                    NetworkProtocol.SMB -> "SMB (Samba)"
                                                    NetworkProtocol.WEBDAV -> if (!isUnencrypted) "WebDAV (HTTPS)" else "⚠ WebDAV (HTTP)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = tagColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    actualRepository.deleteConnection(item.id)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Connection",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secure Storage Notice
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = VaultEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Credentials stored securely with hardware AES-256-GCM encryption. Zero plaintext persistence.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddConnectionDialog(
            onDismiss = { showAddDialog = false },
            onSaveConnection = { newConfig ->
                scope.launch {
                    actualRepository.saveConnection(newConfig)
                }
            }
        )
    }
}
