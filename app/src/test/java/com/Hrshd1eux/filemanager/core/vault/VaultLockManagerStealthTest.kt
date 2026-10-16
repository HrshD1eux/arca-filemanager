package com.Hrshd1eux.filemanager.core.vault

import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import com.Hrshd1eux.filemanager.core.vault.stealth.VaultStealthSecurity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultLockManagerStealthTest {

    private lateinit var lockManager: VaultLockManager

    @Before
    fun setUp() {
        lockManager = VaultLockManager(context = null, vaultManager = null)
    }

    @Test
    fun matchesSecretCode_whenVaultNotHidden_alwaysReturnsFalse() {
        lockManager.updateSettings(
            VaultLockSettings(
                isVaultHidden = false,
                secretCodeSalt = "",
                secretCodeHash = "",
                encryptedSecretCode = ""
            )
        )

        assertFalse(lockManager.matchesSecretCode("#vault"))
        assertFalse(lockManager.matchesSecretCode("🔐"))
    }

    @Test
    fun matchesSecretCode_whenVaultHidden_matchesDefaultCode() {
        lockManager.updateSettings(
            VaultLockSettings(
                isVaultHidden = true,
                secretCodeSalt = "",
                secretCodeHash = "",
                encryptedSecretCode = ""
            )
        )

        assertTrue(lockManager.matchesSecretCode("#vault"))
        assertFalse(lockManager.matchesSecretCode("random_query"))
    }

    @Test
    fun matchesSecretCode_whenVaultHidden_matchesCustomCode() {
        val secretCode = "🔐##secret_vault"
        val credentials = VaultStealthSecurity.createStealthCredentials(secretCode)

        lockManager.updateSettings(
            VaultLockSettings(
                isVaultHidden = true,
                secretCodeSalt = credentials.saltHex,
                secretCodeHash = credentials.hashHex,
                encryptedSecretCode = credentials.encryptedPayloadHex
            )
        )

        assertTrue(lockManager.matchesSecretCode(secretCode))
        assertFalse(lockManager.matchesSecretCode("#vault"))
        assertFalse(lockManager.matchesSecretCode("secret_vault"))
    }

    @Test
    fun matchesSecretCode_ignoresLeadingAndTrailingWhitespace() {
        val secretCode = "*#open#*"
        val credentials = VaultStealthSecurity.createStealthCredentials(secretCode)

        lockManager.updateSettings(
            VaultLockSettings(
                isVaultHidden = true,
                secretCodeSalt = credentials.saltHex,
                secretCodeHash = credentials.hashHex,
                encryptedSecretCode = credentials.encryptedPayloadHex
            )
        )

        assertTrue(lockManager.matchesSecretCode("  *#open#*  "))
        assertFalse(lockManager.matchesSecretCode("open"))
    }
}
