package com.Hrshd1eux.filemanager.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionLevelPreset
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArchiveSettingsDialog(
    enabledFormats: Set<ArchiveFormatOption>,
    defaultFormat: ArchiveFormatOption,
    defaultLevel: CompressionLevelPreset,
    onToggleFormat: (ArchiveFormatOption, Boolean) -> Unit,
    onSelectDefaultFormat: (ArchiveFormatOption) -> Unit,
    onSelectDefaultLevel: (CompressionLevelPreset) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Archive,
                contentDescription = null,
                tint = ZipOrange,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Archive Formats & Engines",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Section: Default Creation Format
                Text(
                    text = "Default Creation Format",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Format selected when creating new archives",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    enabledFormats.forEach { format ->
                        FilterChip(
                            selected = defaultFormat == format,
                            onClick = { onSelectDefaultFormat(format) },
                            label = { Text(format.displayName, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Section: Enabled Archive Formats
                Text(
                    text = "Enabled Archive Formats",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Formats available for compression throughout the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                ArchiveFormatOption.entries.forEach { format ->
                    val isEnabled = enabledFormats.contains(format)
                    val isLastEnabled = isEnabled && enabledFormats.size <= 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onToggleFormat(format, !isEnabled)
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isEnabled,
                            onCheckedChange = { onToggleFormat(format, it) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${format.displayName} (${format.defaultExtension})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isEnabled) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (isLastEnabled) {
                                Text(
                                    text = "Cannot disable (at least one format required)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Section: Default Compression Level
                Text(
                    text = "Default Compression Level",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompressionLevelPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = defaultLevel == preset,
                            onClick = { onSelectDefaultLevel(preset) },
                            label = { Text(preset.displayName) }
                        )
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
}
