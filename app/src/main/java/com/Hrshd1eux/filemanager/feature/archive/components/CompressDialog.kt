package com.Hrshd1eux.filemanager.feature.archive.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionLevelPreset
import com.Hrshd1eux.filemanager.core.archive.model.CompressionMethodOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionProfile
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import java.util.zip.Deflater

@Composable
fun CompressDialog(
    sources: List<StorageNode>,
    defaultName: String,
    onConfirmCreate: (archiveName: String, compressionLevel: Int, password: CharArray?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFormat by remember { mutableStateOf(ArchiveFormatOption.ZIP) }
    var selectedPreset by remember { mutableStateOf(CompressionLevelPreset.NORMAL) }
    var customLevel by remember { mutableIntStateOf(6) }
    var selectedMethod by remember { mutableStateOf(CompressionMethodOption.DEFAULT) }
    var selectedThreads by remember { mutableIntStateOf(0) } // 0 = Auto
    var isSolid by remember { mutableStateOf(true) }
    var dictionarySizeMb by remember { mutableIntStateOf(32) }
    var showAdvanced by remember { mutableStateOf(false) }

    // Encryption & Password States
    var isEncryptEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var rawName by remember {
        val baseName = defaultName
            .removeSuffix(".zip")
            .removeSuffix(".7z")
            .removeSuffix(".tar.gz")
            .removeSuffix(".tar.xz")
            .removeSuffix(".tar.bz2")
            .removeSuffix(".tar")
        mutableStateOf(baseName)
    }

    val totalBytes = remember(sources) { sources.sumOf { it.size } }
    val isNameValid = rawName.isNotBlank() && !rawName.contains('/') && !rawName.contains('\\')
    val ext = selectedFormat.defaultExtension

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = ZipOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Compression",
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
                // Selection Summary Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${sources.size} item(s) selected • ${FileFormatter.formatFileSize(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Format Selector (ZIP, 7Z, TAR, TAR.GZ, TAR.XZ, TAR.BZ2)
                Text(
                    text = "Format",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatChip(ArchiveFormatOption.ZIP, selectedFormat) { selectedFormat = it }
                        FormatChip(ArchiveFormatOption.SEVEN_Z, selectedFormat) { selectedFormat = it }
                        FormatChip(ArchiveFormatOption.TAR, selectedFormat) { selectedFormat = it }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatChip(ArchiveFormatOption.TAR_GZ, selectedFormat) { selectedFormat = it }
                        FormatChip(ArchiveFormatOption.TAR_XZ, selectedFormat) { selectedFormat = it }
                        FormatChip(ArchiveFormatOption.TAR_BZ2, selectedFormat) { selectedFormat = it }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Compression Level Preset Selector
                Text(
                    text = "Level",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LevelPresetChip("Store", CompressionLevelPreset.STORE, selectedPreset) {
                        selectedPreset = it
                        customLevel = it.level
                    }
                    LevelPresetChip("Fast", CompressionLevelPreset.FAST, selectedPreset) {
                        selectedPreset = it
                        customLevel = it.level
                    }
                    LevelPresetChip("Normal", CompressionLevelPreset.NORMAL, selectedPreset) {
                        selectedPreset = it
                        customLevel = it.level
                    }
                    LevelPresetChip("Max", CompressionLevelPreset.MAXIMUM, selectedPreset) {
                        selectedPreset = it
                        customLevel = it.level
                    }
                    LevelPresetChip("Ultra", CompressionLevelPreset.ULTRA, selectedPreset) {
                        selectedPreset = it
                        customLevel = it.level
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val isPasswordValid = !isEncryptEnabled || (password.isNotEmpty() && password == confirmPassword)
                val canCreate = isNameValid && isPasswordValid

                // Archive Name Input Field
                OutlinedTextField(
                    value = rawName,
                    onValueChange = { rawName = it },
                    label = { Text("Archive Name") },
                    suffix = { Text(ext, color = ZipOrange, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    isError = !isNameValid && rawName.isNotBlank(),
                    supportingText = {
                        if (!isNameValid && rawName.isNotBlank()) {
                            Text("Name cannot contain / or \\")
                        }
                    },
                    trailingIcon = {
                        if (rawName.isNotEmpty()) {
                            IconButton(onClick = { rawName = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canCreate) {
                                val finalName = if (rawName.endsWith(ext, ignoreCase = true)) rawName else "$rawName$ext"
                                val passChars = if (isEncryptEnabled && password.isNotEmpty()) password.toCharArray() else null
                                password = ""
                                confirmPassword = ""
                                onConfirmCreate(finalName, customLevel, passChars)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Encryption & Password Section Card
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isEncryptEnabled = !isEncryptEnabled },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Encrypt Archive",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = isEncryptEnabled,
                                onCheckedChange = { isEncryptEnabled = it }
                            )
                        }

                        AnimatedVisibility(visible = isEncryptEnabled) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    placeholder = { Text("••••••••••") },
                                    singleLine = true,
                                    isError = isEncryptEnabled && password.isEmpty() && confirmPassword.isNotEmpty(),
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val passwordsMismatch = isEncryptEnabled && confirmPassword.isNotEmpty() && password != confirmPassword
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    label = { Text("Confirm") },
                                    placeholder = { Text("••••••••••") },
                                    singleLine = true,
                                    isError = passwordsMismatch,
                                    supportingText = if (passwordsMismatch) {
                                        { Text("Passwords do not match") }
                                    } else null,
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showPassword = !showPassword }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = showPassword,
                                        onCheckedChange = { showPassword = it }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Show password",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Advanced Options Toggle Card
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvanced = !showAdvanced },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Advanced",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showAdvanced) "Collapse" else "Expand"
                            )
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(10.dp))

                                // Fine-grained Compression Level (0 - 9)
                                Text(
                                    text = "Compression level: $customLevel",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Slider(
                                    value = customLevel.toFloat(),
                                    onValueChange = { customLevel = it.toInt() },
                                    valueRange = 0f..9f,
                                    steps = 8,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Compression Method
                                Text(
                                    text = "Method",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    MethodChip(CompressionMethodOption.DEFAULT, selectedMethod) { selectedMethod = it }
                                    MethodChip(CompressionMethodOption.DEFLATE, selectedMethod) { selectedMethod = it }
                                    MethodChip(CompressionMethodOption.LZMA2, selectedMethod) { selectedMethod = it }
                                    MethodChip(CompressionMethodOption.COPY, selectedMethod) { selectedMethod = it }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Threads Selector
                                Text(
                                    text = "Threads",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ThreadChip("Auto", 0, selectedThreads) { selectedThreads = it }
                                    ThreadChip("1", 1, selectedThreads) { selectedThreads = it }
                                    ThreadChip("2", 2, selectedThreads) { selectedThreads = it }
                                    ThreadChip("4", 4, selectedThreads) { selectedThreads = it }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Solid Archive Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Solid Archive",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Switch(
                                        checked = isSolid,
                                        onCheckedChange = { isSolid = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isPasswordValid = !isEncryptEnabled || (password.isNotEmpty() && password == confirmPassword)
            val canCreate = isNameValid && isPasswordValid

            Button(
                onClick = {
                    if (canCreate) {
                        val finalName = if (rawName.endsWith(ext, ignoreCase = true)) rawName else "$rawName$ext"
                        val passChars = if (isEncryptEnabled && password.isNotEmpty()) password.toCharArray() else null
                        password = ""
                        confirmPassword = ""
                        onConfirmCreate(finalName, customLevel, passChars)
                    }
                },
                enabled = canCreate,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    password = ""
                    confirmPassword = ""
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

@Composable
private fun FormatChip(
    option: ArchiveFormatOption,
    selected: ArchiveFormatOption,
    onSelect: (ArchiveFormatOption) -> Unit
) {
    FilterChip(
        selected = option == selected,
        onClick = { onSelect(option) },
        label = { Text(option.displayName, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ZipOrange.copy(alpha = 0.15f),
            selectedLabelColor = ZipOrange
        )
    )
}

@Composable
private fun LevelPresetChip(
    label: String,
    preset: CompressionLevelPreset,
    selectedPreset: CompressionLevelPreset,
    onSelect: (CompressionLevelPreset) -> Unit
) {
    FilterChip(
        selected = selectedPreset == preset,
        onClick = { onSelect(preset) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun MethodChip(
    method: CompressionMethodOption,
    selectedMethod: CompressionMethodOption,
    onSelect: (CompressionMethodOption) -> Unit
) {
    FilterChip(
        selected = selectedMethod == method,
        onClick = { onSelect(method) },
        label = { Text(method.displayName, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}

@Composable
private fun ThreadChip(
    label: String,
    threads: Int,
    selectedThreads: Int,
    onSelect: (Int) -> Unit
) {
    FilterChip(
        selected = selectedThreads == threads,
        onClick = { onSelect(threads) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}

@Composable
private fun DictChip(
    label: String,
    sizeMb: Int,
    selectedSizeMb: Int,
    onSelect: (Int) -> Unit
) {
    FilterChip(
        selected = selectedSizeMb == sizeMb,
        onClick = { onSelect(sizeMb) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}
