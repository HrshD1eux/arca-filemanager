package com.Hrshd1eux.filemanager.feature.archive.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractOptionsBottomSheet(
    archiveName: String,
    isSelective: Boolean = false,
    selectedCount: Int = 0,
    onExtractToSubfolder: (conflictResolution: ConflictResolution, verifyIntegrity: Boolean) -> Unit,
    onExtractHere: (conflictResolution: ConflictResolution, verifyIntegrity: Boolean) -> Unit,
    onExtractToCustom: (conflictResolution: ConflictResolution, verifyIntegrity: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedResolution by remember { mutableStateOf(ConflictResolution.RENAME_AUTO) }
    var verifyIntegrity by remember { mutableStateOf(false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    val folderName = archiveName.substringBeforeLast(".")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = if (isSelective) "Extract $selectedCount Selected Items" else "Extract Archive",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = archiveName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 1-TAP ACTION 1: Extract to subfolder (if full extraction)
            if (!isSelective) {
                ExtractActionCard(
                    icon = Icons.Default.CreateNewFolder,
                    iconTint = FolderYellow,
                    title = "Extract to '$folderName/'",
                    subtitle = "1-tap unpack into dedicated folder",
                    isPrimary = true,
                    onClick = {
                        onDismiss()
                        onExtractToSubfolder(selectedResolution, verifyIntegrity)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 1-TAP ACTION 2: Extract here
            ExtractActionCard(
                icon = Icons.Default.FolderOpen,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Extract here",
                subtitle = "Unpacks directly into current directory",
                isPrimary = isSelective,
                onClick = {
                    onDismiss()
                    onExtractHere(selectedResolution, verifyIntegrity)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 1-TAP ACTION 3: Extract to custom directory
            ExtractActionCard(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                iconTint = ZipOrange,
                title = "Extract to…",
                subtitle = "Choose destination directory across storage",
                isPrimary = false,
                onClick = {
                    onDismiss()
                    onExtractToCustom(selectedResolution, verifyIntegrity)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // Expandable Advanced Settings (Conflict Resolution & Verification)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAdvancedOptions = !showAdvancedOptions }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Advanced Options",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = showAdvancedOptions,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = "If file already exists:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedResolution == ConflictResolution.RENAME_AUTO,
                            onClick = { selectedResolution = ConflictResolution.RENAME_AUTO },
                            label = { Text("Auto-Rename") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZipOrange.copy(alpha = 0.15f),
                                selectedLabelColor = ZipOrange
                            )
                        )

                        FilterChip(
                            selected = selectedResolution == ConflictResolution.OVERWRITE,
                            onClick = { selectedResolution = ConflictResolution.OVERWRITE },
                            label = { Text("Overwrite") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        FilterChip(
                            selected = selectedResolution == ConflictResolution.SKIP,
                            onClick = { selectedResolution = ConflictResolution.SKIP },
                            label = { Text("Skip") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { verifyIntegrity = !verifyIntegrity }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Verify integrity after extraction",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Checks CRC against archive records",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = verifyIntegrity,
                            onCheckedChange = { verifyIntegrity = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractActionCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
