package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.operations.clipboard.StagedTransfer
import com.Hrshd1eux.filemanager.core.operations.clipboard.StagedTransferType
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

/**
 * Universal Floating Bar for staged cross-boundary transfers (Phone ↔ NAS ↔ Server):
 *
 * Appears across local and remote browsers whenever items are staged for transfer.
 * Allows the user to browse anywhere in the file hierarchy and commit with 1-tap:
 * [ Copy here ] or [ Move here ].
 */
@Composable
fun StagedTransferFloatingBar(
    transfer: StagedTransfer,
    currentFolder: StorageNode?,
    onCommitTransfer: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stagedItems = transfer.sources
    if (stagedItems.isEmpty()) return

    val isMove = transfer.type == StagedTransferType.MOVE

    // Cannot move into the same directory where all items already reside
    val isSameDirectory = isMove && currentFolder != null && stagedItems.all { item ->
        val parentPath = item.path.substringBeforeLast('/', "")
        parentPath == currentFolder.path
    }

    // Cannot move a directory into itself or its own subdirectories
    val isInsideMovingSubtree = isMove && currentFolder != null && stagedItems.any { item ->
        item.isDirectory && (currentFolder.path == item.path || currentFolder.path.startsWith("${item.path}/"))
    }

    val canCommit = currentFolder != null && (!isMove || (!isSameDirectory && !isInsideMovingSubtree))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isMove) Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    val count = stagedItems.size
                    val verb = if (isMove) "Moving" else "Copying"
                    val countText = if (count == 1) "$verb 1 item" else "$verb $count items"
                    val fromLocation = transfer.sourceLocationName.ifEmpty { "Storage" }

                    Text(
                        text = "$countText ($fromLocation)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (currentFolder != null) "to ${currentFolder.name}" else "Navigate to target",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onCommitTransfer,
                    enabled = canCommit,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isMove) "Move here" else "Copy here",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Staged Transfer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
