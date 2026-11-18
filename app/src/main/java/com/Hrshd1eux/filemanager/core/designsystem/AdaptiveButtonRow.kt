package com.Hrshd1eux.filemanager.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive action button container that automatically adapts to device width and font scaling.
 *
 * When screen width is constrained (< 360dp, small devices, foldable cover screens) or when
 * Android accessibility font scaling is enlarged (> 1.15x), buttons automatically stack vertically
 * with full width to prevent truncation, text wrapping bugs, and touch target overlap.
 * On normal or wide screens, buttons are laid out horizontally.
 */
@Composable
fun AdaptiveButtonRow(
    primaryButton: @Composable () -> Unit,
    secondaryButton: (@Composable () -> Unit)? = null,
    tertiaryButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    forceVerticalOnCompact: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val fontScale = configuration.fontScale
    val screenWidth = configuration.screenWidthDp.dp

    val isCompactOrEnlarged = forceVerticalOnCompact && (screenWidth < 360.dp || fontScale > 1.15f)

    if (isCompactOrEnlarged) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            primaryButton()
            secondaryButton?.invoke()
            tertiaryButton?.invoke()
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tertiaryButton?.let {
                Box(modifier = Modifier.weight(1f)) { it() }
            }
            secondaryButton?.let {
                Box(modifier = Modifier.weight(1f)) { it() }
            }
            Box(modifier = Modifier.weight(1f)) {
                primaryButton()
            }
        }
    }
}

/**
 * Responsive paired confirmation/dismiss dialog button row that stacks vertically
 * on compact or high-DPI font-scaled screens.
 */
@Composable
fun AdaptiveDialogButtonRow(
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp
) {
    val configuration = LocalConfiguration.current
    val fontScale = configuration.fontScale
    val screenWidth = configuration.screenWidthDp.dp

    val shouldStackVertically = screenWidth < 360.dp || fontScale > 1.15f

    if (shouldStackVertically) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            confirmButton()
            dismissButton?.invoke()
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dismissButton?.let {
                Box(modifier = Modifier.weight(1f)) { it() }
            }
            Box(modifier = Modifier.weight(1f)) {
                confirmButton()
            }
        }
    }
}
