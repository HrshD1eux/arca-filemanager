package com.Hrshd1eux.filemanager.core.checksum

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

enum class ChecksumAlgorithm(
    val displayName: String,
    val label: String,
    val isCryptographicallySecure: Boolean
) {
    CRC32("CRC32", "Error-detection code", false),
    MD5("MD5", "Legacy checksum", false),
    SHA_256("SHA-256", "Secure hash", true),
    SHA_512("SHA-512", "High-security hash", true)
}

data class ChecksumItem(
    val algorithm: ChecksumAlgorithm,
    val hashHex: String,
    val elapsedMs: Long
)

class ChecksumEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Calculates CRC32, MD5, SHA-256, and SHA-512 checksums in a single streaming pass over the file.
     */
    suspend fun calculateAll(
        file: File,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<List<ChecksumItem>> = withContext(ioDispatcher) {
        runCatching {
            if (!file.exists() || !file.isFile) {
                throw IllegalArgumentException("File does not exist: ${file.path}")
            }

            val startTime = System.currentTimeMillis()
            val totalBytes = file.length()

            val crc32 = CRC32()
            val md5Digest = MessageDigest.getInstance("MD5")
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val sha512Digest = MessageDigest.getInstance("SHA-512")

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesReadTotal = 0L

            FileInputStream(file).use { fis ->
                var len: Int
                while (fis.read(buffer).also { len = it } != -1) {
                    coroutineContext.ensureActive()

                    crc32.update(buffer, 0, len)
                    md5Digest.update(buffer, 0, len)
                    sha256Digest.update(buffer, 0, len)
                    sha512Digest.update(buffer, 0, len)

                    bytesReadTotal += len
                    onProgress?.invoke(bytesReadTotal, totalBytes)
                }
            }

            val elapsed = System.currentTimeMillis() - startTime

            val crcHex = "%08x".format(crc32.value)
            val md5Hex = md5Digest.digest().toHex()
            val sha256Hex = sha256Digest.digest().toHex()
            val sha512Hex = sha512Digest.digest().toHex()

            listOf(
                ChecksumItem(ChecksumAlgorithm.SHA_256, sha256Hex, elapsed),
                ChecksumItem(ChecksumAlgorithm.SHA_512, sha512Hex, elapsed),
                ChecksumItem(ChecksumAlgorithm.MD5, md5Hex, elapsed),
                ChecksumItem(ChecksumAlgorithm.CRC32, crcHex, elapsed)
            )
        }
    }

    /**
     * Verifies if an expected hash matches any calculated checksum.
     * Returns matching ChecksumAlgorithm if match found, null otherwise.
     */
    fun matchExpected(expected: String, items: List<ChecksumItem>): ChecksumAlgorithm? {
        val sanitizedExpected = expected.trim().replace(":", "").replace(" ", "").lowercase()
        if (sanitizedExpected.isEmpty()) return null

        return items.firstOrNull { item ->
            val sanitizedActual = item.hashHex.trim().lowercase()
            sanitizedActual == sanitizedExpected
        }?.algorithm
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}
