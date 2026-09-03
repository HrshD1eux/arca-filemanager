package com.Hrshd1eux.filemanager.core.crypto.random

import java.security.SecureRandom

object SecureRandomGenerator {

    private val secureRandom: SecureRandom by lazy {
        SecureRandom()
    }

    /**
     * Generates a cryptographically secure random byte array of specified length.
     */
    fun generateBytes(size: Int): ByteArray {
        require(size > 0) { "Byte size must be greater than 0" }
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /**
     * Generates a random cryptographic salt (Default: 32 bytes / 256 bits).
     */
    fun generateSalt(size: Int = 32): ByteArray = generateBytes(size)

    /**
     * Generates a random AES-GCM 96-bit Initialization Vector / Nonce (12 bytes).
     */
    fun generateGcmNonce(size: Int = 12): ByteArray = generateBytes(size)

    /**
     * Generates raw 256-bit AES key material (32 bytes).
     */
    fun generateKeyMaterial(size: Int = 32): ByteArray = generateBytes(size)
}
