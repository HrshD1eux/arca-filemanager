package com.Hrshd1eux.filemanager.core.crypto

import com.Hrshd1eux.filemanager.core.crypto.aead.AeadCipherEngine
import com.Hrshd1eux.filemanager.core.crypto.aead.EncryptedPayload
import com.Hrshd1eux.filemanager.core.crypto.kdf.KeyDerivationEngine
import com.Hrshd1eux.filemanager.core.crypto.random.SecureRandomGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class CryptoEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cryptoEngine: CryptoEngine

    @Before
    fun setUp() {
        cryptoEngine = CryptoEngine(
            random = SecureRandomGenerator,
            kdf = KeyDerivationEngine(),
            aead = AeadCipherEngine(),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun secureRandomGenerator_generatesUniqueCryptographicBytes() {
        val salt1 = SecureRandomGenerator.generateSalt(32)
        val salt2 = SecureRandomGenerator.generateSalt(32)

        assertEquals(32, salt1.size)
        assertEquals(32, salt2.size)
        assertFalse(salt1.contentEquals(salt2))

        val nonce1 = SecureRandomGenerator.generateGcmNonce(12)
        val nonce2 = SecureRandomGenerator.generateGcmNonce(12)

        assertEquals(12, nonce1.size)
        assertEquals(12, nonce2.size)
        assertFalse(nonce1.contentEquals(nonce2))
    }

    @Test
    fun keyDerivationEngine_derivesDeterministicKeyFromPasswordAndSalt() {
        val password = "SuperSecretPassword123!".toCharArray()
        val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)

        val key1 = cryptoEngine.deriveKey(password, salt, iterations = 10_000)
        val key2 = cryptoEngine.deriveKey(password, salt, iterations = 10_000)

        assertArrayEquals(key1.encoded, key2.encoded)
        assertEquals("AES", key1.algorithm)
        assertEquals(32, key1.encoded.size) // 256 bits

        // Different salt produces distinct key
        val differentSalt = byteArrayOf(16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
        val key3 = cryptoEngine.deriveKey(password, differentSalt, iterations = 10_000)
        assertFalse(key1.encoded.contentEquals(key3.encoded))

        // Different password produces distinct key
        val key4 = cryptoEngine.deriveKey("DifferentPassword!".toCharArray(), salt, iterations = 10_000)
        assertFalse(key1.encoded.contentEquals(key4.encoded))
    }

    @Test
    fun keyDerivationEngine_expandsSubKeysViaHkdf() {
        val masterKey = cryptoEngine.deriveKey("MasterKeyPass".toCharArray(), byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 10_000)
        val subkey1 = cryptoEngine.expandSubKey(masterKey, "file-encryption-v1".toByteArray())
        val subkey2 = cryptoEngine.expandSubKey(masterKey, "file-encryption-v1".toByteArray())
        val subkey3 = cryptoEngine.expandSubKey(masterKey, "metadata-encryption-v1".toByteArray())

        assertArrayEquals(subkey1.encoded, subkey2.encoded)
        assertFalse(subkey1.encoded.contentEquals(subkey3.encoded))
    }

    @Test
    fun aeadCipherEngine_encryptsAndDecryptsPayloadAccurately() {
        val key = cryptoEngine.deriveKey("TestPass".toCharArray(), byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 10_000)
        val plaintext = "Confidential privacy data payload".toByteArray()
        val aad = "user-id-42".toByteArray()

        val payload = cryptoEngine.encryptPayload(plaintext, key, aad)
        assertEquals(12, payload.iv.size)
        assertTrue(payload.ciphertextWithTag.size > plaintext.size) // Includes 128-bit tag

        val decrypted = cryptoEngine.decryptPayload(payload, key, aad)
        assertArrayEquals(plaintext, decrypted)
        assertEquals("Confidential privacy data payload", String(decrypted))
    }

    @Test
    fun aeadCipherEngine_rejectsTamperedCiphertext() {
        val key = cryptoEngine.deriveKey("TestPass".toCharArray(), byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 10_000)
        val plaintext = "Sensitive data".toByteArray()

        val payload = cryptoEngine.encryptPayload(plaintext, key)

        // Tamper with 1 byte of ciphertext
        val tamperedBytes = payload.ciphertextWithTag.copyOf()
        tamperedBytes[0] = (tamperedBytes[0].toInt() xor 0xFF).toByte()
        val tamperedPayload = EncryptedPayload(payload.iv, tamperedBytes)

        assertThrows(GeneralSecurityException::class.java) {
            cryptoEngine.decryptPayload(tamperedPayload, key)
        }
    }

    @Test
    fun aeadCipherEngine_rejectsTamperedAad() {
        val key = cryptoEngine.deriveKey("TestPass".toCharArray(), byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 10_000)
        val plaintext = "Sensitive data".toByteArray()
        val aad = "correct-aad".toByteArray()
        val wrongAad = "tampered-aad".toByteArray()

        val payload = cryptoEngine.encryptPayload(plaintext, key, aad)

        assertThrows(GeneralSecurityException::class.java) {
            cryptoEngine.decryptPayload(payload, key, wrongAad)
        }
    }

    @Test
    fun encryptFileAndDecryptFile_roundtripsLargeStreamingFiles() = runTest {
        val key = cryptoEngine.deriveKey("FileSecretKey".toCharArray(), byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2), 10_000)

        // Create 200 KB test file (multiple 64KB stream chunks)
        val originalFile = tempFolder.newFile("plain.dat").apply {
            val content = ByteArray(200 * 1024) { (it % 256).toByte() }
            writeBytes(content)
        }
        val encryptedFile = tempFolder.newFile("encrypted.arca")
        val decryptedFile = tempFolder.newFile("decrypted.dat")

        cryptoEngine.encryptFile(originalFile, encryptedFile, key)
        assertTrue(encryptedFile.length() > originalFile.length())

        cryptoEngine.decryptFile(encryptedFile, decryptedFile, key)
        assertEquals(originalFile.length(), decryptedFile.length())
        assertArrayEquals(originalFile.readBytes(), decryptedFile.readBytes())
    }

    @Test
    fun wipeSensitiveData_zeroizesMemoryArrays() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        cryptoEngine.wipeSensitiveData(bytes)
        assertTrue(bytes.all { it == 0.toByte() })

        val chars = charArrayOf('p', 'a', 's', 's', 'w', 'o', 'r', 'd')
        cryptoEngine.wipeSensitiveData(chars)
        assertTrue(chars.all { it == '\u0000' })
    }
}
