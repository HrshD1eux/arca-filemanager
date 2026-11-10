package com.Hrshd1eux.filemanager.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun isNewerVersion_detectsHigherPatch() {
        assertTrue(AppUpdateManager.isNewerVersion("1.6.12", "1.6.13"))
        assertFalse(AppUpdateManager.isNewerVersion("1.6.13", "1.6.12"))
    }

    @Test
    fun isNewerVersion_detectsHigherMinor() {
        assertTrue(AppUpdateManager.isNewerVersion("1.6.12", "1.7.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.7.0", "1.6.12"))
    }

    @Test
    fun isNewerVersion_detectsHigherMajor() {
        assertTrue(AppUpdateManager.isNewerVersion("1.6.12", "2.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("2.0.0", "1.6.12"))
    }

    @Test
    fun isNewerVersion_sameVersion_returnsFalse() {
        assertFalse(AppUpdateManager.isNewerVersion("1.6.12", "1.6.12"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.6.12", "v1.6.12"))
    }

    @Test
    fun isNewerVersion_sameVersionWithDifferentPrefixes_returnsFalse() {
        assertFalse(AppUpdateManager.isNewerVersion("1.6.19", "v1.6.19"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.6.19", "1.6.19"))
        assertFalse(AppUpdateManager.isNewerVersion("1.6.19-debug", "1.6.19"))
        assertFalse(AppUpdateManager.isNewerVersion("1.6.19", "1.6.19-debug"))
    }

    @Test
    fun isNewerVersion_handlesVPrefixAndSuffixes() {
        assertTrue(AppUpdateManager.isNewerVersion("1.6.12", "1.6.13-beta1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.6.13", "1.6.12-rc2"))
    }

    @Test
    fun isNewerVersion_handlesEmptyOrBlank() {
        assertFalse(AppUpdateManager.isNewerVersion("", "1.6.13"))
        assertFalse(AppUpdateManager.isNewerVersion("1.6.12", ""))
    }

    @Test
    fun oncePerVersionUpdatePrompt_logicValidation() {
        val currentVersion = "1.6.21"
        val latestRemote = "1.6.22"

        // 1. Initial check: update is available
        assertTrue(AppUpdateManager.isNewerVersion(currentVersion, latestRemote))

        // 2. First prompt: lastPrompted is null, prompt is permitted
        var lastPrompted: String? = null
        val shouldPromptFirstTime = AppUpdateManager.isNewerVersion(currentVersion, latestRemote) && lastPrompted != latestRemote
        assertTrue(shouldPromptFirstTime)

        // 3. User was prompted: record version
        lastPrompted = latestRemote

        // 4. Second app launch with same remote: must NOT prompt again
        val shouldPromptSecondTime = AppUpdateManager.isNewerVersion(currentVersion, latestRemote) && lastPrompted != latestRemote
        assertFalse(shouldPromptSecondTime)

        // 5. Future release (v1.6.23 published): should prompt once again
        val futureRemote = "1.6.23"
        val shouldPromptFutureRelease = AppUpdateManager.isNewerVersion(currentVersion, futureRemote) && lastPrompted != futureRemote
        assertTrue(shouldPromptFutureRelease)
    }
}
