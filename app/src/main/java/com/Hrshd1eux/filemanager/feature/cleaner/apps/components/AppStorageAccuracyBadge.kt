package com.Hrshd1eux.filemanager.feature.cleaner.apps.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Hrshd1eux.filemanager.core.storage.model.StorageAccuracy

/**
 * Renders an explicit accuracy badge indicating whether a storage metric is Exact, Approximate, Stale, or Unavailable.
 */
@Composable
fun AppStorageAccuracyBadge(
    accuracy: StorageAccuracy,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (bgColor, textColor) = when (accuracy) {
        StorageAccuracy.EXACT -> {
            Color(0xFF2E7D32).copy(alpha = 0.15f) to Color(0xFF1B5E20)
        }
        StorageAccuracy.APPROXIMATE -> {
            Color(0xFFE65100).copy(alpha = 0.15f) to Color(0xFFBF360C)
        }
        StorageAccuracy.STALE -> {
            Color(0xFFF57F17).copy(alpha = 0.15f) to Color(0xFFE65100)
        }
        StorageAccuracy.UNAVAILABLE -> {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
    }

    val hPadding = if (compact) 6.dp else 8.dp
    val vPadding = if (compact) 2.dp else 4.dp
    val fontSize = if (compact) 10.sp else 11.sp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = hPadding, vertical = vPadding)
    ) {
        Text(
            text = accuracy.displayName,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize),
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
