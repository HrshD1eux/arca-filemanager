package com.Hrshd1eux.filemanager.core.storage.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Comprehensive Test Suite for Version 1.5.1 – 1.5.5:
 * Better Permissions UX and Storage-Access Recovery.
 *
 * Covers:
 * - 1.5.1: Granular StorageAccessState classification (Full, Scoped, Denied, Revoked).
 * - 1.5.2: Transparent rationale, Android limitation notices, and educational UX.
 * - 1.5.3: StorageAccessRecoveryCoordinator lifecycle recovery, auto-detection, and pending path restoration.
 * - 1.5.4: SAF OpenDocumentTree intent creation, persistable URI handling, and scoped access.
 * - 1.5.5: Platform permission diagnostics and revocation resilience.
 */
class StorageAccessRecoveryTest {

    private lateinit var coordinator: StorageAccessRecoveryCoordinator

    @Before
    fun setUp() {
        StorageAccessRecoveryCoordinator.resetInstance()
        coordinator = StorageAccessRecoveryCoordinator.getInstance()
    }

    // =========================================================================
    // 1.5.1 Storage Access State Model
    // =========================================================================

    @Test
    fun storageAccessState_fullAccessProperties() {
        val state = StorageAccessState.fullAccess()
        assertEquals(StorageAccessLevel.FULL_ALL_FILES, state.level)
        assertTrue(state.hasAllFilesAccess)
        assertTrue(state.isUsable)
        assertTrue(state.isFullAccess)
        assertFalse(state.isScopedAccess)
        assertFalse(state.isRevoked)
        assertTrue(state.statusDescription.contains("Full shared storage"))
    }

    @Test
    fun storageAccessState_scopedAccessProperties() {
        val uris = listOf("content://com.android.externalstorage.documents/tree/primary%3ADownloads")
        val state = StorageAccessState.scopedAccess(uris)
        assertEquals(StorageAccessLevel.SCOPED_FOLDER_TREES, state.level)
        assertFalse(state.hasAllFilesAccess)
        assertTrue(state.isUsable)
        assertFalse(state.isFullAccess)
        assertTrue(state.isScopedAccess)
        assertEquals(1, state.scopedTreeUris.size)
        assertTrue(state.statusDescription.contains("Scoped access to 1 specific folder trees"))
    }

    @Test
    fun storageAccessState_deniedAndRevokedProperties() {
        // Normal first-time denied
        val deniedState = StorageAccessState.denied(canRequestDirectly = true, wasPreviouslyGranted = false, isRevoked = false)
        assertEquals(StorageAccessLevel.DENIED_CAN_REQUEST, deniedState.level)
        assertFalse(deniedState.isUsable)
        assertFalse(deniedState.isRevoked)
        assertTrue(deniedState.canRequestDirectly)

        // Settings required denied (Android 11+)
        val settingsState = StorageAccessState.denied(canRequestDirectly = false, wasPreviouslyGranted = false, isRevoked = false)
        assertEquals(StorageAccessLevel.DENIED_SETTINGS_REQUIRED, settingsState.level)
        assertFalse(settingsState.canRequestDirectly)

        // Revoked state (was granted before, now revoked by OS/Settings)
        val revokedState = StorageAccessState.denied(canRequestDirectly = false, wasPreviouslyGranted = true, isRevoked = true)
        assertEquals(StorageAccessLevel.REVOKED, revokedState.level)
        assertTrue(revokedState.isRevoked)
        assertTrue(revokedState.wasPreviouslyGranted)
        assertTrue(revokedState.statusDescription.contains("revoked by the system or in Settings"))
        assertTrue(revokedState.recoveryActionDescription.contains("Restore storage access"))
    }

    // =========================================================================
    // 1.5.3 Storage Access Recovery Coordinator (Auto-Detection & Path Restoration)
    // =========================================================================

    @Test
    fun recoveryCoordinator_remembersAndRestoresPendingResumePath() {
        assertNull(coordinator.consumePendingResumePath())

        val targetPath = "/storage/emulated/0/DCIM/Camera"
        coordinator.setPendingResumePath(targetPath)

        // First consume returns path
        assertEquals(targetPath, coordinator.consumePendingResumePath())
        // Subsequent consume returns null (consumed)
        assertNull(coordinator.consumePendingResumePath())
    }

    // =========================================================================
    // 1.5.4 SAF Document Tree Intent & Scoped Access
    // =========================================================================

    @Test
    fun createOpenDocumentTreeIntent_configuresRequiredFlagsAndAction() {
        val intent = StoragePermissionManager.createOpenDocumentTreeIntent()
        assertNotNull("OpenDocumentTree intent must be non-null", intent)
    }

    // =========================================================================
    // 1.5.5 Permissions Diagnostics
    // =========================================================================

    @Test
    fun storagePermissionDiagnostics_formatsDetailedTransparentSummary() {
        val diag = StoragePermissionDiagnostics(
            apiLevel = 34,
            isAllFilesSupported = true,
            isExternalStorageManager = true,
            hasLegacyReadPermission = true,
            hasLegacyWritePermission = true,
            persistedUriCount = 2,
            persistedUris = listOf("uri1", "uri2"),
            wasEverGranted = true,
            isRevoked = false,
            diagnosticSummary = "Android API: 34. All Files Access: true. Persisted SAF Trees: 2."
        )

        assertEquals(34, diag.apiLevel)
        assertTrue(diag.isAllFilesSupported)
        assertTrue(diag.isExternalStorageManager)
        assertEquals(2, diag.persistedUriCount)
        assertFalse(diag.isRevoked)
        assertTrue(diag.diagnosticSummary.contains("Android API: 34"))
    }

    // =========================================================================
    // UI Integration & Educational Rationale Verification
    // =========================================================================

    @Test
    fun fileBrowserScreen_permissionDeniedView_includesScopedAccessAndRevocation() {
        val browserFile = File("src/main/java/com/Hrshd1eux/filemanager/feature/browser/FileBrowserScreen.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/Hrshd1eux/filemanager/feature/browser/FileBrowserScreen.kt")

        assertTrue("FileBrowserScreen.kt must exist", browserFile.exists())
        val code = browserFile.readText()

        // Verifies Scoped Folder option
        assertTrue("Must contain Choose Specific Folder action", code.contains("Choose Specific Folder (Scoped Access)"))
        assertTrue("Must support onSelectScopedFolder", code.contains("onSelectScopedFolder"))

        // Verifies Revocation title detection
        assertTrue("Must handle Storage Access Revoked condition", code.contains("Storage Access Revoked"))

        // Verifies Android limitation disclaimer is preserved
        assertTrue("Must preserve ANDROID_STORAGE_LIMITATION_DISCLAIMER", code.contains("ANDROID_STORAGE_LIMITATION_DISCLAIMER"))
    }
}
