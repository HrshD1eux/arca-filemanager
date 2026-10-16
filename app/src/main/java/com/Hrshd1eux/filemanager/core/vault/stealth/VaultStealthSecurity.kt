package com.Hrshd1eux.filemanager.core.vault.stealth

import com.Hrshd1eux.filemanager.core.crypto.random.SecureRandomGenerator
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utility for securing the Private Vault Stealth Mode trigger code.
 * Ensures that secret codes (including emojis and symbols) are stored strictly encrypted
 * and hashed with a cryptographic salt, preventing plaintext exposure on disk or memory dumps.
 */
object VaultStealthSecurity {

    const val DEFAULT_TRIGGER_CODE = "#vault"
    private const val AES_GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private val INTERNAL_SALT = "ARCA_STEALTH_SECRET_SALT_2026_KDF".toByteArray(Charsets.UTF_8)

    data class StealthCredentials(
        val saltHex: String,
        val hashHex: String,
        val encryptedPayloadHex: String
    )

    /**
     * Computes a salted SHA-256 hash of the trigger code.
     */
    fun computeHash(code: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(code.toByteArray(Charsets.UTF_8))
    }

    /**
     * Generates a new random cryptographic salt and returns the salted hash
     * plus AES-256-GCM encrypted payload for the trigger code.
     */
    fun createStealthCredentials(code: String): StealthCredentials {
        val cleanCode = code.trim().ifEmpty { DEFAULT_TRIGGER_CODE }
        val salt = SecureRandomGenerator.generateSalt(16)
        val hash = computeHash(cleanCode, salt)
        val encryptedPayload = encryptCode(cleanCode, salt)

        return StealthCredentials(
            saltHex = salt.toHexString(),
            hashHex = hash.toHexString(),
            encryptedPayloadHex = encryptedPayload
        )
    }

    /**
     * Verifies if a user's input query matches the configured secret trigger code in constant time.
     */
    fun verifyTriggerCode(
        input: String,
        saltHex: String,
        hashHex: String
    ): Boolean {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) return false

        // If no credentials configured yet, check default
        if (saltHex.isBlank() || hashHex.isBlank()) {
            return cleanInput == DEFAULT_TRIGGER_CODE
        }

        return try {
            val salt = hexToBytes(saltHex)
            val expectedHash = hexToBytes(hashHex)
            val actualHash = computeHash(cleanInput, salt)
            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypts the code using AES-256-GCM authenticated encryption.
     */
    fun encryptCode(code: String, salt: ByteArray): String {
        val iv = SecureRandomGenerator.generateGcmNonce(GCM_IV_LENGTH)
        val key = deriveKey(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(AES_GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertextWithTag = cipher.doFinal(code.toByteArray(Charsets.UTF_8))
        return "${iv.toHexString()}:${ciphertextWithTag.toHexString()}"
    }

    /**
     * Decrypts the code using AES-256-GCM to display in the settings dialog when the vault is unlocked.
     */
    fun decryptCode(encryptedPayload: String, saltHex: String): String {
        if (encryptedPayload.isBlank() || saltHex.isBlank()) {
            return DEFAULT_TRIGGER_CODE
        }

        return try {
            val parts = encryptedPayload.split(":")
            if (parts.size != 2) return DEFAULT_TRIGGER_CODE
            val iv = hexToBytes(parts[0])
            val ciphertextWithTag = hexToBytes(parts[1])
            val salt = hexToBytes(saltHex)
            val key = deriveKey(salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(AES_GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plaintext = cipher.doFinal(ciphertextWithTag)
            String(plaintext, Charsets.UTF_8)
        } catch (_: Exception) {
            DEFAULT_TRIGGER_CODE
        }
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(INTERNAL_SALT)
        md.update(salt)
        val keyBytes = md.digest()
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
