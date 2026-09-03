package com.Hrshd1eux.filemanager.core.crypto

import com.Hrshd1eux.filemanager.core.crypto.aead.AeadCipherEngine
import com.Hrshd1eux.filemanager.core.crypto.aead.EncryptedPayload
import com.Hrshd1eux.filemanager.core.crypto.kdf.KeyDerivationEngine
import com.Hrshd1eux.filemanager.core.crypto.keystore.AndroidKeyStoreManager
import com.Hrshd1eux.filemanager.core.crypto.random.SecureRandomGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Arrays
import javax.crypto.SecretKey

/**
 * Unified Cryptography Coordinator for ARCA.
 * Orchestrates Key Derivation (PBKDF2/HKDF), Authenticated Encryption (AES-256-GCM),
 * Android Keystore hardware protection, and secure data zeroization.
 */
class CryptoEngine(
    val random: SecureRandomGenerator = SecureRandomGenerator,
    val kdf: KeyDerivationEngine = KeyDerivationEngine(),
    val aead: AeadCipherEngine = AeadCipherEngine(),
    val keyStore: AndroidKeyStoreManager = AndroidKeyStoreManager(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Derives a 256-bit AES key from a user password and cryptographic salt.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = KeyDerivationEngine.DEFAULT_PBKDF2_ITERATIONS
    ): SecretKey {
        return kdf.deriveKeyFromPassword(password, salt, iterations)
    }

    /**
     * Derives a dedicated subkey using HKDF-SHA256.
     */
    fun expandSubKey(
        masterKey: SecretKey,
        info: ByteArray,
        lengthBytes: Int = 32
    ): SecretKey {
        return kdf.expandSubKey(masterKey, info, lengthBytes)
    }

    /**
     * Encrypts a byte array using AES-256-GCM authenticated encryption.
     */
    fun encryptPayload(
        plaintext: ByteArray,
        key: SecretKey,
        associatedData: ByteArray? = null
    ): EncryptedPayload {
        return aead.encryptPayload(plaintext, key, associatedData)
    }

    /**
     * Decrypts an EncryptedPayload using AES-256-GCM.
     */
    fun decryptPayload(
        payload: EncryptedPayload,
        key: SecretKey,
        associatedData: ByteArray? = null
    ): ByteArray {
        return aead.decryptPayload(payload, key, associatedData)
    }

    /**
     * Encrypts a source file to destination file using authenticated framed chunk streaming.
     */
    suspend fun encryptFile(
        sourceFile: File,
        destinationFile: File,
        key: SecretKey,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ) = withContext(ioDispatcher) {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            throw IllegalArgumentException("Source file does not exist: ${sourceFile.path}")
        }
        destinationFile.parentFile?.mkdirs()

        FileInputStream(sourceFile).use { fis ->
            FileOutputStream(destinationFile).use { fos ->
                aead.encryptStream(fis, fos, key, ioDispatcher, onProgress)
            }
        }
    }

    /**
     * Decrypts an encrypted source file back to destination file with integrity verification.
     */
    suspend fun decryptFile(
        sourceFile: File,
        destinationFile: File,
        key: SecretKey,
        onProgress: ((bytesWritten: Long) -> Unit)? = null
    ) = withContext(ioDispatcher) {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            throw IllegalArgumentException("Encrypted source file does not exist: ${sourceFile.path}")
        }
        destinationFile.parentFile?.mkdirs()

        FileInputStream(sourceFile).use { fis ->
            FileOutputStream(destinationFile).use { fos ->
                aead.decryptStream(fis, fos, key, ioDispatcher, onProgress)
            }
        }
    }

    /**
     * Zeroizes sensitive byte arrays in memory to prevent memory scraping.
     */
    fun wipeSensitiveData(vararg arrays: ByteArray?) {
        for (array in arrays) {
            if (array != null) {
                Arrays.fill(array, 0.toByte())
            }
        }
    }

    /**
     * Zeroizes sensitive character arrays (e.g. passwords) in memory.
     */
    fun wipeSensitiveData(chars: CharArray?) {
        if (chars != null) {
            Arrays.fill(chars, '\u0000')
        }
    }
}
