package com.Hrshd1eux.filemanager.feature.archive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import java.util.zip.Deflater

@Composable
fun CompressDialog(
    sources: List<StorageNode>,
    defaultName: String,
    onConfirmCreate: (archiveName: String, compressionLevel: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawName by remember {
        mutableStateOf(
            if (defaultName.endsWith(".zip", ignoreCase = true)) {
                defaultName.substringBeforeLast(".zip")
            } else {
                defaultName
            }
        )
    }
    var selectedLevel by remember { mutableIntStateOf(Deflater.DEFAULT_COMPRESSION) } // 6 (Normal)
    var selectedFormat by remember { mutableStateOf("ZIP") }

    val totalBytes = remember(sources) { sources.sumOf { it.size } }
    val isNameValid = rawName.isNotBlank() && !rawName.contains('/') && !rawName.contains('\\')

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
                    text = "Compress Files",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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

                // Format Selector (ZIP, 7Z)
                Text(
                    text = "Archive Format",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFormat == "ZIP",
                        onClick = { selectedFormat = "ZIP" },
                        label = { Text("ZIP") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ZipOrange.copy(alpha = 0.15f),
                            selectedLabelColor = ZipOrange
                        )
                    )
                    FilterChip(
                        selected = selectedFormat == "7Z",
                        onClick = { selectedFormat = "7Z" },
                        label = { Text("7Z") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = false,
                        onClick = { /* Future format */ },
                        enabled = false,
                        label = { Text("TAR") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val ext = if (selectedFormat == "7Z") ".7z" else ".zip"

                // Name Input Field
                OutlinedTextField(
                    value = rawName,
                    onValueChange = { rawName = it },
                    label = { Text("Archive Name") },
                    suffix = { Text(ext, color = if (selectedFormat == "7Z") MaterialTheme.colorScheme.primary else ZipOrange) },
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
                            if (isNameValid) {
                                val finalName = if (rawName.endsWith(ext, ignoreCase = true)) rawName else "$rawName$ext"
                                onConfirmCreate(finalName, selectedLevel)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Compression Level Selector
                Text(
                    text = "Compression Level",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompressionLevelChip(
                        label = "Store",
                        level = Deflater.NO_COMPRESSION,
                        selectedLevel = selectedLevel,
                        onSelect = { selectedLevel = it }
                    )
                    CompressionLevelChip(
                        label = "Fast",
                        level = Deflater.BEST_SPEED,
                        selectedLevel = selectedLevel,
                        onSelect = { selectedLevel = it }
                    )
                    CompressionLevelChip(
                        label = "Normal",
                        level = Deflater.DEFAULT_COMPRESSION,
                        selectedLevel = selectedLevel,
                        onSelect = { selectedLevel = it }
                    )
                    CompressionLevelChip(
                        label = "Max",
                        level = Deflater.BEST_COMPRESSION,
                        selectedLevel = selectedLevel,
                        onSelect = { selectedLevel = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isNameValid) {
                        val ext = if (selectedFormat == "7Z") ".7z" else ".zip"
                        val finalName = if (rawName.endsWith(ext, ignoreCase = true)) rawName else "$rawName$ext"
                        onConfirmCreate(finalName, selectedLevel)
                    }
                },
                enabled = isNameValid,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

@Composable
private fun CompressionLevelChip(
    label: String,
    level: Int,
    selectedLevel: Int,
    onSelect: (Int) -> Unit
) {
    FilterChip(
        selected = selectedLevel == level,
        onClick = { onSelect(level) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
