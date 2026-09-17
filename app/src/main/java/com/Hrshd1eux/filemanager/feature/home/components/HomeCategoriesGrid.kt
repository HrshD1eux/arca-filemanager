package com.Hrshd1eux.filemanager.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter

/**
 * Google Files 2x3 Category grid:
 * Images, Videos, Documents
 * Archives, APKs, Downloads
 */
@Composable
fun HomeCategoriesGrid(
    onCategoryClick: (CategoryFilter) -> Unit,
    onDownloadsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Row 1: Images, Videos, Documents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryGridItem(
                icon = Icons.Default.Image,
                label = "Images",
                iconTint = Color(0xFF00ACC1), // Cyan
                containerColor = Color(0xFF00ACC1).copy(alpha = 0.12f),
                onClick = { onCategoryClick(CategoryFilter.IMAGES) },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.VideoLibrary,
                label = "Videos",
                iconTint = Color(0xFF8E24AA), // Purple
                containerColor = Color(0xFF8E24AA).copy(alpha = 0.12f),
                onClick = { onCategoryClick(CategoryFilter.VIDEOS) },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.Description,
                label = "Documents",
                iconTint = Color(0xFF1E88E5), // Blue
                containerColor = Color(0xFF1E88E5).copy(alpha = 0.12f),
                onClick = { onCategoryClick(CategoryFilter.DOCUMENTS) },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Archives, APKs, Downloads
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryGridItem(
                icon = Icons.Default.FolderZip,
                label = "Archives",
                iconTint = Color(0xFFFB8C00), // Amber
                containerColor = Color(0xFFFB8C00).copy(alpha = 0.12f),
                onClick = { onCategoryClick(CategoryFilter.ARCHIVES) },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.Android,
                label = "APKs",
                iconTint = Color(0xFF43A047), // Green
                containerColor = Color(0xFF43A047).copy(alpha = 0.12f),
                onClick = { onCategoryClick(CategoryFilter.APKS) },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.Download,
                label = "Downloads",
                iconTint = Color(0xFFE53935), // Red / Deep Orange
                containerColor = Color(0xFFE53935).copy(alpha = 0.12f),
                onClick = onDownloadsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CategoryGridItem(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
