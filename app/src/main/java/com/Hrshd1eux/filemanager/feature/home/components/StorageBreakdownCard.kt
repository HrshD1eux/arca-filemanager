package com.Hrshd1eux.filemanager.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.model.StorageCategory
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageBreakdownCard(
    breakdown: StorageBreakdown,
    title: String = "Internal Storage",
    onCardClick: () -> Unit,
    onCategoryClick: (CategoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val usedFormatted = FileFormatter.formatFileSize(breakdown.usedBytes)
                        val totalFormatted = FileFormatter.formatFileSize(breakdown.totalBytes)
                        Text(
                            text = if (breakdown.totalBytes > 0) "$usedFormatted used of $totalFormatted" else "Calculating...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (breakdown.totalBytes > 0) {
                    val percent = (breakdown.usedPercentage * 100).toInt()
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-color Segmented Storage Bar
            SegmentedStorageBar(
                breakdown = breakdown,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Breakdown Grid / FlowRow
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                breakdown.categories.forEach { catSize ->
                    CategoryPill(
                        category = catSize.category,
                        byteCount = catSize.byteCount,
                        onClick = {
                            catSize.category.searchCategory?.let { onCategoryClick(it) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedStorageBar(
    breakdown: StorageBreakdown,
    modifier: Modifier = Modifier
) {
    val total = breakdown.totalBytes
    if (total <= 0) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }

    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        breakdown.categories.forEach { catSize ->
            if (catSize.byteCount > 0) {
                val weight = (catSize.byteCount.toFloat() / total.toFloat()).coerceAtLeast(0.001f)
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .background(catSize.category.color)
                )
            }
        }

        // Free space remainder
        val freeWeight = (breakdown.freeBytes.toFloat() / total.toFloat()).coerceAtLeast(0f)
        if (freeWeight > 0) {
            Box(
                modifier = Modifier
                    .weight(freeWeight)
                    .fillMaxHeight()
                    .background(Color.Transparent)
            )
        }
    }
}

@Composable
private fun CategoryPill(
    category: StorageCategory,
    byteCount: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = category.searchCategory != null, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(category.color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = FileFormatter.formatFileSize(byteCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
