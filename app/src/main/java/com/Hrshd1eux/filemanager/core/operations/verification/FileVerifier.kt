package com.Hrshd1eux.filemanager.core.operations.verification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.coroutines.coroutineContext

class IntegrityVerificationException(message: String) : Exception(message)

object FileVerifier {

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Performs a bit-for-bit cryptographic SHA-256 verification between source and destination files.
     * Throws IntegrityVerificationException if sizes differ or digests do not match.
     */
    suspend fun verifyFilePair(
        source: File,
        destination: File,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean = withContext(ioDispatcher) {
        if (source.length() != destination.length()) {
            throw IntegrityVerificationException(
                "Size mismatch: Source ${source.name} is ${source.length()} bytes, but destination is ${destination.length()} bytes"
            )
        }

        val srcDigest = MessageDigest.getInstance("SHA-256")
        val dstDigest = MessageDigest.getInstance("SHA-256")

        val srcBuffer = ByteArray(BUFFER_SIZE)
        val dstBuffer = ByteArray(BUFFER_SIZE)

        FileInputStream(source).use { srcStream ->
            FileInputStream(destination).use { dstStream ->
                var srcLen: Int
                var dstLen: Int

                while (srcStream.read(srcBuffer).also { srcLen = it } != -1) {
                    coroutineContext.ensureActive()
                    dstLen = dstStream.read(dstBuffer)

                    if (srcLen != dstLen) {
                        throw IntegrityVerificationException("Stream length mismatch during verification of ${source.name}")
                    }

                    srcDigest.update(srcBuffer, 0, srcLen)
                    dstDigest.update(dstBuffer, 0, dstLen)
                }
            }
        }

        if (!MessageDigest.isEqual(srcDigest.digest(), dstDigest.digest())) {
            throw IntegrityVerificationException(
                "SHA-256 checksum mismatch between ${source.name} and ${destination.name}"
            )
        }

        true
    }

    /**
     * Verifies that the extracted file matches the expected CRC-32 checksum recorded in archive headers.
     */
    suspend fun verifyCrc(
        file: File,
        expectedCrc: Long,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean = withContext(ioDispatcher) {
        if (!file.exists() || !file.isFile) {
            throw IntegrityVerificationException("Extracted file does not exist for CRC verification: ${file.path}")
        }

        val crc32 = CRC32()
        val buffer = ByteArray(BUFFER_SIZE)

        FileInputStream(file).use { fis ->
            var len: Int
            while (fis.read(buffer).also { len = it } != -1) {
                coroutineContext.ensureActive()
                crc32.update(buffer, 0, len)
            }
        }

        if (crc32.value != expectedCrc) {
            throw IntegrityVerificationException(
                "CRC-32 mismatch for ${file.name}: expected %08x but computed %08x".format(expectedCrc, crc32.value)
            )
        }

        true
    }
}
