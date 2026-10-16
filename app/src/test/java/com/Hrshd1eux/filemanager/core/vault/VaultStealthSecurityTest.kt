package com.Hrshd1eux.filemanager.core.vault

import com.Hrshd1eux.filemanager.core.vault.stealth.VaultStealthSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultStealthSecurityTest {

    @Test
    fun defaultTriggerCode_isHashAndVerifiedCorrectly() {
        val credentials = VaultStealthSecurity.createStealthCredentials(VaultStealthSecurity.DEFAULT_TRIGGER_CODE)
        assertTrue(credentials.saltHex.isNotEmpty())
        assertTrue(credentials.hashHex.isNotEmpty())
        assertTrue(credentials.encryptedPayloadHex.isNotEmpty())

        assertTrue(
            VaultStealthSecurity.verifyTriggerCode(
                input = "#vault",
                saltHex = credentials.saltHex,
                hashHex = credentials.hashHex
            )
        )

        assertFalse(
            VaultStealthSecurity.verifyTriggerCode(
                input = "#wrongcode",
                saltHex = credentials.saltHex,
                hashHex = credentials.hashHex
            )
        )
    }

    @Test
    fun customTriggerCode_emojisAndSymbols_supportedAndEncrypted() {
        val emojiCode = "🔐##secret_vault"
        val credentials = VaultStealthSecurity.createStealthCredentials(emojiCode)

        // Verify hash verification
        assertTrue(
            VaultStealthSecurity.verifyTriggerCode(
                input = emojiCode,
                saltHex = credentials.saltHex,
                hashHex = credentials.hashHex
            )
        )
        assertFalse(
            VaultStealthSecurity.verifyTriggerCode(
                input = "🔐##secret_wrong",
                saltHex = credentials.saltHex,
                hashHex = credentials.hashHex
            )
        )

        // Verify reversible AES-GCM decryption
        val decrypted = VaultStealthSecurity.decryptCode(
            encryptedPayload = credentials.encryptedPayloadHex,
            saltHex = credentials.saltHex
        )
        assertEquals(emojiCode, decrypted)
    }

    @Test
    fun pureEmojiCode_correctlyEncryptedAndVerified() {
        val emojiCode = "🤫🔒✨"
        val credentials = VaultStealthSecurity.createStealthCredentials(emojiCode)

        assertTrue(
            VaultStealthSecurity.verifyTriggerCode(
                input = emojiCode,
                saltHex = credentials.saltHex,
                hashHex = credentials.hashHex
            )
        )

        val decrypted = VaultStealthSecurity.decryptCode(
            encryptedPayload = credentials.encryptedPayloadHex,
            saltHex = credentials.saltHex
        )
        assertEquals(emojiCode, decrypted)
    }

    @Test
    fun zeroPlaintextStorage_ciphertextDiffersFromPlaintext() {
        val secret = "mySuperSecretCode123!"
        val credentials = VaultStealthSecurity.createStealthCredentials(secret)

        assertNotEquals(secret, credentials.encryptedPayloadHex)
        assertFalse(credentials.encryptedPayloadHex.contains(secret))
        assertFalse(credentials.hashHex.contains(secret))
    }

    @Test
    fun verifyTriggerCode_handlesEmptyOrCorruptedInputGracefully() {
        val credentials = VaultStealthSecurity.createStealthCredentials("#vault")

        assertFalse(
            VaultStealthSecurity.verifyTriggerCode(
                input = "",
                saltHex = credentials.saltHex,
                hashHex = credentials.hashHex
            )
        )
        assertFalse(
            VaultStealthSecurity.verifyTriggerCode(
                input = "   ",
                saltHex = credentials.saltHex,
                hashHex = credentials.hashHex
            )
        )
        assertFalse(
            VaultStealthSecurity.verifyTriggerCode(
                input = "#vault",
                saltHex = "corrupted_hex",
                hashHex = credentials.hashHex
            )
        )
    }
}
