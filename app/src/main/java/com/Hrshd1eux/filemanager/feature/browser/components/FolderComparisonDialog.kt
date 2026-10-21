package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Hrshd1eux.filemanager.core.designsystem.FolderYellow
import com.Hrshd1eux.filemanager.core.storage.util.ComparisonItem
import com.Hrshd1eux.filemanager.core.storage.util.ComparisonStatus
import com.Hrshd1eux.filemanager.core.storage.util.FolderComparator
import com.Hrshd1eux.filemanager.core.storage.util.FolderComparisonReport
import java.io.File

/**
 * Structured folder comparison dialog (Milestones 1.4.23 & 1.4.24).
 *
 * Implements structured 4-column comparison:
 * - Item | Location/Backup A | Location/Backup B | Result
 * - Status filtering (All, Same, Requires comparison, Different, Missing from B, Missing from A, Inaccessible)
 * - Safe subfolder drill-down navigation
 */
@Composable
fun FolderComparisonDialog(
    leftFolderName: String,
    rightFolderName: String,
    report: FolderComparisonReport,
    leftPath: String? = null,
    rightPath: String? = null,
    onSyncMissingToRight: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var activeFilter by remember { mutableStateOf<ComparisonStatus?>(null) }
    var subfolderReport by remember { mutableStateOf<Pair<String, FolderComparisonReport>?>(null) }

    val currentReport = subfolderReport?.second ?: report
    val currentTitle = subfolderReport?.first ?: "Folder Comparison"

    val filteredItems = remember(currentReport, activeFilter) {
        if (activeFilter == null) {
            currentReport.items
        } else {
            currentReport.items.filter { it.status == activeFilter }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (subfolderReport != null) {
                    Text(
                        text = "Tap 'Back' below to return to root folders",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Locations
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Backup A (Left)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(leftFolderName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Backup B (Right)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(rightFolderName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status Filter Chips (1.4.23: allow users to filter by status)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activeFilter == null,
                        onClick = { activeFilter = null },
                        label = { Text("All (${currentReport.totalCount})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = activeFilter == ComparisonStatus.IDENTICAL,
                        onClick = { activeFilter = if (activeFilter == ComparisonStatus.IDENTICAL) null else ComparisonStatus.IDENTICAL },
                        label = { Text("Same (${currentReport.identicalCount})", fontSize = 11.sp) }
                    )
                    if (currentReport.requiresComparisonCount > 0) {
                        FilterChip(
                            selected = activeFilter == ComparisonStatus.REQUIRES_COMPARISON,
                            onClick = { activeFilter = if (activeFilter == ComparisonStatus.REQUIRES_COMPARISON) null else ComparisonStatus.REQUIRES_COMPARISON },
                            label = { Text("Requires comparison (${currentReport.requiresComparisonCount})", fontSize = 11.sp) }
                        )
                    }
                    FilterChip(
                        selected = activeFilter == ComparisonStatus.MODIFIED,
                        onClick = { activeFilter = if (activeFilter == ComparisonStatus.MODIFIED) null else ComparisonStatus.MODIFIED },
                        label = { Text("Diff (${currentReport.modifiedCount})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = activeFilter == ComparisonStatus.UNIQUE_LEFT,
                        onClick = { activeFilter = if (activeFilter == ComparisonStatus.UNIQUE_LEFT) null else ComparisonStatus.UNIQUE_LEFT },
                        label = { Text("Missing in B (${currentReport.leftOnlyCount})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = activeFilter == ComparisonStatus.UNIQUE_RIGHT,
                        onClick = { activeFilter = if (activeFilter == ComparisonStatus.UNIQUE_RIGHT) null else ComparisonStatus.UNIQUE_RIGHT },
                        label = { Text("Missing in A (${currentReport.rightOnlyCount})", fontSize = 11.sp) }
                    )
                    if (currentReport.inaccessibleCount > 0) {
                        FilterChip(
                            selected = activeFilter == ComparisonStatus.INACCESSIBLE,
                            onClick = { activeFilter = if (activeFilter == ComparisonStatus.INACCESSIBLE) null else ComparisonStatus.INACCESSIBLE },
                            label = { Text("Inaccessible (${currentReport.inaccessibleCount})", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Table Header (1.4.23: Item | Backup A | Backup B | Result)
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Item",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "Backup A",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.9f)
                        )
                        Text(
                            text = "Backup B",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.9f)
                        )
                        Text(
                            text = "Result",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }

                HorizontalDivider()

                // Items list
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching items found.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredItems, key = { it.name }) { item ->
                            StructuredComparisonRow(
                                item = item,
                                onDrillDown = {
                                    if (item.isDirectory && item.leftNode != null && item.rightNode != null) {
                                        val fA = File(item.leftNode.path)
                                        val fB = File(item.rightNode.path)
                                        if (fA.isDirectory && fB.isDirectory) {
                                            val subReport = FolderComparator.compareDirectoriesStructured(fA, fB)
                                            subfolderReport = Pair(item.name, subReport)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (subfolderReport != null) {
                OutlinedButton(onClick = { subfolderReport = null }) {
                    Text("Back to Root")
                }
            } else if (onSyncMissingToRight != null && (report.leftOnlyCount > 0 || report.modifiedCount > 0)) {
                Button(
                    onClick = {
                        onSyncMissingToRight()
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync to Right (${report.leftOnlyCount + report.modifiedCount})")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun StructuredComparisonRow(
    item: ComparisonItem,
    onDrillDown: () -> Unit
) {
    val resultColor = when (item.status) {
        ComparisonStatus.IDENTICAL -> Color(0xFF2E7D32)
        ComparisonStatus.REQUIRES_COMPARISON -> Color(0xFFFFA000)
        ComparisonStatus.MODIFIED -> Color(0xFF1976D2)
        ComparisonStatus.UNIQUE_LEFT -> Color(0xFFE65100)
        ComparisonStatus.UNIQUE_RIGHT -> Color(0xFF7B1FA2)
        ComparisonStatus.INACCESSIBLE -> Color(0xFFC62828)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isDirectory && item.leftNode != null && item.rightNode != null, onClick = onDrillDown),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item Column
            Row(
                modifier = Modifier.weight(1.3f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (item.isDirectory) FolderYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isDirectory && item.leftNode != null && item.rightNode != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Navigate subfolder",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Backup A Column
            Text(
                text = item.presenceA,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.presenceA == "Present") Color(0xFF2E7D32) else Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.9f)
            )

            // Backup B Column
            Text(
                text = item.presenceB,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.presenceB == "Present") Color(0xFF2E7D32) else Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.9f)
            )

            // Result Column
            Text(
                text = item.resultDescription,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = resultColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f)
            )
        }
    }
}
