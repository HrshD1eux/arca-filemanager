package com.Hrshd1eux.filemanager.feature.viewer.unified.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Consistent 6-action bottom bar for the unified preview experience.
 */
@Composable
fun UnifiedBottomBar(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onNavigatePrevious: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onNavigateNext: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkOverlay: Boolean = false
) {
    val containerColor = if (isDarkOverlay) {
        Color.Black.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val contentColor = if (isDarkOverlay) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val disabledColor = if (isDarkOverlay) {
        Color.White.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Surface(
        color = containerColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous File
            IconButton(
                onClick = onNavigatePrevious,
                enabled = hasPrevious
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = "Previous File",
                    tint = if (hasPrevious) contentColor else disabledColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Share Action
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = contentColor
                )
            }

            // Copy Action
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = contentColor
                )
            }

            // Move Action
            IconButton(onClick = onMove) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Move",
                    tint = contentColor
                )
            }

            // Delete Action
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = if (isDarkOverlay) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.error
                )
            }

            // Next File
            IconButton(
                onClick = onNavigateNext,
                enabled = hasNext
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Next File",
                    tint = if (hasNext) contentColor else disabledColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
