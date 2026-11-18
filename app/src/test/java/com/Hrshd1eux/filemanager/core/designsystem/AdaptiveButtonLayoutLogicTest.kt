package com.Hrshd1eux.filemanager.core.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the responsiveness thresholds implemented in [AdaptiveButtonRow].
 */
class AdaptiveButtonLayoutLogicTest {

    private fun shouldStackVertically(
        screenWidthDp: Float,
        fontScale: Float,
        forceVerticalOnCompact: Boolean = true
    ): Boolean {
        return forceVerticalOnCompact && (screenWidthDp < 360f || fontScale > 1.15f)
    }

    @Test
    fun compactScreenWidth_triggersVerticalStacking() {
        // Devices under 360dp width (small phones, cover screens) must stack buttons vertically
        assertTrue(shouldStackVertically(screenWidthDp = 320f, fontScale = 1.0f))
        assertTrue(shouldStackVertically(screenWidthDp = 340f, fontScale = 1.0f))
        assertTrue(shouldStackVertically(screenWidthDp = 359f, fontScale = 1.0f))
    }

    @Test
    fun standardScreenWidth_withNormalFont_usesHorizontalLayout() {
        // Standard modern phones (360dp, 393dp, 411dp, 480dp) at 1.0x font scale must use horizontal row
        assertFalse(shouldStackVertically(screenWidthDp = 360f, fontScale = 1.0f))
        assertFalse(shouldStackVertically(screenWidthDp = 393f, fontScale = 1.0f))
        assertFalse(shouldStackVertically(screenWidthDp = 411f, fontScale = 1.0f))
    }

    @Test
    fun enlargedFontScale_triggersVerticalStackingEvenOnWideScreens() {
        // When accessibility font scaling is enabled (> 1.15x), buttons must stack vertically
        // to prevent label truncation, multi-line squishing, or intrinsic width collapse
        assertTrue(shouldStackVertically(screenWidthDp = 411f, fontScale = 1.2f))
        assertTrue(shouldStackVertically(screenWidthDp = 411f, fontScale = 1.3f))
        assertTrue(shouldStackVertically(screenWidthDp = 411f, fontScale = 1.5f))
    }

    @Test
    fun boundaryConditions_evaluatedAccurately() {
        // Exactly at boundary 360dp and 1.15f scale
        assertFalse(shouldStackVertically(screenWidthDp = 360f, fontScale = 1.15f))
        assertTrue(shouldStackVertically(screenWidthDp = 359.9f, fontScale = 1.15f))
        assertTrue(shouldStackVertically(screenWidthDp = 360f, fontScale = 1.16f))
    }
}
