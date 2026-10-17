package com.Hrshd1eux.filemanager.feature.home.components

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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter

/**
 * Google Files inspired primary Storage card displaying capacity metrics and progress bar.
 */
@Composable
fun HomeStorageCard(
    breakdown: StorageBreakdown,
    title: String = "Storage",
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasAllFilesAccess: Boolean = true,
    onGrantPermissionClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {
                if (!hasAllFilesAccess && onGrantPermissionClick != null) {
                    onGrantPermissionClick()
                } else {
                    onCardClick()
                }
            }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasAllFilesAccess) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: "Storage" title & percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (breakdown.totalBytes > 0) {
                    val percent = (breakdown.usedPercentage * 100).toInt()
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main storage metric: e.g. "86.4 GB / 128 GB"
            val usedFormatted = FileFormatter.formatFileSize(breakdown.usedBytes)
            val totalFormatted = FileFormatter.formatFileSize(breakdown.totalBytes)
            val freeFormatted = FileFormatter.formatFileSize(breakdown.freeBytes)

            if (!hasAllFilesAccess) {
                Text(
                    text = "Limited Access Mode",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Private Vault and Network features are active. Grant storage access to browse device files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onGrantPermissionClick != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Button(
                        onClick = onGrantPermissionClick,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Grant Storage Access")
                    }
                }
            } else {
                Text(
                    text = if (breakdown.totalBytes > 0) "$usedFormatted / $totalFormatted" else "Calculating storage...",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar (e.g. ████████████░░░)
                LinearProgressIndicator(
                    progress = { breakdown.usedPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = if (breakdown.usedPercentage > 0.9f) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )

                if (breakdown.totalBytes > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$freeFormatted available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
