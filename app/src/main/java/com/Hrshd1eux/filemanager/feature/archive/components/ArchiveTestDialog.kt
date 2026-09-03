package com.Hrshd1eux.filemanager.feature.archive.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.designsystem.SuccessGreen

@Composable
fun ArchiveTestDialog(
    report: ArchiveIntegrityReport,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSuccess = report.isPassed

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSuccess) SuccessGreen.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) SuccessGreen else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = if (isSuccess) "Test Passed" else "Test Failed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isSuccess) {
                        "All ${report.totalEntries} entries in '${report.archiveName}' were verified. CRC-32 checksums match perfectly with zero corruption."
                    } else {
                        "Found ${report.corruptedEntries.size} corrupted or unreadable entries in '${report.archiveName}'."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isSuccess && report.corruptedEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Corrupted entries:\n" + report.corruptedEntries.take(5).joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Verification completed in ${report.elapsedMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done")
            }
        },
        modifier = modifier
    )
}
