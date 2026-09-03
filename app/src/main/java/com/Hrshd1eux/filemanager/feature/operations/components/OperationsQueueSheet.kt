package com.Hrshd1eux.filemanager.feature.operations.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.designsystem.ZipOrange
import com.Hrshd1eux.filemanager.core.operations.queue.OperationStatus
import com.Hrshd1eux.filemanager.core.operations.queue.OperationType
import com.Hrshd1eux.filemanager.core.operations.queue.QueuedOperation
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsQueueSheet(
    activeOperation: QueuedOperation?,
    waitingQueue: List<QueuedOperation>,
    completedHistory: List<QueuedOperation>,
    onCancelOperation: (String) -> Unit,
    onCancelAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasPending = activeOperation != null || waitingQueue.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Operations",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (hasPending) {
                    TextButton(onClick = onCancelAll) {
                        Text("Cancel All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Active Running Operation Card
                if (activeOperation != null) {
                    item {
                        ActiveOperationCard(
                            operation = activeOperation,
                            onCancel = { onCancelOperation(activeOperation.id) }
                        )
                    }
                }

                // 2. Waiting Queue Section
                if (waitingQueue.isNotEmpty()) {
                    item {
                        Text(
                            text = "Waiting (${waitingQueue.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(waitingQueue, key = { it.id }) { op ->
                        WaitingOperationCard(
                            operation = op,
                            onCancel = { onCancelOperation(op.id) }
                        )
                    }
                }

                // 3. Completed / Recent History
                if (completedHistory.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onClearCompleted) {
                                Text("Clear History")
                            }
                        }
                    }

                    items(completedHistory.take(5), key = { it.id }) { historyOp ->
                        HistoryOperationCard(operation = historyOp)
                    }
                }

                if (!hasPending && completedHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No active or queued operations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOperationCard(
    operation: QueuedOperation,
    onCancel: () -> Unit
) {
    val progress = operation.progress
    val percent = (progress.progressRatio * 100).toInt()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(getOperationColor(operation.type).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getOperationIcon(operation.type),
                        contentDescription = null,
                        tint = getOperationColor(operation.type),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = operation.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Running • $percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = getOperationColor(operation.type),
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Operation",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress.progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = getOperationColor(operation.type)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val detailText = if (progress.totalBytes > 0L) {
                    "${FileFormatter.formatFileSize(progress.bytesProcessed)} / ${FileFormatter.formatFileSize(progress.totalBytes)}"
                } else if (progress.totalFiles > 0) {
                    "${progress.filesProcessed} / ${progress.totalFiles} files"
                } else {
                    "Processing..."
                }

                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (progress.currentFileName.isNotEmpty()) {
                    Text(
                        text = progress.currentFileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WaitingOperationCard(
    operation: QueuedOperation,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getOperationIcon(operation.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Waiting in queue...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryOperationCard(operation: QueuedOperation) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (operation.status) {
                    OperationStatus.COMPLETED -> Icons.Default.CheckCircle
                    OperationStatus.CANCELLED -> Icons.Default.Cancel
                    OperationStatus.FAILED -> Icons.Default.Error
                    else -> Icons.Default.HourglassEmpty
                },
                contentDescription = null,
                tint = when (operation.status) {
                    OperationStatus.COMPLETED -> Color(0xFF4CAF50)
                    OperationStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                    OperationStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusText = when (operation.status) {
                    OperationStatus.COMPLETED -> "Completed"
                    OperationStatus.CANCELLED -> "Cancelled"
                    OperationStatus.FAILED -> operation.errorMessage ?: "Failed"
                    else -> "Pending"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun getOperationIcon(type: OperationType): ImageVector {
    return when (type) {
        OperationType.EXTRACT -> Icons.Default.Unarchive
        OperationType.COMPRESS -> Icons.Default.Archive
        OperationType.COPY -> Icons.Default.ContentCopy
        OperationType.MOVE -> Icons.AutoMirrored.Filled.DriveFileMove
        OperationType.DELETE -> Icons.Default.Delete
    }
}

private fun getOperationColor(type: OperationType): Color {
    return when (type) {
        OperationType.EXTRACT, OperationType.COMPRESS -> ZipOrange
        OperationType.COPY, OperationType.MOVE -> Color(0xFF2196F3)
        OperationType.DELETE -> Color(0xFFE53935)
    }
}
