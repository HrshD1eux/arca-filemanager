package com.Hrshd1eux.filemanager.core.crypto.kdf

import java.nio.ByteBuffer
import java.security.spec.KeySpec
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class KeyDerivationEngine {

    /**
     * Derives a 256-bit AES secret key from a user password and cryptographic salt using PBKDF2WithHmacSHA256.
     */
    fun deriveKeyFromPassword(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
        keyLengthBits: Int = 256
    ): SecretKey {
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(salt.isNotEmpty()) { "Salt must not be empty" }
        require(iterations >= 10_000) { "Iterations count must be at least 10,000" }

        val spec: KeySpec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Derives a dedicated sub-key using HKDF-SHA256 (HMAC-based Extract-and-Expand Key Derivation).
     */
    fun expandSubKey(
        masterKey: SecretKey,
        info: ByteArray,
        lengthBytes: Int = 32
    ): SecretKey {
        require(lengthBytes in 1..255 * 32) { "Requested HKDF length is out of bounds" }

        val mac = Mac.getInstance(HMAC_SHA256_ALGORITHM)
        // Extract step (Pseudo-Random Key)
        val prkSpec = SecretKeySpec(masterKey.encoded, HMAC_SHA256_ALGORITHM)
        mac.init(prkSpec)
        val prk = mac.doFinal(masterKey.encoded)

        // Expand step
        val expandMac = Mac.getInstance(HMAC_SHA256_ALGORITHM)
        expandMac.init(SecretKeySpec(prk, HMAC_SHA256_ALGORITHM))

        val result = ByteArray(lengthBytes)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1.toByte()

        while (offset < lengthBytes) {
            expandMac.update(t)
            expandMac.update(info)
            expandMac.update(counter)
            t = expandMac.doFinal()

            val toCopy = minOf(t.size, lengthBytes - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }

        return SecretKeySpec(result, "AES")
    }

    companion object {
        const val DEFAULT_PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val HMAC_SHA256_ALGORITHM = "HmacSHA256"
    }
}
