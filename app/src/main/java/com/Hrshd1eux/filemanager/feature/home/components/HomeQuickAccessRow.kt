package com.Hrshd1eux.filemanager.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.designsystem.TwoToneIconBadge
import com.Hrshd1eux.filemanager.core.designsystem.VaultEmerald
import com.Hrshd1eux.filemanager.core.designsystem.bounceClick

/**
 * Compact, ergonomic 4-tile horizontal row for Quick Access destinations
 * (Recent, Favorites, Private Vault, Network), saving ~200dp of vertical height.
 */
@Composable
fun HomeQuickAccessRow(
    recentCount: Int,
    favoriteCount: Int,
    onRecentsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onVaultClick: () -> Unit,
    onNetworkClick: () -> Unit,
    modifier: Modifier = Modifier,
    showVault: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickAccessTile(
            icon = Icons.Default.History,
            iconTint = Color(0xFF1E88E5),
            title = stringResource(R.string.nav_recent),
            subtitle = if (recentCount > 0) stringResource(R.string.badge_items_count, recentCount) else stringResource(R.string.badge_empty),
            onClick = onRecentsClick
        )

        QuickAccessTile(
            icon = Icons.Default.Star,
            iconTint = Color(0xFFFFA000),
            title = stringResource(R.string.nav_favorites),
            subtitle = if (favoriteCount > 0) stringResource(R.string.badge_items_count, favoriteCount) else stringResource(R.string.badge_none),
            onClick = onFavoritesClick
        )

        if (showVault) {
            QuickAccessTile(
                icon = Icons.Default.Lock,
                iconTint = VaultEmerald,
                title = stringResource(R.string.vault_title),
                subtitle = stringResource(R.string.badge_vault_encrypted),
                onClick = onVaultClick
            )
        }

        QuickAccessTile(
            icon = Icons.Default.Language,
            iconTint = Color(0xFF00897B),
            title = stringResource(R.string.nav_network),
            subtitle = stringResource(R.string.badge_network_offline),
            onClick = onNetworkClick
        )
    }
}

@Composable
private fun RowScope.QuickAccessTile(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .weight(1f)
            .heightIn(min = 88.dp)
            .bounceClick(scaleDown = 0.985f, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TwoToneIconBadge(
                icon = icon,
                tint = iconTint,
                size = 28.dp,
                iconSize = 15.dp,
                shape = RoundedCornerShape(6.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
