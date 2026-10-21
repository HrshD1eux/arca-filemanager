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
    SHA_1("SHA-1", "Legacy hash", false),
    SHA_256("SHA-256", "Secure hash", true),
    SHA_512("SHA-512", "High-security hash", true)
}

class FileChangedDuringVerificationException(message: String) : IllegalStateException(message)

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
     * Calculates a single specific checksum for a file.
     */
    suspend fun calculateAlgorithm(
        file: File,
        algorithm: ChecksumAlgorithm,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<ChecksumItem> = withContext(ioDispatcher) {
        runCatching {
            if (!file.exists() || !file.isFile) {
                throw IllegalArgumentException("File does not exist: ${file.path}")
            }
            val startTime = System.currentTimeMillis()
            val initialLength = file.length()
            val initialLastModified = file.lastModified()
            val totalBytes = initialLength
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesReadTotal = 0L

            val hashHex = when (algorithm) {
                ChecksumAlgorithm.CRC32 -> {
                    val crc = CRC32()
                    FileInputStream(file).use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } != -1) {
                            coroutineContext.ensureActive()
                            crc.update(buffer, 0, len)
                            bytesReadTotal += len
                            onProgress?.invoke(bytesReadTotal, totalBytes)
                        }
                    }
                    "%08x".format(crc.value)
                }
                ChecksumAlgorithm.MD5 -> {
                    val digest = MessageDigest.getInstance("MD5")
                    FileInputStream(file).use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } != -1) {
                            coroutineContext.ensureActive()
                            digest.update(buffer, 0, len)
                            bytesReadTotal += len
                            onProgress?.invoke(bytesReadTotal, totalBytes)
                        }
                    }
                    digest.digest().toHex()
                }
                ChecksumAlgorithm.SHA_1 -> {
                    val digest = MessageDigest.getInstance("SHA-1")
                    FileInputStream(file).use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } != -1) {
                            coroutineContext.ensureActive()
                            digest.update(buffer, 0, len)
                            bytesReadTotal += len
                            onProgress?.invoke(bytesReadTotal, totalBytes)
                        }
                    }
                    digest.digest().toHex()
                }
                ChecksumAlgorithm.SHA_256 -> {
                    val digest = MessageDigest.getInstance("SHA-256")
                    FileInputStream(file).use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } != -1) {
                            coroutineContext.ensureActive()
                            digest.update(buffer, 0, len)
                            bytesReadTotal += len
                            onProgress?.invoke(bytesReadTotal, totalBytes)
                        }
                    }
                    digest.digest().toHex()
                }
                ChecksumAlgorithm.SHA_512 -> {
                    val digest = MessageDigest.getInstance("SHA-512")
                    FileInputStream(file).use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } != -1) {
                            coroutineContext.ensureActive()
                            digest.update(buffer, 0, len)
                            bytesReadTotal += len
                            onProgress?.invoke(bytesReadTotal, totalBytes)
                        }
                    }
                    digest.digest().toHex()
                }
            }

            // Milestone 1.4.22: Report when a file changes during verification
            if (file.length() != initialLength || file.lastModified() != initialLastModified) {
                throw FileChangedDuringVerificationException(
                    "File '${file.name}' changed during verification: initial length/mtime ($initialLength, $initialLastModified) does not match final state (${file.length()}, ${file.lastModified()})."
                )
            }

            val elapsed = System.currentTimeMillis() - startTime
            ChecksumItem(algorithm, hashHex, elapsed)
        }
    }

    /**
     * Compares two files using cryptographic checksums to verify bit-for-bit file integrity.
     */
    suspend fun compareFiles(
        file1: File,
        file2: File,
        algorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA_256,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<FileComparisonResult> = withContext(ioDispatcher) {
        runCatching {
            if (!file1.exists() || !file1.isFile) throw IllegalArgumentException("Source file does not exist: ${file1.path}")
            if (!file2.exists() || !file2.isFile) throw IllegalArgumentException("Target file does not exist: ${file2.path}")

            if (file1.length() != file2.length()) {
                val h1 = calculateAlgorithm(file1, algorithm).getOrThrow().hashHex
                val h2 = calculateAlgorithm(file2, algorithm).getOrThrow().hashHex
                return@runCatching FileComparisonResult(
                    file1 = file1,
                    file2 = file2,
                    isMatch = false,
                    algorithm = algorithm,
                    hash1 = h1,
                    hash2 = h2,
                    explanation = "File sizes differ (${file1.length()} vs ${file2.length()} bytes)"
                )
            }

            val totalBytes = file1.length() + file2.length()
            var currentBytes = 0L

            val item1 = calculateAlgorithm(file1, algorithm) { read, _ ->
                onProgress?.invoke(read, totalBytes)
            }.getOrThrow()
            currentBytes += file1.length()

            val item2 = calculateAlgorithm(file2, algorithm) { read, _ ->
                onProgress?.invoke(currentBytes + read, totalBytes)
            }.getOrThrow()

            val matches = item1.hashHex.equals(item2.hashHex, ignoreCase = true)
            FileComparisonResult(
                file1 = file1,
                file2 = file2,
                isMatch = matches,
                algorithm = algorithm,
                hash1 = item1.hashHex,
                hash2 = item2.hashHex,
                explanation = if (matches) "Files are identical bit-for-bit." else "Files differ (checksum mismatch)."
            )
        }
    }

    /**
     * Extracts and normalizes a hash from raw pasted text, stripping common formatting like
     * sha256sum outputs ("<hash>  <filename>"), BSD style ("SHA256 (file) = <hash>"), colons, or spaces.
     */
    fun extractHashFromText(rawText: String): String {
        var text = rawText.trim()
        if (text.contains("=")) {
            text = text.substringAfter("=").trim()
        }
        val directClean = text.replace(":", "").replace(" ", "").replace("-", "")
        if (directClean.isNotEmpty() && directClean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } &&
            directClean.length in setOf(8, 32, 40, 64, 128)) {
            return directClean.lowercase()
        }
        val tokens = text.split("\\s+".toRegex())
        val candidate = tokens.firstOrNull { token ->
            val clean = token.replace(":", "").replace("-", "")
            clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } &&
                clean.length in setOf(8, 32, 40, 64, 128)
        } ?: text
        return candidate.replace(":", "").replace(" ", "").replace("-", "").lowercase()
    }

    /**
     * Parses standard checksum file formats (e.g., md5sum, sha256sum, BSD styles).
     */
    fun parseChecksumContent(content: String): List<ChecksumRecord> {
        val records = mutableListOf<ChecksumRecord>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach

            if (trimmed.contains("=") && (trimmed.startsWith("MD5") || trimmed.startsWith("SHA") || trimmed.startsWith("CRC"))) {
                val algoName = trimmed.substringBefore("(").trim()
                val fileName = trimmed.substringAfter("(").substringBefore(")").trim()
                val hash = trimmed.substringAfter("=").trim().replace(" ", "").lowercase()
                val algo = when {
                    algoName.contains("256") -> ChecksumAlgorithm.SHA_256
                    algoName.contains("512") -> ChecksumAlgorithm.SHA_512
                    algoName.contains("MD5", ignoreCase = true) -> ChecksumAlgorithm.MD5
                    algoName.contains("CRC", ignoreCase = true) -> ChecksumAlgorithm.CRC32
                    else -> null
                }
                records.add(ChecksumRecord(hash = hash, fileName = fileName, algorithm = algo))
            } else {
                val parts = trimmed.split("\\s+".toRegex(), limit = 2)
                if (parts.size >= 2) {
                    val hash = parts[0].trim().lowercase()
                    val fileName = parts[1].trim().removePrefix("*")
                    val algo = when (hash.length) {
                        8 -> ChecksumAlgorithm.CRC32
                        32 -> ChecksumAlgorithm.MD5
                        64 -> ChecksumAlgorithm.SHA_256
                        128 -> ChecksumAlgorithm.SHA_512
                        else -> null
                    }
                    records.add(ChecksumRecord(hash = hash, fileName = fileName, algorithm = algo))
                }
            }
        }
        return records
    }

    /**
     * Verifies if an expected hash matches any calculated checksum.
     * Returns matching ChecksumAlgorithm if match found, null otherwise.
     */
    fun matchExpected(expected: String, items: List<ChecksumItem>): ChecksumAlgorithm? {
        val sanitizedExpected = extractHashFromText(expected)
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

data class FileComparisonResult(
    val file1: File,
    val file2: File,
    val isMatch: Boolean,
    val algorithm: ChecksumAlgorithm,
    val hash1: String,
    val hash2: String,
    val explanation: String
)

data class ChecksumRecord(
    val hash: String,
    val fileName: String,
    val algorithm: ChecksumAlgorithm?
)
