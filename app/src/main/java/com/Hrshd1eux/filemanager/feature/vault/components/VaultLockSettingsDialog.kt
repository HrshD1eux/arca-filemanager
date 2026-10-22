package com.Hrshd1eux.filemanager.feature.vault.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.vault.lock.VaultAutoLockTimeout
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import com.Hrshd1eux.filemanager.core.vault.stealth.VaultStealthSecurity

@Composable
fun VaultLockSettingsDialog(
    currentSettings: VaultLockSettings,
    isBiometricSupported: Boolean = false,
    onSaveSettings: (VaultLockSettings) -> Unit,
    onManualLock: () -> Unit,
    onChangePassword: () -> Unit = {},
    onDeleteVault: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTimeout by remember { mutableStateOf(currentSettings.autoLockTimeout) }
    var lockOnBg by remember { mutableStateOf(currentSettings.lockOnBackground) }
    var lockOnScreenOff by remember { mutableStateOf(currentSettings.lockOnScreenOff) }
    var isVaultHidden by remember { mutableStateOf(currentSettings.isVaultHidden) }
    var isBiometricEnabled by remember { mutableStateOf(currentSettings.isBiometricEnabled) }

    val initialCode = remember(currentSettings) {
        if (currentSettings.encryptedSecretCode.isNotBlank() && currentSettings.secretCodeSalt.isNotBlank()) {
            VaultStealthSecurity.decryptCode(currentSettings.encryptedSecretCode, currentSettings.secretCodeSalt)
        } else {
            VaultStealthSecurity.DEFAULT_TRIGGER_CODE
        }
    }
    var secretCodeInput by remember { mutableStateOf(initialCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = VaultEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vault Lock Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Auto-lock Timeout",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                VaultAutoLockTimeout.entries.forEach { timeout ->
                    val isSelected = selectedTimeout == timeout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTimeout = timeout }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedTimeout = timeout }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = timeout.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Security Triggers",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Lock on background toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { lockOnBg = !lockOnBg }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lock on background",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Lock when minimizing or switching apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lockOnBg,
                        onCheckedChange = { lockOnBg = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = VaultEmerald)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Lock on screen off toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { lockOnScreenOff = !lockOnScreenOff }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lock on screen off",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Lock immediately when device turns off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lockOnScreenOff,
                        onCheckedChange = { lockOnScreenOff = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = VaultEmerald)
                    )
                }

                if (isBiometricSupported) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isBiometricEnabled = !isBiometricEnabled }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric Unlock",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Unlock vault using enrolled fingerprint or face recognition",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { isBiometricEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = VaultEmerald)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Stealth Mode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Hide Vault toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isVaultHidden = !isVaultHidden }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Private Vault",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Hides vault from Home screen. Access via secret trigger code in search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isVaultHidden,
                        onCheckedChange = { isVaultHidden = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = VaultEmerald)
                    )
                }

                if (isVaultHidden) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secretCodeInput,
                        onValueChange = { secretCodeInput = it },
                        label = { Text("Secret Trigger Code") },
                        placeholder = { Text("e.g. #vault or 🔐") },
                        supportingText = {
                            Text("Type this code in global search to reveal the vault. Emojis and symbols supported. Encrypted with salted hash.")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Vault Management",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onChangePassword()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Master Password")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onDeleteVault()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Vault (Permanent)")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Manual Lock Action
                Button(
                    onClick = {
                        onDismiss()
                        onManualLock()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lock Vault Now", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val credentials = if (isVaultHidden) {
                        VaultStealthSecurity.createStealthCredentials(secretCodeInput)
                    } else {
                        null
                    }
                    val newSettings = VaultLockSettings(
                        autoLockTimeout = selectedTimeout,
                        lockOnBackground = lockOnBg,
                        lockOnScreenOff = lockOnScreenOff,
                        isVaultHidden = isVaultHidden,
                        secretCodeSalt = credentials?.saltHex ?: currentSettings.secretCodeSalt,
                        secretCodeHash = credentials?.hashHex ?: currentSettings.secretCodeHash,
                        encryptedSecretCode = credentials?.encryptedPayloadHex ?: currentSettings.encryptedSecretCode,
                        isBiometricEnabled = isBiometricEnabled
                    )
                    onSaveSettings(newSettings)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultEmerald)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
