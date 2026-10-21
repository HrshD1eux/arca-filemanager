package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.operations.recovery.RecoveryReport

/**
 * Dialog presenting an honest report for an interrupted file operation (Milestone 1.4.36).
 *
 * Requirements:
 * - "Previous operation interrupted"
 * - "Copying: 42 GB of 80 GB" (verified figures)
 * - "Some files may need verification before continuing."
 * - Three distinct actions: Resume · Restart · Discard operation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterruptedOperationRecoveryDialog(
    report: RecoveryReport,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicAlertDialog(
        onDismissRequest = { /* Non-dismissible by clicking outside to prevent accidental state loss */ },
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with warning icon
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (report.storageDisconnected) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (report.storageDisconnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Previous operation interrupted",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Verified Progress Summary: "Copying: 42 GB of 80 GB"
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${report.operationType.displayName}: ${report.progressDisplay}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LinearProgressIndicator(
                        progress = { report.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${report.verifiedFilesCount} of ${report.totalFilesCount} files verified intact",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${report.progressPercent.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Explanatory Notice Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (report.storageDisconnected) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = report.notice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (report.storageDisconnected) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions: Resume · Restart · Discard operation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Resume Action
                    Button(
                        onClick = onResume,
                        enabled = report.canResume && !report.storageDisconnected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (report.storageDisconnected) "Storage Disconnected" else "Resume",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Restart Action
                        OutlinedButton(
                            onClick = onRestart,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Restart")
                        }

                        // Discard Action
                        TextButton(
                            onClick = onDiscard,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Discard")
                        }
                    }
                }
            }
        }
    }
}
