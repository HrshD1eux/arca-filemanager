package com.Hrshd1eux.filemanager.feature.archive.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSecurityEvaluator
import com.Hrshd1eux.filemanager.core.archive.security.PasswordStrength
import kotlin.math.roundToInt

/**
 * Dedicated cryptographic Password Generator dialog matching Milestone 1.2.15 specifications:
 *
 * Generate Password
 *
 * Length
 * [ 24 ]
 *
 * ☑ Letters
 * ☑ Numbers
 * ☑ Symbols
 *
 * [ Generate ]
 *
 * SECURITY GUARANTEE:
 * Generated passwords are NEVER stored automatically (no SharedPreferences, no DataStore, no DB, no logs).
 * Password state exists only in volatile Compose memory and is cleared upon dismissal.
 */
@Composable
fun PasswordGeneratorDialog(
    initialLength: Int = 24,
    onDismiss: () -> Unit,
    onUsePassword: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var lengthSlider by remember { mutableFloatStateOf(initialLength.coerceIn(8, 64).toFloat()) }
    val length = lengthSlider.roundToInt()

    var includeLetters by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }

    val hasActivePool = includeLetters || includeNumbers || includeSymbols

    var generatedPassword by remember {
        mutableStateOf(
            ArchiveSecurityEvaluator.generateSecurePassword(
                length = initialLength.coerceIn(8, 64),
                includeLetters = true,
                includeNumbers = true,
                includeSymbols = true
            )
        )
    }

    var showPassword by remember { mutableStateOf(true) }
    var copyFeedback by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val passwordStrength = remember(generatedPassword) {
        ArchiveSecurityEvaluator.evaluatePassword(generatedPassword)
    }

    fun generate() {
        if (hasActivePool) {
            generatedPassword = ArchiveSecurityEvaluator.generateSecurePassword(
                length = length,
                includeLetters = includeLetters,
                includeNumbers = includeNumbers,
                includeSymbols = includeSymbols
            )
            copyFeedback = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            generatedPassword = ""
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Generate Password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Generated Password Display Card
                OutlinedCard(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showPassword) generatedPassword else "•".repeat(generatedPassword.length),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Strength Meter
                        val strengthColor = when (passwordStrength.strength) {
                            PasswordStrength.VERY_WEAK, PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                            PasswordStrength.FAIR -> Color(0xFFE6A700)
                            PasswordStrength.STRONG, PasswordStrength.VERY_STRONG -> Color(0xFF2E7D32)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${passwordStrength.label} (${generatedPassword.length} chars)",
                                style = MaterialTheme.typography.labelSmall,
                                color = strengthColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { passwordStrength.score },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = strengthColor,
                            trackColor = strengthColor.copy(alpha = 0.2f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Length Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Length",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "[ $length ]",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Slider(
                    value = lengthSlider,
                    onValueChange = { lengthSlider = it },
                    valueRange = 8f..64f,
                    steps = 55,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Character Class Checkboxes
                // 1. Letters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (!includeLetters || includeNumbers || includeSymbols) {
                                includeLetters = !includeLetters
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeLetters,
                        onCheckedChange = { checked ->
                            if (checked || includeNumbers || includeSymbols) {
                                includeLetters = checked
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Letters",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 2. Numbers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (!includeNumbers || includeLetters || includeSymbols) {
                                includeNumbers = !includeNumbers
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeNumbers,
                        onCheckedChange = { checked ->
                            if (checked || includeLetters || includeSymbols) {
                                includeNumbers = checked
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Numbers",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 3. Symbols
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (!includeSymbols || includeLetters || includeNumbers) {
                                includeSymbols = !includeSymbols
                            }
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeSymbols,
                        onCheckedChange = { checked ->
                            if (checked || includeLetters || includeNumbers) {
                                includeSymbols = checked
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Symbols",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (!hasActivePool) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select at least one character class",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Generate Button
                Button(
                    onClick = { generate() },
                    enabled = hasActivePool,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Zero-Persistence Privacy Guarantee
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
                            text = "Generated passwords are never stored automatically. Copy or save securely before closing.",
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
                        clipboardManager.setText(AnnotatedString(generatedPassword))
                        copyFeedback = true
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (copyFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (copyFeedback) "Copied!" else "Copy")
                }

                if (onUsePassword != null) {
                    Button(
                        onClick = {
                            val pass = generatedPassword
                            generatedPassword = ""
                            onUsePassword(pass)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Use Password")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    generatedPassword = ""
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Close")
            }
        },
        modifier = modifier
    )
}
