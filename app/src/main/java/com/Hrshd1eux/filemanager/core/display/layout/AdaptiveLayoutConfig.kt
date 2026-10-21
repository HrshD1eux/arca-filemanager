package com.Hrshd1eux.filemanager.core.display.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen width classifications conforming to Android Material 3 adaptive design.
 */
enum class WindowWidthClass {
    COMPACT,  // Phone portrait (< 600dp)
    MEDIUM,   // Foldable / Small tablet / Phone landscape (600dp .. 839dp)
    EXPANDED  // Large tablet / Desktop / Dual-screen (>= 840dp)
}

/**
 * Responsive layout metrics for dynamic spacing, grid columns, and dual-pane display.
 */
data class AdaptiveLayoutMetrics(
    val widthClass: WindowWidthClass,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    val isLandscape: Boolean,
    val gridColumns: Int,
    val isDualPaneEligible: Boolean,
    val contentPaddingHorizontal: Dp
) {
    companion object {
        fun calculate(screenWidthDp: Dp, screenHeightDp: Dp): AdaptiveLayoutMetrics {
            val widthClass = when {
                screenWidthDp < 600.dp -> WindowWidthClass.COMPACT
                screenWidthDp < 840.dp -> WindowWidthClass.MEDIUM
                else -> WindowWidthClass.EXPANDED
            }

            val isLandscape = screenWidthDp > screenHeightDp

            val gridColumns = when (widthClass) {
                WindowWidthClass.COMPACT -> if (isLandscape) 4 else 3
                WindowWidthClass.MEDIUM -> if (isLandscape) 5 else 4
                WindowWidthClass.EXPANDED -> 6
            }

            val isDualPaneEligible = widthClass == WindowWidthClass.EXPANDED || (widthClass == WindowWidthClass.MEDIUM && isLandscape)

            val padding = when (widthClass) {
                WindowWidthClass.COMPACT -> 16.dp
                WindowWidthClass.MEDIUM -> 24.dp
                WindowWidthClass.EXPANDED -> 32.dp
            }

            return AdaptiveLayoutMetrics(
                widthClass = widthClass,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                isLandscape = isLandscape,
                gridColumns = gridColumns,
                isDualPaneEligible = isDualPaneEligible,
                contentPaddingHorizontal = padding
            )
        }
    }
}

/**
 * Composition helper providing reactive AdaptiveLayoutMetrics for the current configuration.
 */
@Composable
fun rememberAdaptiveLayoutMetrics(): AdaptiveLayoutMetrics {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    return AdaptiveLayoutMetrics.calculate(screenWidth, screenHeight)
}
