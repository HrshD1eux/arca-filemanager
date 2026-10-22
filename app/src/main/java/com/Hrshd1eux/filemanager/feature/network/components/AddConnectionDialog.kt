package com.Hrshd1eux.filemanager.feature.network.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import com.Hrshd1eux.filemanager.core.network.util.WebDavProtocolHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionTestResult
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for adding a new remote network connection.
 * Prioritizes SFTP and FTPS, with explicit warnings when unencrypted FTP is selected.
 */
@Composable
fun AddConnectionDialog(
    onDismiss: () -> Unit,
    onSaveConnection: (NetworkServerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedProtocol by remember { mutableStateOf(NetworkProtocol.SFTP) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("22") }
    var authType by remember { mutableStateOf(NetworkAuthType.PASSWORD) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var basePath by remember { mutableStateOf("/") }
    var shareName by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }

    // Protocol Specific Options
    var isPassiveMode by remember { mutableStateOf(true) }
    var isImplicitTls by remember { mutableStateOf(false) }
    var allowSelfSignedCert by remember { mutableStateOf(false) }
    var isAnonymousFtp by remember { mutableStateOf(false) }

    var showPassword by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<NetworkConnectionTestResult?>(null) }
    val scope = rememberCoroutineScope()

    val isFormValid = name.isNotBlank() && host.isNotBlank() && (portText.toIntOrNull() ?: 0) in 1..65535 &&
            when {
                selectedProtocol == NetworkProtocol.FTP && isAnonymousFtp -> true
                selectedProtocol == NetworkProtocol.WEBDAV && authType == NetworkAuthType.ANONYMOUS -> true
                selectedProtocol == NetworkProtocol.WEBDAV && authType == NetworkAuthType.TOKEN -> password.isNotBlank()
                selectedProtocol == NetworkProtocol.SMB && authType == NetworkAuthType.ANONYMOUS -> true
                authType == NetworkAuthType.PASSWORD -> username.isNotBlank() && password.isNotEmpty()
                authType == NetworkAuthType.KEY_PAIR -> username.isNotBlank() && privateKey.isNotBlank()
                else -> true
            }

    fun buildConfig(): NetworkServerConfig {
        val effectiveAuth = when {
            selectedProtocol == NetworkProtocol.FTP && isAnonymousFtp -> NetworkAuthType.ANONYMOUS
            selectedProtocol == NetworkProtocol.WEBDAV && authType == NetworkAuthType.ANONYMOUS -> NetworkAuthType.ANONYMOUS
            selectedProtocol == NetworkProtocol.WEBDAV && authType == NetworkAuthType.TOKEN -> NetworkAuthType.TOKEN
            selectedProtocol == NetworkProtocol.SMB && authType == NetworkAuthType.ANONYMOUS -> NetworkAuthType.ANONYMOUS
            else -> authType
        }
        val effectiveUser = when {
            selectedProtocol == NetworkProtocol.FTP && isAnonymousFtp -> "anonymous"
            selectedProtocol == NetworkProtocol.WEBDAV && effectiveAuth == NetworkAuthType.TOKEN -> username.trim().ifEmpty { "token" }
            selectedProtocol == NetworkProtocol.SMB && effectiveAuth == NetworkAuthType.ANONYMOUS -> null
            else -> username.trim().ifEmpty { null }
        }

        return NetworkServerConfig(
            name = name.trim(),
            protocol = selectedProtocol,
            host = host.trim(),
            port = portText.toIntOrNull() ?: selectedProtocol.defaultPort,
            authType = effectiveAuth,
            username = effectiveUser,
            password = if (effectiveAuth == NetworkAuthType.PASSWORD || effectiveAuth == NetworkAuthType.TOKEN) password else null,
            privateKey = if (effectiveAuth == NetworkAuthType.KEY_PAIR) privateKey.trim() else null,
            passphrase = if (effectiveAuth == NetworkAuthType.KEY_PAIR && passphrase.isNotEmpty()) passphrase else null,
            domain = if (selectedProtocol == NetworkProtocol.SMB) domain.trim().ifEmpty { null } else null,
            shareName = if (selectedProtocol == NetworkProtocol.SMB) shareName.trim().ifEmpty { null } else null,
            basePath = basePath.trim().ifEmpty { "/" },
            isPassiveMode = isPassiveMode,
            isImplicitTls = isImplicitTls,
            allowSelfSignedCert = allowSelfSignedCert
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Connection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Protocol Priority Selector (SFTP / FTPS / FTP)
                Text(
                    text = "Protocol",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. SFTP (Default & Prioritized)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedProtocol = NetworkProtocol.SFTP
                                portText = "22"
                                if (authType != NetworkAuthType.PASSWORD && authType != NetworkAuthType.KEY_PAIR) {
                                    authType = NetworkAuthType.PASSWORD
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProtocol == NetworkProtocol.SFTP) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = VaultEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SFTP",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "SSH (22)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. WebDAV (Universal Protocol)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedProtocol = NetworkProtocol.WEBDAV
                                isImplicitTls = true
                                portText = "443"
                                if (authType == NetworkAuthType.KEY_PAIR || authType == NetworkAuthType.DOMAIN_NTLM) {
                                    authType = NetworkAuthType.PASSWORD
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProtocol == NetworkProtocol.WEBDAV) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isImplicitTls) VaultEmerald else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "WebDAV",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "HTTP(S)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. SMB (Windows / Samba)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedProtocol = NetworkProtocol.SMB
                                portText = "445"
                                if (authType == NetworkAuthType.KEY_PAIR || authType == NetworkAuthType.TOKEN) {
                                    authType = NetworkAuthType.PASSWORD
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProtocol == NetworkProtocol.SMB) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SMB",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "Samba (445)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 4. FTPS (TLS Encrypted)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedProtocol = NetworkProtocol.FTPS
                                portText = if (isImplicitTls) "990" else "21"
                                authType = NetworkAuthType.PASSWORD
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProtocol == NetworkProtocol.FTPS) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = VaultEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "FTPS",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "TLS (21/990)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 5. FTP (Plaintext - Warned)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedProtocol = NetworkProtocol.FTP
                                portText = "21"
                                authType = NetworkAuthType.PASSWORD
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProtocol == NetworkProtocol.FTP) {
                                Color(0xFFFFECB3)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "FTP",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "Plain (21)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBF360C)
                            )
                        }
                    }
                }

                // EXPLICIT WARNING: Whenever FTP is selected
                if (selectedProtocol == NetworkProtocol.FTP) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "⚠ FTP is not encrypted.",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFBF360C)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Use SFTP or FTPS when possible. Passwords and file transfers travel in cleartext over the network.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5D4037)
                                )
                            }
                        }
                    }
                }

                // WebDAV Universal Protocol Banner & Security Warning
                if (selectedProtocol == NetworkProtocol.WEBDAV) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Universal WebDAV (RFC 4918)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Protocol-based storage: Nextcloud, ownCloud, Synology, TrueNAS, or any standard WebDAV endpoint. No vendor SDKs or account tracking.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // SMB Samba Banner
                if (selectedProtocol == NetworkProtocol.SMB) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Windows / Samba SMB",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Dialect SMB 2.0.2 to 3.1.1. Insecure SMB1 (CIFS) is strictly rejected.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // FTPS Encryption Information Badge
                if (selectedProtocol == NetworkProtocol.FTPS) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VaultEmerald.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, VaultEmerald.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = VaultEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FTPS encrypts control and data channels using TLS (PROT P).",
                                style = MaterialTheme.typography.labelSmall,
                                color = VaultEmerald,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Connection Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Connection Name") },
                    placeholder = { Text("e.g. NAS or Home Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Host & Port
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { input ->
                            if (selectedProtocol == NetworkProtocol.WEBDAV && (input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true) || input.startsWith("webdav://", ignoreCase = true) || input.startsWith("webdavs://", ignoreCase = true) || input.contains("/"))) {
                                val parsed = WebDavProtocolHelper.parseWebDavUrl(input)
                                if (parsed != null) {
                                    host = parsed.host
                                    portText = parsed.port.toString()
                                    isImplicitTls = parsed.isHttps
                                    if (parsed.basePath != "/") basePath = parsed.basePath
                                    if (parsed.username != null) username = parsed.username
                                    if (parsed.password != null) password = parsed.password
                                } else {
                                    host = input
                                }
                            } else {
                                host = input
                            }
                        },
                        label = { Text(if (selectedProtocol == NetworkProtocol.WEBDAV) "Host / WebDAV URL" else "Host / IP Address") },
                        placeholder = { Text(if (selectedProtocol == NetworkProtocol.WEBDAV) "cloud.example.com or URL" else "192.168.1.20") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(0.3f)
                    )
                }

                // WebDAV Presets & Options
                if (selectedProtocol == NetworkProtocol.WEBDAV) {
                    Text(
                        text = "Server Presets",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WebDavProtocolHelper.PRESETS.forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    basePath = preset.defaultPath
                                    portText = preset.defaultPort.toString()
                                    isImplicitTls = preset.defaultPort == 443 || preset.defaultPort == 5006
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = preset.displayName.substringBefore(" "),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Text(
                        text = "Transport Security",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                isImplicitTls = true
                                if (portText == "80") portText = "443"
                            }
                        ) {
                            RadioButton(
                                selected = isImplicitTls,
                                onClick = {
                                    isImplicitTls = true
                                    if (portText == "80") portText = "443"
                                }
                            )
                            Text("HTTPS (:443)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                isImplicitTls = false
                                if (portText == "443") portText = "80"
                            }
                        ) {
                            RadioButton(
                                selected = !isImplicitTls,
                                onClick = {
                                    isImplicitTls = false
                                    if (portText == "443") portText = "80"
                                }
                            )
                            Text("HTTP (:80)", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // WebDAV Cleartext Warning
                    if (!isImplicitTls || portText == "80") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF3E0),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "⚠ HTTP WebDAV is not encrypted.",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFBF360C)
                                    )
                                    Text(
                                        text = "Passwords, auth tokens, and files travel in cleartext over the network. Use HTTPS whenever possible.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF5D4037)
                                    )
                                }
                            }
                        }
                    }

                    // Self-signed certificate checkbox for WebDAV
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { allowSelfSignedCert = !allowSelfSignedCert }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = allowSelfSignedCert,
                            onCheckedChange = { allowSelfSignedCert = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("Allow self-signed certificates", style = MaterialTheme.typography.bodyMedium)
                            Text("Bypasses standard CA chain verification for home NAS / private servers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // SMB Specific Options
                if (selectedProtocol == NetworkProtocol.SMB) {
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text("Share Name") },
                        placeholder = { Text("e.g. Shared, Public, or Storage") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Domain / Workgroup (Optional)") },
                        placeholder = { Text("WORKGROUP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // FTPS Mode Selector (Explicit AUTH TLS vs Implicit TLS)
                if (selectedProtocol == NetworkProtocol.FTPS) {
                    Text(
                        text = "TLS Mode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                isImplicitTls = false
                                portText = "21"
                            }
                        ) {
                            RadioButton(
                                selected = !isImplicitTls,
                                onClick = {
                                    isImplicitTls = false
                                    portText = "21"
                                }
                            )
                            Text("Explicit (AUTH TLS :21)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                isImplicitTls = true
                                portText = "990"
                            }
                        ) {
                            RadioButton(
                                selected = isImplicitTls,
                                onClick = {
                                    isImplicitTls = true
                                    portText = "990"
                                }
                            )
                            Text("Implicit (:990)", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Self-signed certificate checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { allowSelfSignedCert = !allowSelfSignedCert }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = allowSelfSignedCert,
                            onCheckedChange = { allowSelfSignedCert = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("Allow self-signed certificates", style = MaterialTheme.typography.bodyMedium)
                            Text("Bypasses standard CA chain verification for home NAS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // FTP Specific Options (Passive Mode & Anonymous)
                if (selectedProtocol == NetworkProtocol.FTP) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isPassiveMode = !isPassiveMode }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = isPassiveMode,
                            onCheckedChange = { isPassiveMode = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("Passive Mode (PASV)", style = MaterialTheme.typography.bodyMedium)
                            Text("Recommended for connections behind NAT routers and firewalls", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isAnonymousFtp = !isAnonymousFtp }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = isAnonymousFtp,
                            onCheckedChange = { isAnonymousFtp = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Anonymous Login", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Authentication Method Selector
                if (selectedProtocol == NetworkProtocol.SFTP) {
                    Text(
                        text = "Authentication Method",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { authType = NetworkAuthType.PASSWORD }
                        ) {
                            RadioButton(
                                selected = authType == NetworkAuthType.PASSWORD,
                                onClick = { authType = NetworkAuthType.PASSWORD }
                            )
                            Text("Password", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { authType = NetworkAuthType.KEY_PAIR }
                        ) {
                            RadioButton(
                                selected = authType == NetworkAuthType.KEY_PAIR,
                                onClick = { authType = NetworkAuthType.KEY_PAIR }
                            )
                            Text("SSH Key", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (selectedProtocol == NetworkProtocol.WEBDAV) {
                    Text(
                        text = "Authentication Method",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { authType = NetworkAuthType.PASSWORD }
                        ) {
                            RadioButton(
                                selected = authType == NetworkAuthType.PASSWORD,
                                onClick = { authType = NetworkAuthType.PASSWORD }
                            )
                            Text("Password", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { authType = NetworkAuthType.TOKEN }
                        ) {
                            RadioButton(
                                selected = authType == NetworkAuthType.TOKEN,
                                onClick = { authType = NetworkAuthType.TOKEN }
                            )
                            Text("Token", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { authType = NetworkAuthType.ANONYMOUS }
                        ) {
                            RadioButton(
                                selected = authType == NetworkAuthType.ANONYMOUS,
                                onClick = { authType = NetworkAuthType.ANONYMOUS }
                            )
                            Text("Public", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (selectedProtocol == NetworkProtocol.SMB) {
                    Text(
                        text = "Authentication Method",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { authType = NetworkAuthType.PASSWORD }
                        ) {
                            RadioButton(
                                selected = authType == NetworkAuthType.PASSWORD,
                                onClick = { authType = NetworkAuthType.PASSWORD }
                            )
                            Text("User Credentials", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { authType = NetworkAuthType.ANONYMOUS }
                        ) {
                            RadioButton(
                                selected = authType == NetworkAuthType.ANONYMOUS,
                                onClick = { authType = NetworkAuthType.ANONYMOUS }
                            )
                            Text("Guest / Anonymous", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Authentication Input Fields
                val isAnonymous = (selectedProtocol == NetworkProtocol.FTP && isAnonymousFtp) ||
                        (selectedProtocol == NetworkProtocol.WEBDAV && authType == NetworkAuthType.ANONYMOUS) ||
                        (selectedProtocol == NetworkProtocol.SMB && authType == NetworkAuthType.ANONYMOUS)

                if (!isAnonymous) {
                    if (selectedProtocol == NetworkProtocol.WEBDAV && authType == NetworkAuthType.TOKEN) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username (Optional)") },
                            placeholder = { Text("token") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Bearer Token / App Password") },
                            placeholder = { Text("e.g. Nextcloud app password") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Username
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Password or SSH Key Fields
                        if (authType == NetworkAuthType.PASSWORD) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (authType == NetworkAuthType.KEY_PAIR && selectedProtocol == NetworkProtocol.SFTP) {
                            OutlinedTextField(
                                value = privateKey,
                                onValueChange = { privateKey = it },
                                label = { Text("Private Key (PEM / OpenSSH)") },
                                placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") },
                                maxLines = 4,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text("Passphrase (Optional)") },
                                singleLine = true,
                                visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                        Icon(
                                            imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Base Path
                OutlinedTextField(
                    value = basePath,
                    onValueChange = { basePath = it },
                    label = { Text("Remote Starting Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Pre-flight Test Status
                AnimatedVisibility(visible = testResult != null || isTesting) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isTesting -> MaterialTheme.colorScheme.surfaceVariant
                            testResult?.isSuccess == true -> VaultEmerald.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing connection...", style = MaterialTheme.typography.bodySmall)
                            } else if (testResult?.isSuccess == true) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VaultEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connected in ${testResult?.latencyMs}ms (${testResult?.serverBanner ?: "OK"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = VaultEmerald
                                )
                            } else {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = testResult?.errorMessage ?: "Connection failed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Security Note
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Credentials stored securely with hardware AES-256-GCM encryption.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = null
                            testResult = withContext(Dispatchers.IO) {
                                runCatching {
                                    val config = buildConfig()
                                    config.validate()
                                    val provider = NetworkProviderRegistry.createProvider(config)
                                    val res = provider.testConnection()
                                    res.getOrElse {
                                        NetworkConnectionTestResult(
                                            isSuccess = false,
                                            latencyMs = 0L,
                                            errorMessage = it.message ?: "Failed"
                                        )
                                    }
                                }.getOrElse { error ->
                                    NetworkConnectionTestResult(
                                        isSuccess = false,
                                        latencyMs = 0L,
                                        errorMessage = error.localizedMessage ?: error.message ?: "Connection error"
                                    )
                                }
                            }
                            isTesting = false
                        }
                    },
                    enabled = host.isNotBlank() && !isTesting
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test")
                }

                Button(
                    onClick = {
                        onSaveConnection(buildConfig())
                        onDismiss()
                    },
                    enabled = isFormValid
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}
