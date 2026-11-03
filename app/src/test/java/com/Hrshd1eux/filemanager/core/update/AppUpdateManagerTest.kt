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
    fun isNewerVersion_handlesVPrefixAndSuffixes() {
        assertTrue(AppUpdateManager.isNewerVersion("1.6.12", "1.6.13-beta1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.6.13", "1.6.12-rc2"))
    }

    @Test
    fun isNewerVersion_handlesEmptyOrBlank() {
        assertFalse(AppUpdateManager.isNewerVersion("", "1.6.13"))
        assertFalse(AppUpdateManager.isNewerVersion("1.6.12", ""))
    }
}
