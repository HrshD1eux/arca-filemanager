package com.Hrshd1eux.filemanager.core.lock

import com.Hrshd1eux.filemanager.core.lock.manager.AppLockManager
import com.Hrshd1eux.filemanager.core.lock.model.AppLockMode
import com.Hrshd1eux.filemanager.core.lock.model.AppLockTimeout
import com.Hrshd1eux.filemanager.core.lock.model.PinUnlockResult
import com.Hrshd1eux.filemanager.core.lock.security.AppLockSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Rigorous Unit & Cryptography Test Suite for Version 1.5.16 – 1.5.20:
 * App Lock, Biometric Authentication, and PIN Fallback Design.
 */
class AppLockSecurityTest {

    private lateinit var appLockManager: AppLockManager

    @Before
    fun setUp() {
        AppLockManager.resetInstance()
        appLockManager = AppLockManager(context = null)
    }

    // =========================================================================
    // 1.5.16 PIN Hashing & Constant-Time Verification
    // =========================================================================

    @Test
    fun hashPin_producesDeterministicAndSaltedOutputs() {
        val salt1 = AppLockSecurity.generateSalt()
        val salt2 = AppLockSecurity.generateSalt()

        val hashA = AppLockSecurity.hashPin("1234", salt1)
        val hashB = AppLockSecurity.hashPin("1234", salt1)
        val hashWithDiffSalt = AppLockSecurity.hashPin("1234", salt2)
        val hashWithDiffPin = AppLockSecurity.hashPin("5678", salt1)

        assertEquals("Same PIN and salt must produce identical hash", hashA, hashB)
        assertNotEquals("Different salt must produce different hash", hashA, hashWithDiffSalt)
        assertNotEquals("Different PIN must produce different hash", hashA, hashWithDiffPin)
    }

    @Test
    fun verifyPin_validatesCorrectPinAndRejectsInvalidOrEmptyPins() {
        val salt = AppLockSecurity.generateSalt()
        val saltHex = AppLockSecurity.byteArrayToHex(salt)
        val hashHex = AppLockSecurity.hashPin("9876", salt)

        assertTrue(AppLockSecurity.verifyPin("9876", saltHex, hashHex))
        assertFalse(AppLockSecurity.verifyPin("9875", saltHex, hashHex))
        assertFalse(AppLockSecurity.verifyPin("0000", saltHex, hashHex))
        assertFalse(AppLockSecurity.verifyPin("", saltHex, hashHex))
        assertFalse(AppLockSecurity.verifyPin("9876", "", hashHex))
        assertFalse(AppLockSecurity.verifyPin("9876", saltHex, ""))
    }

    // =========================================================================
    // 1.5.17 Progressive Rate Limiting & Lockout Schedule
    // =========================================================================

    @Test
    fun calculateLockoutDuration_enforcesProgressiveBackoff() {
        // 0 to 4 failures: No lockout
        for (f in 0..4) {
            assertEquals(0L, AppLockSecurity.calculateLockoutDurationMs(f))
        }

        // 5 to 7 failures: 30 seconds
        assertEquals(30_000L, AppLockSecurity.calculateLockoutDurationMs(5))
        assertEquals(30_000L, AppLockSecurity.calculateLockoutDurationMs(6))
        assertEquals(30_000L, AppLockSecurity.calculateLockoutDurationMs(7))

        // 8 to 9 failures: 2 minutes
        assertEquals(120_000L, AppLockSecurity.calculateLockoutDurationMs(8))
        assertEquals(120_000L, AppLockSecurity.calculateLockoutDurationMs(9))

        // 10+ failures: 5 minutes
        assertEquals(300_000L, AppLockSecurity.calculateLockoutDurationMs(10))
        assertEquals(300_000L, AppLockSecurity.calculateLockoutDurationMs(15))
    }

    // =========================================================================
    // 1.5.18 AppLockManager PIN Authentication & Lockout Behavior
    // =========================================================================

    @Test
    fun unlockWithPin_successfulAuthenticationResetsFailedAttempts() {
        appLockManager.setPin("4321")
        assertTrue(appLockManager.settings.value.isConfigured)

        // Simulate 2 wrong attempts
        val fail1 = appLockManager.unlockWithPin("0000")
        assertTrue(fail1 is PinUnlockResult.InvalidPin)
        assertEquals(4, (fail1 as PinUnlockResult.InvalidPin).attemptsRemainingBeforeLockout)

        val fail2 = appLockManager.unlockWithPin("1111")
        assertTrue(fail2 is PinUnlockResult.InvalidPin)
        assertEquals(3, (fail2 as PinUnlockResult.InvalidPin).attemptsRemainingBeforeLockout)

        assertEquals(2, appLockManager.settings.value.failedAttempts)

        // Successful authentication
        val success = appLockManager.unlockWithPin("4321")
        assertTrue(success is PinUnlockResult.Success)
        assertFalse(appLockManager.isAppLocked.value)
        assertEquals(0, appLockManager.settings.value.failedAttempts)
    }

    @Test
    fun unlockWithPin_fiveConsecutiveFailuresLocksOutUser() {
        appLockManager.setPin("1234")
        val baseTime = 1000_000L

        // Attempt 1 to 4
        for (i in 1..4) {
            val res = appLockManager.unlockWithPin("0000", currentTime = baseTime)
            assertTrue(res is PinUnlockResult.InvalidPin)
        }

        // 5th failure triggers 30s lockout
        val res5 = appLockManager.unlockWithPin("0000", currentTime = baseTime)
        assertTrue(res5 is PinUnlockResult.LockedOut)
        assertEquals(30L, (res5 as PinUnlockResult.LockedOut).remainingSeconds)

        // Attempt during lockout period is rejected immediately even with correct PIN
        val lockedAttempt = appLockManager.unlockWithPin("1234", currentTime = baseTime + 10_000L)
        assertTrue(lockedAttempt is PinUnlockResult.LockedOut)
        assertEquals(20L, (lockedAttempt as PinUnlockResult.LockedOut).remainingSeconds)

        // Attempt after lockout period expires succeeds with correct PIN
        val afterLockout = appLockManager.unlockWithPin("1234", currentTime = baseTime + 31_000L)
        assertTrue(afterLockout is PinUnlockResult.Success)
        assertFalse(appLockManager.isAppLocked.value)
    }

    // =========================================================================
    // 1.5.19 Biometric Unlock with Mandatory PIN Fallback
    // =========================================================================

    @Test
    fun biometricUnlock_succeedsOnlyWhenModeIsBiometricAndNotLockedOut() {
        // Setting PIN in standard PIN mode
        appLockManager.setPin("7777", enableBiometrics = false)
        assertFalse("Biometrics should not unlock if mode is PIN only", appLockManager.unlockWithBiometrics())

        // Enabling biometrics mode
        appLockManager.setPin("7777", enableBiometrics = true)
        assertTrue(appLockManager.settings.value.isBiometricsActive)

        // App is locked
        appLockManager.lockApp()
        assertTrue(appLockManager.isAppLocked.value)

        // Biometrics unlocks app
        assertTrue(appLockManager.unlockWithBiometrics())
        assertFalse(appLockManager.isAppLocked.value)

        // But if locked out, biometrics must fail
        val updated = appLockManager.settings.value.copy(lockoutUntilTimestamp = System.currentTimeMillis() + 60_000L)
        appLockManager.updateSettings(updated)
        assertFalse("Biometrics cannot bypass active brute-force lockout", appLockManager.unlockWithBiometrics())
    }

    // =========================================================================
    // 1.5.20 App Background & Inactivity Timeout Lifecycle
    // =========================================================================

    @Test
    fun onAppForegrounded_enforcesTimeoutDurationCorrectly() {
        appLockManager.setPin("9999")
        appLockManager.updateSettings(appLockManager.settings.value.copy(timeout = AppLockTimeout.ONE_MINUTE))

        // Unlock app
        appLockManager.unlockWithPin("9999")
        assertFalse(appLockManager.isAppLocked.value)

        // App goes to background at t = 100_000
        val t0 = 100_000L
        appLockManager.onAppBackgrounded(timestamp = t0)

        // Resumed after 30 seconds (elapsed < 60s timeout)
        appLockManager.onAppForegrounded(currentTime = t0 + 30_000L)
        assertFalse("Should remain unlocked when elapsed time is within timeout", appLockManager.isAppLocked.value)

        // App goes to background again at t0
        appLockManager.onAppBackgrounded(timestamp = t0)

        // Resumed after 65 seconds (elapsed >= 60s timeout)
        appLockManager.onAppForegrounded(currentTime = t0 + 65_000L)
        assertTrue("Must lock app when elapsed background time exceeds timeout", appLockManager.isAppLocked.value)
    }

    @Test
    fun disableAppLock_requiresValidPin() {
        appLockManager.setPin("5555")
        assertTrue(appLockManager.settings.value.isConfigured)

        // Wrong PIN cannot disable
        assertFalse(appLockManager.disableAppLock("0000"))
        assertTrue(appLockManager.settings.value.isConfigured)

        // Correct PIN disables App Lock
        assertTrue(appLockManager.disableAppLock("5555"))
        assertFalse(appLockManager.settings.value.isConfigured)
        assertEquals(AppLockMode.DISABLED, appLockManager.settings.value.mode)
    }
}
