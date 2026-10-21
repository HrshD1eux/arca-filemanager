package com.Hrshd1eux.filemanager.core.lock.security

import com.Hrshd1eux.filemanager.core.crypto.random.SecureRandomGenerator
import java.security.MessageDigest

/**
 * Security engine for PIN code derivation, constant-time verification,
 * and brute-force rate limiting.
 */
object AppLockSecurity {

    const val MAX_ATTEMPTS_BEFORE_FIRST_LOCKOUT = 5
    const val FIRST_LOCKOUT_MS = 30_000L // 30 seconds
    const val SECOND_LOCKOUT_MS = 120_000L // 2 minutes
    const val SEVERE_LOCKOUT_MS = 300_000L // 5 minutes

    fun generateSalt(): ByteArray {
        return SecureRandomGenerator.generateBytes(16)
    }

    /**
     * Derives a salted SHA-256 hash from a PIN and salt bytes.
     */
    fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Constant-time comparison between input PIN and stored hash.
     */
    fun verifyPin(inputPin: String, saltHex: String, expectedHashHex: String): Boolean {
        if (inputPin.isEmpty() || saltHex.isEmpty() || expectedHashHex.isEmpty()) return false
        val saltBytes = hexToByteArray(saltHex)
        val computedHashHex = hashPin(inputPin, saltBytes)

        val computedBytes = computedHashHex.toByteArray(Charsets.UTF_8)
        val expectedBytes = expectedHashHex.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(computedBytes, expectedBytes)
    }

    /**
     * Calculates progressive lockout duration based on consecutive failure count.
     */
    fun calculateLockoutDurationMs(failedAttempts: Int): Long {
        return when {
            failedAttempts < MAX_ATTEMPTS_BEFORE_FIRST_LOCKOUT -> 0L
            failedAttempts < 8 -> FIRST_LOCKOUT_MS
            failedAttempts < 10 -> SECOND_LOCKOUT_MS
            else -> SEVERE_LOCKOUT_MS
        }
    }

    fun hexToByteArray(hex: String): ByteArray {
        val clean = hex.trim()
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
