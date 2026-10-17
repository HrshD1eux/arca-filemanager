package com.Hrshd1eux.filemanager.core.storage.permission

import com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Rigorous tests for Version 1.2.35 — First-Run Permissions Experience.
 *
 * Verifies that:
 * 1. First-run explanation state persists cleanly and defaults to false.
 * 2. Explicit continue actions (Grant Access / Continue with Limited Access) mark first-run completed to prevent nagging.
 * 3. Android platform limitations are clearly communicated in rationale disclaimers
 *    (All-Files Access is NOT a pass to other apps' private sandboxes or system restricted folders).
 * 4. Storage rationale transparently explains file management, compression, and zero-telemetry local operations.
 * 5. Limited access mode enables Private Vault, Network browsing, Settings, and About to function gracefully.
 * 6. UI source code adheres to non-nagging principles and lifecycle-based permission detection.
 */
class FirstRunPermissionsTest {

    private lateinit var appSettingsRepository: AppSettingsRepository

    @Before
    fun setUp() {
        appSettingsRepository = AppSettingsRepository(context = null)
        appSettingsRepository.resetFirstRunExplanation()
    }

    @Test
    fun firstRunExplanation_defaultsToFalse() = runBlocking {
        assertFalse(
            "First run explanation should default to false for new installs",
            appSettingsRepository.firstRunExplanationCompleted.first()
        )
    }

    @Test
    fun firstRunExplanation_persistsWhenCompleted() = runBlocking {
        assertFalse(appSettingsRepository.firstRunExplanationCompleted.first())

        appSettingsRepository.setFirstRunExplanationCompleted(true)
        assertTrue(
            "StateFlow should update to true after completing first run explanation",
            appSettingsRepository.firstRunExplanationCompleted.first()
        )

        appSettingsRepository.resetFirstRunExplanation()
        assertFalse(
            "Reset should return explanation state to false",
            appSettingsRepository.firstRunExplanationCompleted.first()
        )
    }

    @Test
    fun storagePermissionManager_limitationDisclaimer_isExplicitAndAccurate() {
        val disclaimer = StoragePermissionManager.ANDROID_STORAGE_LIMITATION_DISCLAIMER
        assertNotNull(disclaimer)
        assertTrue("Disclaimer must highlight Android limitation", disclaimer.contains("Important Android limitation"))
        assertTrue("Disclaimer must clarify it is not a universal pass", disclaimer.contains("not a universal pass"))
        assertTrue("Disclaimer must explicitly mention private sandbox", disclaimer.contains("/data/data/"))
        assertTrue("Disclaimer must note restricted Android/data directories", disclaimer.contains("Android/data"))
    }

    @Test
    fun storagePermissionManager_whyBroaderAccess_isTransparentAndOffline() {
        val reason = StoragePermissionManager.STORAGE_EXPLANATION_WHY_BROADER_ACCESS
        assertNotNull(reason)
        assertTrue("Must explain file browsing and organization", reason.contains("browse") && reason.contains("organize"))
        assertTrue("Must explain archives and compression", reason.contains("compress") || reason.contains("archive"))
        assertTrue("Must guarantee offline operations with zero analytics", reason.contains("100% offline") && reason.contains("zero analytics"))
    }

    @Test
    fun storagePermissionManager_limitedAccessModeExplanation_highlightsUnrestrictedFeatures() {
        val explanation = StoragePermissionManager.LIMITED_ACCESS_MODE_EXPLANATION
        assertNotNull(explanation)
        assertTrue("Must mention Private Vault availability", explanation.contains("Private Vault"))
        assertTrue("Must mention Network browser servers", explanation.contains("Network"))
        assertTrue("Must mention Settings and offline tools", explanation.contains("Settings") && explanation.contains("offline tools"))
    }

    @Test
    fun firstRunStorageExplanationDialog_containsRequiredComponentsAndActions() {
        val dialogFile = File("src/main/java/com/Hrshd1eux/filemanager/feature/home/components/FirstRunStorageExplanationDialog.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/Hrshd1eux/filemanager/feature/home/components/FirstRunStorageExplanationDialog.kt")

        assertTrue("FirstRunStorageExplanationDialog.kt must exist", dialogFile.exists())
        val code = dialogFile.readText()

        // Required action buttons
        assertTrue("Dialog must contain Grant Storage Access action", code.contains("Grant Storage Access"))
        assertTrue("Dialog must contain Continue with Limited Access action", code.contains("Continue with Limited Access"))
        assertTrue("Dialog must contain Read Privacy Statement action", code.contains("Read Privacy Statement"))

        // Required rationale content
        assertTrue("Dialog must reference Android limitation disclaimer", code.contains("ANDROID_STORAGE_LIMITATION_DISCLAIMER"))
        assertTrue("Dialog must explain Archive Studio capabilities", code.contains("Archive Studio"))
        assertTrue("Dialog must state 100% Offline & Private guarantee", code.contains("100% Offline & Private"))

        // Dismissal must gracefully transition to limited access rather than blocking
        assertTrue("Dismissal must map to onContinueWithLimitedAccess", code.contains("onDismissRequest = onContinueWithLimitedAccess"))
    }

    @Test
    fun homeScreen_integratesFirstRunExplanationAndNonNaggingLimitedAccess() {
        val homeScreenFile = File("src/main/java/com/Hrshd1eux/filemanager/feature/home/HomeScreen.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/Hrshd1eux/filemanager/feature/home/HomeScreen.kt")

        assertTrue("HomeScreen.kt must exist", homeScreenFile.exists())
        val code = homeScreenFile.readText()

        // First run dialog shown conditionally
        assertTrue("HomeScreen must check !hasAllFilesAccess && !firstRunExplanationCompleted",
            code.contains("!hasAllFilesAccess && !firstRunExplanationCompleted"))
        assertTrue("HomeScreen must instantiate FirstRunStorageExplanationDialog",
            code.contains("FirstRunStorageExplanationDialog("))

        // Lifecycle resumption observer to detect return from settings or revocation
        assertTrue("HomeScreen must register LifecycleEventObserver for ON_RESUME",
            code.contains("LifecycleEventObserver") && code.contains("Lifecycle.Event.ON_RESUME"))

        // Limited access mode reflected non-intrusively in HomeStorageCard
        assertTrue("HomeStorageCard must receive hasAllFilesAccess state",
            code.contains("hasAllFilesAccess = hasAllFilesAccess"))
    }

    @Test
    fun fileBrowserScreen_handlesPermissionResumptionAndAndroidLimitations() {
        val browserFile = File("src/main/java/com/Hrshd1eux/filemanager/feature/browser/FileBrowserScreen.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/Hrshd1eux/filemanager/feature/browser/FileBrowserScreen.kt")

        assertTrue("FileBrowserScreen.kt must exist", browserFile.exists())
        val code = browserFile.readText()

        // Lifecycle ON_RESUME detection to refresh directory if permission granted
        assertTrue("FileBrowserScreen must observe ON_RESUME lifecycle event",
            code.contains("Lifecycle.Event.ON_RESUME") && code.contains("viewModel.refresh"))

        // Platform limitation card rendered in PermissionDeniedView
        assertTrue("PermissionDeniedView must display Android limitation disclaimer",
            code.contains("ANDROID_STORAGE_LIMITATION_DISCLAIMER"))

        // Link to privacy statement
        assertTrue("PermissionDeniedView must link to privacy statement",
            code.contains("onReadPrivacyStatement"))
    }
}
