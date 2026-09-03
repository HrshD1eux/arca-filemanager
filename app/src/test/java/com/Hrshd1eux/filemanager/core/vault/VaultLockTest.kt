package com.Hrshd1eux.filemanager.core.vault

import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.vault.lock.VaultAutoLockTimeout
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultLockTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var vaultManager: VaultManager
    private lateinit var vaultLockManager: VaultLockManager
    private lateinit var vaultDir: File

    @Before
    fun setUp() {
        vaultDir = tempFolder.newFolder("test_vault")
        vaultManager = VaultManager(
            vaultDir = vaultDir,
            cryptoEngine = CryptoEngine(ioDispatcher = Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
        vaultLockManager = VaultLockManager(
            context = null,
            vaultManager = vaultManager
        )
    }

    @Test
    fun autoLockTimeouts_durationVerification() {
        assertEquals(0L, VaultAutoLockTimeout.IMMEDIATELY.durationMs)
        assertEquals(60_000L, VaultAutoLockTimeout.ONE_MINUTE.durationMs)
        assertEquals(300_000L, VaultAutoLockTimeout.FIVE_MINUTES.durationMs)
        assertEquals(900_000L, VaultAutoLockTimeout.FIFTEEN_MINUTES.durationMs)
        assertEquals(-1L, VaultAutoLockTimeout.NEVER.durationMs)
    }

    @Test
    fun manualLock_locksVaultImmediately() = runTest {
        val password = "TestPassword123!".toCharArray()
        vaultManager.createVault(password)
        assertTrue(vaultManager.isUnlocked())

        vaultLockManager.manualLock()
        assertFalse(vaultManager.isUnlocked())
    }

    @Test
    fun screenOffLock_triggersLockWhenEnabled() = runTest {
        val password = "TestPassword123!".toCharArray()
        vaultManager.createVault(password)
        assertTrue(vaultManager.isUnlocked())

        // 1. When enabled -> locks
        vaultLockManager.updateSettings(VaultLockSettings(lockOnScreenOff = true))
        vaultLockManager.onScreenOff()
        assertFalse(vaultManager.isUnlocked())

        // 2. Unlock again
        vaultManager.unlock("TestPassword123!".toCharArray())
        assertTrue(vaultManager.isUnlocked())

        // 3. When disabled -> does not lock
        vaultLockManager.updateSettings(VaultLockSettings(lockOnScreenOff = false))
        vaultLockManager.onScreenOff()
        assertTrue(vaultManager.isUnlocked())
    }

    @Test
    fun backgroundLock_immediatelyTriggersWhenConfigured() = runTest {
        val password = "TestPassword123!".toCharArray()
        vaultManager.createVault(password)
        assertTrue(vaultManager.isUnlocked())

        vaultLockManager.updateSettings(
            VaultLockSettings(
                autoLockTimeout = VaultAutoLockTimeout.IMMEDIATELY,
                lockOnBackground = true
            )
        )

        vaultLockManager.onAppBackgrounded()
        assertFalse(vaultManager.isUnlocked())
    }

    @Test
    fun userInteraction_updatesTimestamp() {
        val initial = vaultLockManager.lastInteractionTimestamp
        Thread.sleep(10)
        vaultLockManager.onUserInteraction()
        assertTrue(vaultLockManager.lastInteractionTimestamp >= initial)
    }
}
