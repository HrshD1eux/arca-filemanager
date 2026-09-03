package com.Hrshd1eux.filemanager.core.crypto.aead

import com.Hrshd1eux.filemanager.core.crypto.random.SecureRandomGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.coroutineContext

data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertextWithTag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedPayload
        if (!iv.contentEquals(other.iv)) return false
        if (!ciphertextWithTag.contentEquals(other.ciphertextWithTag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertextWithTag.contentHashCode()
        return result
    }
}

class AeadCipherEngine {

    /**
     * Encrypts a byte array using AES-256-GCM authenticated encryption.
     */
    fun encryptPayload(
        plaintext: ByteArray,
        key: SecretKey,
        associatedData: ByteArray? = null
    ): EncryptedPayload {
        val iv = SecureRandomGenerator.generateGcmNonce(GCM_IV_LENGTH_BYTES)
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        associatedData?.let { cipher.updateAAD(it) }
        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedPayload(iv = iv, ciphertextWithTag = ciphertext)
    }

    /**
     * Decrypts an EncryptedPayload using AES-256-GCM.
     * Throws AEADBadTagException or GeneralSecurityException on authentication failure / tampering.
     */
    fun decryptPayload(
        payload: EncryptedPayload,
        key: SecretKey,
        associatedData: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        associatedData?.let { cipher.updateAAD(it) }
        return cipher.doFinal(payload.ciphertextWithTag)
    }

    /**
     * Encrypts an InputStream to an OutputStream in authenticated 64KB framed chunks.
     * Each chunk has its own distinct IV and sequence index AAD to prevent truncation or reordering.
     */
    suspend fun encryptStream(
        input: InputStream,
        output: OutputStream,
        key: SecretKey,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ) = withContext(ioDispatcher) {
        val dataOut = DataOutputStream(output)
        dataOut.write(STREAM_MAGIC_HEADER)

        val buffer = ByteArray(STREAM_CHUNK_SIZE)
        var totalBytesRead = 0L
        var chunkIndex = 0L

        var len: Int
        while (input.read(buffer).also { len = it } != -1) {
            coroutineContext.ensureActive()

            val chunkIv = SecureRandomGenerator.generateGcmNonce(GCM_IV_LENGTH_BYTES)
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            // Monotonic sequence number AAD protects against block permutation/reordering
            val seqAad = ByteBuffer.allocate(8).putLong(chunkIndex).array()
            cipher.updateAAD(seqAad)

            val ciphertext = cipher.doFinal(buffer, 0, len)

            // Frame structure: [4 bytes cipher length][12 bytes chunk IV][ciphertext + tag]
            dataOut.writeInt(ciphertext.size)
            dataOut.write(chunkIv)
            dataOut.write(ciphertext)

            totalBytesRead += len
            chunkIndex++
            onProgress?.invoke(totalBytesRead)
        }

        // EOF Frame Marker (0 length)
        dataOut.writeInt(0)
        dataOut.flush()
    }

    /**
     * Decrypts an authenticated framed stream back to plaintext.
     * Validates magic header, GCM authentication tags, and sequence number AAD.
     */
    suspend fun decryptStream(
        input: InputStream,
        output: OutputStream,
        key: SecretKey,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        onProgress: ((bytesWritten: Long) -> Unit)? = null
    ) = withContext(ioDispatcher) {
        val dataIn = DataInputStream(input)

        val magic = ByteArray(STREAM_MAGIC_HEADER.size)
        dataIn.readFully(magic)
        if (!magic.contentEquals(STREAM_MAGIC_HEADER)) {
            throw GeneralSecurityException("Invalid or unrecognized encrypted stream header")
        }

        var totalBytesWritten = 0L
        var chunkIndex = 0L

        while (true) {
            coroutineContext.ensureActive()

            val cipherLength = dataIn.readInt()
            if (cipherLength == 0) {
                // EOF marker reached safely
                break
            }

            if (cipherLength < 0 || cipherLength > STREAM_CHUNK_SIZE + 64) {
                throw GeneralSecurityException("Malformed or corrupted stream frame size: $cipherLength")
            }

            val chunkIv = ByteArray(GCM_IV_LENGTH_BYTES)
            dataIn.readFully(chunkIv)

            val ciphertext = ByteArray(cipherLength)
            dataIn.readFully(ciphertext)

            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val seqAad = ByteBuffer.allocate(8).putLong(chunkIndex).array()
            cipher.updateAAD(seqAad)

            val plaintext = cipher.doFinal(ciphertext)
            output.write(plaintext)

            totalBytesWritten += plaintext.size
            chunkIndex++
            onProgress?.invoke(totalBytesWritten)
        }

        output.flush()
    }

    companion object {
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val STREAM_CHUNK_SIZE = 64 * 1024
        private val STREAM_MAGIC_HEADER = byteArrayOf(0x41, 0x52, 0x43, 0x41, 0x45, 0x4E, 0x43, 0x01) // ARCAENC\x01
    }
}
