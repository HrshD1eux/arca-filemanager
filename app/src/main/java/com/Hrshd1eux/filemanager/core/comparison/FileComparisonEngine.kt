package com.Hrshd1eux.filemanager.core.comparison

import com.Hrshd1eux.filemanager.core.checksum.ChecksumAlgorithm
import com.Hrshd1eux.filemanager.core.checksum.ChecksumEngine
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class DetailedFileComparison(
    val file1: File,
    val file2: File,
    val name1: String,
    val name2: String,
    val isNameMatch: Boolean,
    val size1: Long,
    val size2: Long,
    val isSizeMatch: Boolean,
    val lastModified1: Long,
    val lastModified2: Long,
    val isTimestampMatch: Boolean,
    val mimeType1: String?,
    val mimeType2: String?,
    val isMimeMatch: Boolean,
    val hashAlgorithm: ChecksumAlgorithm? = null,
    val hash1: String? = null,
    val hash2: String? = null,
    val isHashMatch: Boolean? = null,
    val isIdentical: Boolean,
    val summary: String,
    val isContentVerified: Boolean = isHashMatch != null,
    val metadataMatchDisclaimer: String = METADATA_MATCH_DISCLAIMER
) {
    companion object {
        const val METADATA_MATCH_DISCLAIMER =
            "Notice: Matching metadata (name, size, modification date) suggests files are likely similar, " +
            "but does NOT establish identical contents without cryptographic hash verification."
    }
}

class FileComparisonEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val checksumEngine: ChecksumEngine = ChecksumEngine(ioDispatcher)
) {

    /**
     * Compares two files by size, metadata (name, modification time, MIME type),
     * and optionally verifies bit-for-bit cryptographic checksum content hashes.
     */
    suspend fun compareFiles(
        file1: File,
        file2: File,
        calculateHash: Boolean = false,
        algorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA_256,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<DetailedFileComparison> = withContext(ioDispatcher) {
        runCatching {
            if (!file1.exists() || !file1.isFile) {
                throw IllegalArgumentException("First file does not exist or is a directory: ${file1.path}")
            }
            if (!file2.exists() || !file2.isFile) {
                throw IllegalArgumentException("Second file does not exist or is a directory: ${file2.path}")
            }

            val size1 = file1.length()
            val size2 = file2.length()
            val sizeMatch = size1 == size2

            val lastMod1 = file1.lastModified()
            val lastMod2 = file2.lastModified()
            val timestampMatch = abs(lastMod1 - lastMod2) < 2000L

            val name1 = file1.name
            val name2 = file2.name
            val nameMatch = name1.equals(name2, ignoreCase = true)

            val ext1 = name1.substringAfterLast('.', "")
            val mime1 = com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver.getMimeType(ext1)
            val ext2 = name2.substringAfterLast('.', "")
            val mime2 = com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver.getMimeType(ext2)
            val mimeMatch = mime1.equals(mime2, ignoreCase = true)

            var hash1: String? = null
            var hash2: String? = null
            var hashMatch: Boolean? = null
            var chosenAlgo: ChecksumAlgorithm? = null

            if (calculateHash) {
                chosenAlgo = algorithm
                if (!sizeMatch) {
                    // Fast path: file sizes differ, so hashes cannot match
                    val h1 = checksumEngine.calculateAlgorithm(file1, algorithm).getOrThrow().hashHex
                    val h2 = checksumEngine.calculateAlgorithm(file2, algorithm).getOrThrow().hashHex
                    hash1 = h1
                    hash2 = h2
                    hashMatch = false
                } else {
                    val res = checksumEngine.compareFiles(file1, file2, algorithm, onProgress).getOrThrow()
                    hash1 = res.hash1
                    hash2 = res.hash2
                    hashMatch = res.isMatch
                }
            }

            val isIdentical = if (hashMatch != null) {
                hashMatch
            } else {
                sizeMatch && timestampMatch
            }

            val summary = when {
                hashMatch == true -> "Files are bit-for-bit identical ($chosenAlgo match)."
                hashMatch == false -> "Files differ: Checksum mismatch ($chosenAlgo)."
                !sizeMatch -> "Files differ: Size difference (${FileFormatter.formatFileSize(size1)} vs ${FileFormatter.formatFileSize(size2)})."
                !timestampMatch -> "Files have identical size, but different modification dates."
                else -> "Files match in size and timestamps."
            }

            DetailedFileComparison(
                file1 = file1,
                file2 = file2,
                name1 = name1,
                name2 = name2,
                isNameMatch = nameMatch,
                size1 = size1,
                size2 = size2,
                isSizeMatch = sizeMatch,
                lastModified1 = lastMod1,
                lastModified2 = lastMod2,
                isTimestampMatch = timestampMatch,
                mimeType1 = mime1,
                mimeType2 = mime2,
                isMimeMatch = mimeMatch,
                hashAlgorithm = chosenAlgo,
                hash1 = hash1,
                hash2 = hash2,
                isHashMatch = hashMatch,
                isIdentical = isIdentical,
                summary = summary
            )
        }
    }

    suspend fun compareStorageNodes(
        node1: StorageNode,
        node2: StorageNode,
        calculateHash: Boolean = false,
        algorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA_256,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<DetailedFileComparison> {
        return compareFiles(
            file1 = File(node1.path),
            file2 = File(node2.path),
            calculateHash = calculateHash,
            algorithm = algorithm,
            onProgress = onProgress
        )
    }
}
