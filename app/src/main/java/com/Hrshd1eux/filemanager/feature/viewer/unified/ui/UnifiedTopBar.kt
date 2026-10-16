package com.Hrshd1eux.filemanager.feature.viewer.unified.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Standard top bar for the unified preview screen with consistent action placement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: () -> Unit,
    onOpenWith: () -> Unit,
    onShowProperties: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkOverlay: Boolean = false,
    customActions: @Composable RowScope.() -> Unit = {}
) {
    val containerColor = if (isDarkOverlay) {
        Color.Black.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isDarkOverlay) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subtitleColor = if (isDarkOverlay) {
        Color.White.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = contentColor
                )
            }
        },
        actions = {
            customActions()

            // Open With external app action
            IconButton(onClick = onOpenWith) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open with external app",
                    tint = contentColor
                )
            }

            // Properties action
            IconButton(onClick = onShowProperties) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "File properties",
                    tint = contentColor
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    )
}
