package com.Hrshd1eux.filemanager.feature.lock.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.lock.manager.AppLockManager
import com.Hrshd1eux.filemanager.core.lock.model.AppLockSettings
import com.Hrshd1eux.filemanager.core.lock.model.PinUnlockResult
import kotlinx.coroutines.delay

@Composable
fun AppLockOverlay(
    appLockManager: AppLockManager,
    settings: AppLockSettings,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutSeconds by remember { mutableStateOf(settings.remainingLockoutSeconds()) }

    LaunchedEffect(settings.lockoutUntilTimestamp) {
        while (true) {
            val remaining = settings.remainingLockoutSeconds()
            lockoutSeconds = remaining
            if (remaining <= 0) break
            delay(1000L)
        }
    }

    val isLockedOut = lockoutSeconds > 0
    val targetPinLength = settings.pinLength

    fun onDigitPress(digit: String) {
        if (isLockedOut) return
        if (enteredPin.length < targetPinLength) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null

            if (newPin.length == targetPinLength) {
                when (val result = appLockManager.unlockWithPin(newPin)) {
                    is PinUnlockResult.Success -> {
                        enteredPin = ""
                        errorMessage = null
                    }
                    is PinUnlockResult.InvalidPin -> {
                        enteredPin = ""
                        errorMessage = "Incorrect PIN. ${result.attemptsRemainingBeforeLockout} attempts remaining."
                    }
                    is PinUnlockResult.LockedOut -> {
                        enteredPin = ""
                        lockoutSeconds = result.remainingSeconds
                        errorMessage = "Too many failed attempts. Locked out for ${result.remainingSeconds}s."
                    }
                }
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Lock Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "App Locked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "ARCA App Lock",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isLockedOut) "Authentication Locked" else "Enter PIN to access files",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // PIN Dots Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until targetPinLength) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error or Lockout Message
            AnimatedVisibility(visible = errorMessage != null || isLockedOut) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLockedOut) "Locked out. Try again in ${lockoutSeconds}s" else (errorMessage ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Numeric Keypad (1 to 9, Biometrics/Blank, 0, Backspace)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        for (digit in row) {
                            KeypadButton(
                                text = digit,
                                enabled = !isLockedOut,
                                onClick = { onDigitPress(digit) }
                            )
                        }
                    }
                }

                // Bottom row: Biometric, 0, Backspace
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (settings.isBiometricsActive && !isLockedOut) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { appLockManager.unlockWithBiometrics() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Unlock",
                                tint = VaultEmerald,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(64.dp))
                    }

                    KeypadButton(
                        text = "0",
                        enabled = !isLockedOut,
                        onClick = { onDigitPress("0") }
                    )

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enteredPin.isNotEmpty()) { onBackspace() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = if (enteredPin.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.6f else 0.2f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
