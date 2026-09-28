package com.Hrshd1eux.filemanager.feature.network.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import kotlinx.coroutines.launch

/**
 * Dialog for adding a new remote network connection.
 * Prioritizes SFTP over unencrypted FTP.
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

    var showPassword by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<NetworkConnectionTestResult?>(null) }
    val scope = rememberCoroutineScope()

    val isFormValid = name.isNotBlank() && host.isNotBlank() && (portText.toIntOrNull() ?: 0) in 1..65535 &&
            when (authType) {
                NetworkAuthType.PASSWORD -> username.isNotBlank() && password.isNotEmpty()
                NetworkAuthType.KEY_PAIR -> username.isNotBlank() && privateKey.isNotBlank()
                else -> true
            }

    fun buildConfig(): NetworkServerConfig {
        return NetworkServerConfig(
            name = name.trim(),
            protocol = selectedProtocol,
            host = host.trim(),
            port = portText.toIntOrNull() ?: selectedProtocol.defaultPort,
            authType = authType,
            username = username.trim().ifEmpty { null },
            password = if (authType == NetworkAuthType.PASSWORD) password else null,
            privateKey = if (authType == NetworkAuthType.KEY_PAIR) privateKey.trim() else null,
            passphrase = if (authType == NetworkAuthType.KEY_PAIR && passphrase.isNotEmpty()) passphrase else null,
            basePath = basePath.trim().ifEmpty { "/" }
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
                // Protocol Priority Selector (SFTP Prioritized)
                Text(
                    text = "Protocol",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. SFTP (Prioritized)
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
                            modifier = Modifier.padding(10.dp),
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
                                text = "Encrypted SSH (Port 22)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. FTP (Legacy LAN)
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
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFE6A700),
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
                                text = "Plaintext LAN (Port 21)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        onValueChange = { host = it },
                        label = { Text("Host / IP Address") },
                        placeholder = { Text("192.168.1.20") },
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

                // Authentication Method
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
                }

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
                } else if (authType == NetworkAuthType.KEY_PAIR) {
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
                            val config = buildConfig()
                            val provider = SftpNetworkProvider(config)
                            val res = provider.testConnection()
                            testResult = res.getOrElse {
                                NetworkConnectionTestResult(
                                    isSuccess = false,
                                    latencyMs = 0L,
                                    errorMessage = it.message ?: "Failed"
                                )
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
