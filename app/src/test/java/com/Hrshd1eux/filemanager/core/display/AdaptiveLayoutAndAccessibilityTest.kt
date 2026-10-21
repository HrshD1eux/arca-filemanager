package com.Hrshd1eux.filemanager.core.display

import androidx.compose.ui.unit.dp
import com.Hrshd1eux.filemanager.core.display.accessibility.AccessibilitySemantics
import com.Hrshd1eux.filemanager.core.display.layout.AdaptiveLayoutMetrics
import com.Hrshd1eux.filemanager.core.display.layout.WindowWidthClass
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.permission.StorageAccessLevel
import com.Hrshd1eux.filemanager.core.storage.permission.StorageAccessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rigorous Unit Test Suite for Version 1.5.31 – 1.5.35:
 * Accessibility, RTL, Landscape, Tablets, and Foldables.
 */
class AdaptiveLayoutAndAccessibilityTest {

    // =========================================================================
    // 1.5.31 Adaptive Window Width & Form Factor Classification
    // =========================================================================

    @Test
    fun adaptiveMetrics_compactPortraitPhone_calculatesCorrectLayout() {
        val metrics = AdaptiveLayoutMetrics.calculate(screenWidthDp = 390.dp, screenHeightDp = 844.dp)

        assertEquals(WindowWidthClass.COMPACT, metrics.widthClass)
        assertFalse(metrics.isLandscape)
        assertEquals(3, metrics.gridColumns)
        assertFalse(metrics.isDualPaneEligible)
        assertEquals(16.dp, metrics.contentPaddingHorizontal)
    }

    @Test
    fun adaptiveMetrics_compactLandscapePhone_calculatesCorrectLayout() {
        val metrics = AdaptiveLayoutMetrics.calculate(screenWidthDp = 844.dp, screenHeightDp = 390.dp)

        // 844dp width is EXPANDED (>= 840dp)
        assertEquals(WindowWidthClass.EXPANDED, metrics.widthClass)
        assertTrue(metrics.isLandscape)
        assertEquals(6, metrics.gridColumns)
        assertTrue(metrics.isDualPaneEligible)
    }

    @Test
    fun adaptiveMetrics_mediumFoldableOrTablet_calculatesCorrectLayout() {
        // Unfolded foldable in portrait (e.g. 720dp x 900dp)
        val metricsPortrait = AdaptiveLayoutMetrics.calculate(screenWidthDp = 720.dp, screenHeightDp = 900.dp)
        assertEquals(WindowWidthClass.MEDIUM, metricsPortrait.widthClass)
        assertFalse(metricsPortrait.isLandscape)
        assertEquals(4, metricsPortrait.gridColumns)
        assertFalse(metricsPortrait.isDualPaneEligible)

        // Unfolded foldable in landscape (e.g. 720dp x 600dp)
        val metricsLandscape = AdaptiveLayoutMetrics.calculate(screenWidthDp = 720.dp, screenHeightDp = 600.dp)
        assertEquals(WindowWidthClass.MEDIUM, metricsLandscape.widthClass)
        assertTrue(metricsLandscape.isLandscape)
        assertEquals(5, metricsLandscape.gridColumns)
        assertTrue(metricsLandscape.isDualPaneEligible)
    }

    @Test
    fun adaptiveMetrics_expandedLargeTablet_calculatesDualPaneLayout() {
        val metrics = AdaptiveLayoutMetrics.calculate(screenWidthDp = 1024.dp, screenHeightDp = 768.dp)

        assertEquals(WindowWidthClass.EXPANDED, metrics.widthClass)
        assertTrue(metrics.isLandscape)
        assertEquals(6, metrics.gridColumns)
        assertTrue(metrics.isDualPaneEligible)
        assertEquals(32.dp, metrics.contentPaddingHorizontal)
    }

    // =========================================================================
    // 1.5.32 Accessibility Touch Targets & Standards
    // =========================================================================

    @Test
    fun touchTargetStandard_meetsWcagLevelAaMinimum48Dp() {
        assertTrue(
            "Minimum touch target must be at least 48dp",
            AccessibilitySemantics.MIN_TOUCH_TARGET_SIZE >= 48.dp
        )
    }

    // =========================================================================
    // 1.5.33 TalkBack Semantic Formatter & Labels
    // =========================================================================

    @Test
    fun formatNodeLabel_formatsDescriptiveTalkBackLabelForDirectory() {
        val folderNode = StorageNode(
            id = "dir_1",
            path = "/sdcard/Documents",
            name = "Documents",
            isDirectory = true,
            size = 0L,
            lastModified = 1729000000000L
        )

        val label = AccessibilitySemantics.formatNodeLabel(folderNode)
        assertTrue(label.contains("Documents"))
        assertTrue(label.contains("folder"))
        assertTrue(label.contains("modified"))
    }

    @Test
    fun formatNodeLabel_formatsDescriptiveTalkBackLabelForFile() {
        val fileNode = StorageNode(
            id = "file_1",
            path = "/sdcard/Download/photo.jpg",
            name = "photo.jpg",
            isDirectory = false,
            size = 2_500_000L,
            lastModified = 1729000000000L
        )

        val label = AccessibilitySemantics.formatNodeLabel(fileNode)
        assertTrue(label.contains("photo.jpg"))
        assertTrue(label.contains("jpg file"))
        assertTrue(label.contains("MB") || label.contains("2.38") || label.contains("2.5"))
    }

    @Test
    fun formatStorageAccessDescription_announcesGranularStateAndRevocation() {
        val revokedState = StorageAccessState(
            level = StorageAccessLevel.REVOKED,
            hasAllFilesAccess = false,
            isRevoked = true
        )

        val desc = AccessibilitySemantics.formatStorageAccessDescription(revokedState)
        assertTrue(desc.contains("Storage access status"))
        assertTrue(desc.contains("revoked"))
    }

    @Test
    fun formatProgressAnnouncement_announcesPercentageAndBytes() {
        val announcement = AccessibilitySemantics.formatProgressAnnouncement(
            operationName = "Copying",
            percent = 45,
            processedBytes = 450_000_000L,
            totalBytes = 1_000_000_000L
        )

        assertTrue(announcement.contains("Copying: 45 percent complete"))
        assertTrue(announcement.contains("transferred"))
    }
}
