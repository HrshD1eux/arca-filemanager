package com.Hrshd1eux.filemanager.feature.lock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.lock.manager.AppLockManager
import com.Hrshd1eux.filemanager.core.lock.model.AppLockMode
import com.Hrshd1eux.filemanager.core.lock.model.AppLockSettings
import com.Hrshd1eux.filemanager.core.lock.model.AppLockTimeout
import com.Hrshd1eux.filemanager.core.lock.model.BiometricCapabilityStatus

@Composable
fun AppLockSettingsDialog(
    appLockManager: AppLockManager,
    settings: AppLockSettings,
    onDismiss: () -> Unit
) {
    val biometricStatus = remember { appLockManager.biometricCoordinator.checkBiometricCapability() }
    val isBiometricsAvailable = biometricStatus == BiometricCapabilityStatus.READY

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showDisableConfirmDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "App Lock & Biometrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Status Header
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isConfigured) VaultEmerald.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (settings.isConfigured) Icons.Default.Shield else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (settings.isConfigured) VaultEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (settings.isConfigured) "App Lock Active" else "App Lock Disabled",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (settings.isConfigured) settings.mode.displayName else "Configure a PIN to protect ARCA when opened",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!settings.isConfigured) {
                    TextButton(
                        onClick = { showPinSetupDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set Up Master PIN")
                    }
                } else {
                    // Biometric Unlock Switch (if supported)
                    if (isBiometricsAvailable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newMode = if (settings.isBiometricsActive) AppLockMode.PIN else AppLockMode.BIOMETRIC_WITH_PIN
                                    appLockManager.updateSettings(settings.copy(mode = newMode))
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Unlock with Biometrics",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Use fingerprint or face recognition (PIN remains root fallback)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.isBiometricsActive,
                                onCheckedChange = { isChecked ->
                                    val newMode = if (isChecked) AppLockMode.BIOMETRIC_WITH_PIN else AppLockMode.PIN
                                    appLockManager.updateSettings(settings.copy(mode = newMode))
                                }
                            )
                        }
                    }

                    // Lock on Screen-Off
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                appLockManager.updateSettings(settings.copy(lockOnScreenOff = !settings.lockOnScreenOff))
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lock When Screen Turns Off",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Locks immediately when device screen is turned off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.lockOnScreenOff,
                            onCheckedChange = { isChecked ->
                                appLockManager.updateSettings(settings.copy(lockOnScreenOff = isChecked))
                            }
                        )
                    }

                    HorizontalDivider()

                    // Auto-Lock Timeout
                    Text(
                        text = "Auto-Lock Timeout",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    for (timeout in AppLockTimeout.values()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appLockManager.updateSettings(settings.copy(timeout = timeout))
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.timeout == timeout,
                                onClick = { appLockManager.updateSettings(settings.copy(timeout = timeout)) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timeout.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showPinSetupDialog = true }) {
                            Text("Change PIN")
                        }
                        TextButton(onClick = { showDisableConfirmDialog = true }) {
                            Text("Disable Lock", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    if (showPinSetupDialog) {
        PinSetupModal(
            isChangingPin = settings.isConfigured,
            onPinConfirmed = { newPin, enableBiometrics ->
                appLockManager.setPin(newPin, enableBiometrics)
                showPinSetupDialog = false
            },
            onDismiss = { showPinSetupDialog = false }
        )
    }

    if (showDisableConfirmDialog) {
        DisableLockModal(
            onConfirmDisable = { currentPin ->
                val success = appLockManager.disableAppLock(currentPin)
                if (success) {
                    showDisableConfirmDialog = false
                }
                success
            },
            onDismiss = { showDisableConfirmDialog = false }
        )
    }
}

@Composable
private fun PinSetupModal(
    isChangingPin: Boolean,
    onPinConfirmed: (pin: String, enableBiometrics: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var enableBiometrics by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isChangingPin) "Change PIN" else "Set Up Master PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter a 4 to 8 digit numeric PIN. This PIN is your root cryptographic fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("New PIN (4-8 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pin.length < 4) {
                        error = "PIN must be at least 4 digits."
                    } else if (pin != confirmPin) {
                        error = "PINs do not match."
                    } else {
                        onPinConfirmed(pin, enableBiometrics)
                    }
                }
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DisableLockModal(
    onConfirmDisable: (currentPin: String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable App Lock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your current PIN to confirm disabling App Lock.",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("Current PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val success = onConfirmDisable(pin)
                    if (!success) {
                        error = "Incorrect PIN."
                    }
                }
            ) {
                Text("Disable", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
