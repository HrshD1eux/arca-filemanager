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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSecurityEvaluator
import com.Hrshd1eux.filemanager.core.archive.security.PasswordStrength
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionDialogMode
import com.Hrshd1eux.filemanager.core.archive.model.CompressionLevelPreset
import com.Hrshd1eux.filemanager.core.archive.model.CompressionMethodOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionProfile
import com.Hrshd1eux.filemanager.core.archive.model.DictionarySizePreset
import com.Hrshd1eux.filemanager.core.archive.model.SplitSizePreset
import com.Hrshd1eux.filemanager.core.archive.model.ThreadCountPreset
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter

@Composable
fun CompressDialog(
    sources: List<StorageNode>,
    defaultName: String,
    onConfirmCreate: (profile: CompressionProfile, archiveName: String, password: CharArray?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mode: Simple (default) vs Advanced (power-user)
    var dialogMode by remember { mutableStateOf(CompressionDialogMode.SIMPLE) }

    // Advanced / Simple configuration states
    var selectedFormat by remember { mutableStateOf(ArchiveFormatOption.ZIP) }
    var selectedPreset by remember { mutableStateOf(CompressionLevelPreset.NORMAL) }
    var customLevel by remember { mutableIntStateOf(6) }
    var selectedMethod by remember { mutableStateOf(CompressionMethodOption.DEFAULT) }
    var selectedThreads by remember { mutableIntStateOf(0) } // 0 = Auto
    var isSolid by remember { mutableStateOf(true) }
    var dictionarySizeMb by remember { mutableIntStateOf(32) }

    // Split size state
    var selectedSplitSize by remember { mutableStateOf(SplitSizePreset.NO_SPLIT) }
    var splitDropdownExpanded by remember { mutableStateOf(false) }
    var customSplitMb by remember { mutableStateOf("100") }

    // Encryption & Password States
    var isEncryptEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var encryptFileNames by remember { mutableStateOf(false) }
    var copyFeedback by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val passwordStrength = remember(password) {
        ArchiveSecurityEvaluator.evaluatePassword(password)
    }

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
    val displaySuffix = if (selectedSplitSize != SplitSizePreset.NO_SPLIT && selectedFormat == ArchiveFormatOption.SEVEN_Z) {
        ".7z.001"
    } else {
        ext
    }

    val isPasswordValid = !isEncryptEnabled || (password.isNotEmpty() && password == confirmPassword)
    val canCreate = isNameValid && isPasswordValid

    fun triggerCreate() {
        if (canCreate) {
            val finalSplitBytes = when (selectedSplitSize) {
                SplitSizePreset.NO_SPLIT -> null
                SplitSizePreset.CUSTOM -> (customSplitMb.toLongOrNull() ?: 100L).coerceAtLeast(1L) * 1024 * 1024
                else -> selectedSplitSize.sizeBytes
            }
            val profile = CompressionProfile(
                format = selectedFormat,
                levelPreset = selectedPreset,
                compressionLevel = customLevel,
                method = selectedMethod,
                threads = selectedThreads,
                isSolid = isSolid,
                dictionarySizeMb = dictionarySizeMb,
                splitSize = selectedSplitSize,
                customSplitSizeBytes = finalSplitBytes,
                encryptFileNames = encryptFileNames
            )
            val finalName = if (rawName.endsWith(ext, ignoreCase = true)) rawName else "$rawName$ext"
            val passChars = if (isEncryptEnabled && password.isNotEmpty()) password.toCharArray() else null
            password = ""
            confirmPassword = ""
            onConfirmCreate(profile, finalName, passChars)
        }
    }

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
                // Segmented Mode Selector: [ Simple ] [ Advanced ]
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (dialogMode == CompressionDialogMode.SIMPLE) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { dialogMode = CompressionDialogMode.SIMPLE }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Simple",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (dialogMode == CompressionDialogMode.SIMPLE) FontWeight.Bold else FontWeight.Medium,
                                color = if (dialogMode == CompressionDialogMode.SIMPLE) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (dialogMode == CompressionDialogMode.ADVANCED) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable {
                                    dialogMode = CompressionDialogMode.ADVANCED
                                    // Power user defaults: 7z, Ultra, LZMA2, 64 MB dict
                                    if (selectedFormat == ArchiveFormatOption.ZIP) {
                                        selectedFormat = ArchiveFormatOption.SEVEN_Z
                                        selectedPreset = CompressionLevelPreset.ULTRA
                                        customLevel = 9
                                        selectedMethod = CompressionMethodOption.LZMA2
                                        selectedThreads = 4
                                        dictionarySizeMb = 64
                                        isSolid = true
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (dialogMode == CompressionDialogMode.ADVANCED) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Advanced",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (dialogMode == CompressionDialogMode.ADVANCED) FontWeight.Bold else FontWeight.Medium,
                                    color = if (dialogMode == CompressionDialogMode.ADVANCED) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Selection Summary Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${sources.size} item(s) selected • ${FileFormatter.formatFileSize(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (dialogMode == CompressionDialogMode.SIMPLE) {
                    // ==========================================
                    // SIMPLE MODE (Default)
                    // ==========================================

                    // Format Selector (Primary formats)
                    Text(
                        text = "Format",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatChip(ArchiveFormatOption.ZIP, selectedFormat) { selectedFormat = it }
                        FormatChip(ArchiveFormatOption.SEVEN_Z, selectedFormat) { selectedFormat = it }
                        FormatChip(ArchiveFormatOption.TAR, selectedFormat) { selectedFormat = it }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Compression Level Preset
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
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Split size selector
                    SplitSizeSelector(
                        selectedSplitSize = selectedSplitSize,
                        splitDropdownExpanded = splitDropdownExpanded,
                        customSplitMb = customSplitMb,
                        onDropdownToggle = { splitDropdownExpanded = it },
                        onSelectPreset = { preset ->
                            selectedSplitSize = preset
                            splitDropdownExpanded = false
                            if (preset != SplitSizePreset.NO_SPLIT && selectedFormat != ArchiveFormatOption.SEVEN_Z) {
                                selectedFormat = ArchiveFormatOption.SEVEN_Z
                            }
                        },
                        onCustomMbChange = { customSplitMb = it }
                    )

                } else {
                    // ==========================================
                    // ADVANCED MODE (Power-User Interface)
                    // ==========================================
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Format (7z, ZIP, TAR, TAR.GZ, TAR.XZ, TAR.BZ2)
                            Text(
                                text = "Format",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FormatChip(ArchiveFormatOption.SEVEN_Z, selectedFormat) {
                                        selectedFormat = it
                                        selectedMethod = CompressionMethodOption.LZMA2
                                        selectedPreset = CompressionLevelPreset.ULTRA
                                        customLevel = 9
                                        dictionarySizeMb = 64
                                    }
                                    FormatChip(ArchiveFormatOption.ZIP, selectedFormat) {
                                        selectedFormat = it
                                        selectedMethod = CompressionMethodOption.DEFLATE
                                    }
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

                            Spacer(modifier = Modifier.height(10.dp))

                            // Level (Store, Fast, Normal, Max, Ultra)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Level",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${selectedPreset.displayName} (Level $customLevel)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                    if (selectedFormat == ArchiveFormatOption.SEVEN_Z) {
                                        dictionarySizeMb = 64
                                    }
                                }
                            }
                            Slider(
                                value = customLevel.toFloat(),
                                onValueChange = { customLevel = it.toInt() },
                                valueRange = 0f..9f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Method
                            Text(
                                text = "Method",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MethodChip(CompressionMethodOption.LZMA2, selectedMethod) { selectedMethod = it }
                                MethodChip(CompressionMethodOption.LZMA, selectedMethod) { selectedMethod = it }
                                MethodChip(CompressionMethodOption.DEFLATE, selectedMethod) { selectedMethod = it }
                                MethodChip(CompressionMethodOption.COPY, selectedMethod) { selectedMethod = it }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Threads
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Threads",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (selectedThreads == 0) "Auto (${Runtime.getRuntime().availableProcessors()} cores)" else "$selectedThreads thread(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (tp in ThreadCountPreset.entries) {
                                    ThreadChip(tp.displayName, tp.count, selectedThreads) { selectedThreads = it }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Dictionary (LZMA2 / 7z)
                            val is7zSelected = selectedFormat == ArchiveFormatOption.SEVEN_Z
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (is7zSelected) "Dictionary" else "Dictionary (7z only)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (is7zSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                if (is7zSelected) {
                                    val dictPreset = DictionarySizePreset.fromSizeMb(dictionarySizeMb)
                                    Text(
                                        text = "~${dictPreset.memoryEstimateMb} MB RAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (dictionarySizeMb >= 64) ZipOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (dp in DictionarySizePreset.entries) {
                                    DictChip(dp.displayName, dp.sizeMb, dictionarySizeMb, enabled = is7zSelected) {
                                        dictionarySizeMb = it
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Solid Archive Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (is7zSelected) "Solid" else "Solid (7z only)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (is7zSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "Single compression stream for maximum ratio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = isSolid && is7zSelected,
                                    onCheckedChange = { isSolid = it },
                                    enabled = is7zSelected
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Split Selector in Advanced
                            Text(
                                text = "Split",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SplitSizeSelector(
                                selectedSplitSize = selectedSplitSize,
                                splitDropdownExpanded = splitDropdownExpanded,
                                customSplitMb = customSplitMb,
                                onDropdownToggle = { splitDropdownExpanded = it },
                                onSelectPreset = { preset ->
                                    selectedSplitSize = preset
                                    splitDropdownExpanded = false
                                    if (preset != SplitSizePreset.NO_SPLIT && selectedFormat != ArchiveFormatOption.SEVEN_Z) {
                                        selectedFormat = ArchiveFormatOption.SEVEN_Z
                                    }
                                },
                                onCustomMbChange = { customSplitMb = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Archive Name Input Field
                OutlinedTextField(
                    value = rawName,
                    onValueChange = { rawName = it },
                    label = { Text("Archive Name") },
                    suffix = { Text(displaySuffix, color = ZipOrange, fontWeight = FontWeight.Bold) },
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
                    keyboardActions = KeyboardActions(onDone = { triggerCreate() }),
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
                                .clickable {
                                    val newEnabled = !isEncryptEnabled
                                    isEncryptEnabled = newEnabled
                                    if (newEnabled && selectedFormat != ArchiveFormatOption.SEVEN_Z) {
                                        selectedFormat = ArchiveFormatOption.SEVEN_Z
                                        selectedPreset = CompressionLevelPreset.NORMAL
                                        customLevel = 6
                                        selectedMethod = CompressionMethodOption.LZMA2
                                        isSolid = true
                                        dictionarySizeMb = 32
                                    }
                                },
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
                                onCheckedChange = { enabled ->
                                    isEncryptEnabled = enabled
                                    if (enabled && selectedFormat != ArchiveFormatOption.SEVEN_Z) {
                                        selectedFormat = ArchiveFormatOption.SEVEN_Z
                                        selectedPreset = CompressionLevelPreset.NORMAL
                                        customLevel = 6
                                        selectedMethod = CompressionMethodOption.LZMA2
                                        isSolid = true
                                        dictionarySizeMb = 32
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(visible = isEncryptEnabled) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "7Z Format • AES-256 Payload Encryption",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            val generated = ArchiveSecurityEvaluator.generateSecurePassword(16)
                                            password = generated
                                            confirmPassword = generated
                                            showPassword = true
                                            copyFeedback = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Generate Strong Password", style = MaterialTheme.typography.labelMedium)
                                    }

                                    if (password.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(password))
                                                copyFeedback = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (copyFeedback) "Copied!" else "Copy", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                        copyFeedback = false
                                    },
                                    label = { Text("Password") },
                                    placeholder = { Text("••••••••••") },
                                    singleLine = true,
                                    isError = isEncryptEnabled && password.isEmpty() && confirmPassword.isNotEmpty(),
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showPassword) "Hide password" else "Show password"
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                if (password.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val strengthColor = when (passwordStrength.strength) {
                                        PasswordStrength.VERY_WEAK, PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                                        PasswordStrength.FAIR -> Color(0xFFE6A700)
                                        PasswordStrength.STRONG, PasswordStrength.VERY_STRONG -> Color(0xFF2E7D32)
                                    }
                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Strength: ${passwordStrength.label}",
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
                                        if (passwordStrength.warningMessage != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = passwordStrength.warningMessage,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val passwordsMismatch = isEncryptEnabled && confirmPassword.isNotEmpty() && password != confirmPassword
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    label = { Text("Confirm Password") },
                                    placeholder = { Text("••••••••••") },
                                    singleLine = true,
                                    isError = passwordsMismatch,
                                    supportingText = if (passwordsMismatch) {
                                        { Text("Passwords do not match", color = MaterialTheme.colorScheme.error) }
                                    } else null,
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showPassword) "Hide password" else "Show password"
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { encryptFileNames = !encryptFileNames }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = "Encrypt file names",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Supported when inspecting/extracting archives. Writer encrypts all data payloads with AES-256.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = encryptFileNames,
                                        onCheckedChange = { encryptFileNames = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { triggerCreate() },
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
private fun SplitSizeSelector(
    selectedSplitSize: SplitSizePreset,
    splitDropdownExpanded: Boolean,
    customSplitMb: String,
    onDropdownToggle: (Boolean) -> Unit,
    onSelectPreset: (SplitSizePreset) -> Unit,
    onCustomMbChange: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedCard(
            onClick = { onDropdownToggle(true) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedSplitSize.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select split size"
                )
            }
        }
        DropdownMenu(
            expanded = splitDropdownExpanded,
            onDismissRequest = { onDropdownToggle(false) }
        ) {
            for (preset in SplitSizePreset.entries) {
                DropdownMenuItem(
                    text = { Text(preset.displayName) },
                    onClick = { onSelectPreset(preset) }
                )
            }
        }
    }

    if (selectedSplitSize == SplitSizePreset.CUSTOM) {
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = customSplitMb,
            onValueChange = { if (it.all { c -> c.isDigit() }) onCustomMbChange(it) },
            label = { Text("Volume Size") },
            suffix = { Text("MB", fontWeight = FontWeight.Bold) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
    }
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
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    FilterChip(
        selected = selectedSizeMb == sizeMb,
        onClick = { if (enabled) onSelect(sizeMb) },
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}
