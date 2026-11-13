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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.CategoryAmber
import com.Hrshd1eux.filemanager.core.designsystem.CategoryBlue
import com.Hrshd1eux.filemanager.core.designsystem.CategoryCoral
import com.Hrshd1eux.filemanager.core.designsystem.CategoryCyan
import com.Hrshd1eux.filemanager.core.designsystem.CategoryGreen
import com.Hrshd1eux.filemanager.core.designsystem.CategoryPurple
import com.Hrshd1eux.filemanager.core.designsystem.CategoryRed
import com.Hrshd1eux.filemanager.core.designsystem.TwoToneIconBadge
import com.Hrshd1eux.filemanager.core.designsystem.bounceClick
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter

/**
 * 4x2 Category Grid for fast navigation:
 * Row 1: Images, Videos, Audio, Documents
 * Row 2: Downloads, Archives, APKs, Other
 */
@Composable
fun HomeCategoriesGrid(
    onCategoryClick: (CategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
    onDownloadsClick: (() -> Unit)? = null,
    onAppsClick: (() -> Unit)? = null,
    onTrashClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.section_categories),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Row 1: Images, Videos, Audio, Documents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryGridItem(
                icon = Icons.Default.Image,
                label = stringResource(R.string.category_images),
                iconTint = CategoryCyan,
                onClick = { onCategoryClick(CategoryFilter.IMAGES) },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.VideoLibrary,
                label = stringResource(R.string.category_videos),
                iconTint = CategoryPurple,
                onClick = { onCategoryClick(CategoryFilter.VIDEOS) },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.Audiotrack,
                label = stringResource(R.string.category_audio),
                iconTint = CategoryCoral,
                onClick = { onCategoryClick(CategoryFilter.AUDIO) },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.Description,
                label = stringResource(R.string.category_documents),
                iconTint = CategoryBlue,
                onClick = { onCategoryClick(CategoryFilter.DOCUMENTS) },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Downloads, Archives, APKs, Other
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryGridItem(
                icon = Icons.Default.Download,
                label = stringResource(R.string.category_downloads),
                iconTint = CategoryRed,
                onClick = {
                    if (onDownloadsClick != null) {
                        onDownloadsClick()
                    } else {
                        onCategoryClick(CategoryFilter.DOWNLOADS)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.FolderZip,
                label = stringResource(R.string.category_archives),
                iconTint = CategoryAmber,
                onClick = { onCategoryClick(CategoryFilter.ARCHIVES) },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.Android,
                label = stringResource(R.string.category_apks),
                iconTint = CategoryGreen,
                onClick = {
                    if (onAppsClick != null) {
                        onAppsClick()
                    } else {
                        onCategoryClick(CategoryFilter.APKS)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            CategoryGridItem(
                icon = Icons.Default.DeleteSweep,
                label = stringResource(R.string.category_trash),
                iconTint = CategoryRed,
                onClick = {
                    if (onTrashClick != null) {
                        onTrashClick()
                    } else {
                        onCategoryClick(CategoryFilter.OTHER)
                    }
                },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .bounceClick(scaleDown = 0.94f, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TwoToneIconBadge(
                icon = icon,
                tint = iconTint,
                size = 42.dp,
                iconSize = 22.dp,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
