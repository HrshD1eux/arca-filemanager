package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.util.StreamCopier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.compressors.CompressorInputStream
import org.apache.commons.compress.compressors.CompressorOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.z.ZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

enum class StandaloneCompressionType(val extension: String, val displayName: String) {
    GZIP(".gz", "GZIP Compressed File"),
    BZIP2(".bz2", "BZip2 Compressed File"),
    XZ(".xz", "XZ Compressed File"),
    Z(".z", "Unix Compress File");

    companion object {
        fun fromFile(file: File): StandaloneCompressionType? {
            val name = file.name.lowercase()
            // Exclude compound TAR archives
            if (name.endsWith(".tar.gz") || name.endsWith(".tgz") ||
                name.endsWith(".tar.bz2") || name.endsWith(".tbz2") ||
                name.endsWith(".tar.xz") || name.endsWith(".txz") ||
                name.endsWith(".tar.z") || name.endsWith(".taz")
            ) {
                return null
            }

            return when {
                name.endsWith(".gz") -> GZIP
                name.endsWith(".bz2") -> BZIP2
                name.endsWith(".xz") -> XZ
                name.endsWith(".z") -> Z
                else -> null
            }
        }
    }
}

/**
 * Pure Kotlin/Java compressor engine supporting standalone single-file streams:
 * - GZIP (.gz)
 * - BZIP2 (.bz2)
 * - XZ (.xz)
 * - Unix Compress (.z, .Z)
 *
 * Provides inspection, uncompressed size probing (ISIZE for GZIP), extraction,
 * compression, and integrity testing with zero third-party binary dependencies.
 */
class StandaloneCompressorEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun isSupported(file: File): Boolean {
        return StandaloneCompressionType.fromFile(file) != null
    }

    /**
     * Resolves the target uncompressed filename for a standalone compressed file.
     * E.g. "document.txt.gz" -> "document.txt"
     *      "archive.gz" -> "archive"
     */
    fun resolveExtractedFileName(file: File): String {
        val name = file.name
        val type = StandaloneCompressionType.fromFile(file) ?: return name.substringBeforeLast('.')
        return if (name.length > type.extension.length && name.lowercase().endsWith(type.extension)) {
            name.substring(0, name.length - type.extension.length)
        } else {
            name.substringBeforeLast('.')
        }
    }

    /**
     * Probes uncompressed byte size. For GZIP, reads the 32-bit ISIZE footer in O(1) time.
     */
    fun probeUncompressedSize(file: File, type: StandaloneCompressionType): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        if (type == StandaloneCompressionType.GZIP && file.length() >= 18L) {
            try {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(file.length() - 4L)
                    val b1 = raf.read()
                    val b2 = raf.read()
                    val b3 = raf.read()
                    val b4 = raf.read()
                    if (b1 >= 0 && b2 >= 0 && b3 >= 0 && b4 >= 0) {
                        return ((b1 and 0xFF) or
                                ((b2 and 0xFF) shl 8) or
                                ((b3 and 0xFF) shl 16) or
                                ((b4 and 0xFF) shl 24)).toLong() and 0xFFFFFFFFL
                    }
                }
            } catch (_: Exception) {
                // Fallback to compressed length
            }
        }
        return file.length()
    }

    /**
     * Lists the single internal file entry inside the standalone compressed archive.
     */
    suspend fun listEntries(compressedFile: File): List<ArchiveEntry> = withContext(ioDispatcher) {
        if (!compressedFile.exists() || !compressedFile.isFile) return@withContext emptyList()
        val type = StandaloneCompressionType.fromFile(compressedFile)
            ?: throw IOException("Unsupported compression format for ${compressedFile.name}")

        // Validate stream header integrity
        try {
            openCompressorInputStream(compressedFile, type).use { stream ->
                val buffer = ByteArray(16)
                stream.read(buffer)
            }
        } catch (e: Throwable) {
            throw IOException("Corrupted or invalid ${type.displayName} (${e.localizedMessage ?: "Invalid header"}).", e)
        }

        val targetName = resolveExtractedFileName(compressedFile)
        val uncompressedSize = probeUncompressedSize(compressedFile, type)

        listOf(
            ArchiveEntry(
                path = targetName,
                name = targetName,
                parentPath = "",
                isDirectory = false,
                uncompressedSize = uncompressedSize,
                compressedSize = compressedFile.length(),
                lastModified = compressedFile.lastModified(),
                crc = 0L
            )
        )
    }

    /**
     * Extracts the compressed file to destination directory.
     */
    suspend fun extractAll(
        compressedFile: File,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val type = StandaloneCompressionType.fromFile(compressedFile)
                ?: throw IOException("Unsupported compression format: ${compressedFile.name}")

            val targetFileName = resolveExtractedFileName(compressedFile)
            val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(targetFileName, destinationDir)
            val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                ?: return@runCatching 0

            targetFile.parentFile?.mkdirs()
            var bytesWritten = 0L

            try {
                openCompressorInputStream(compressedFile, type).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        StreamCopier.copyStream(
                            source = input,
                            destination = output,
                            onBytesCopied = { chunk ->
                                bytesWritten += chunk
                                ArchiveSafetyGuard.validateExpansionStream(
                                    entryName = targetFileName,
                                    uncompressedBytesRead = bytesWritten,
                                    compressedEntrySize = compressedFile.length().coerceAtLeast(1L),
                                    cumulativeUncompressedBytes = bytesWritten
                                )
                                onProgress?.invoke(
                                    OperationProgress(
                                        bytesProcessed = bytesWritten,
                                        totalBytes = probeUncompressedSize(compressedFile, type),
                                        filesProcessed = 1,
                                        totalFiles = 1,
                                        currentFileName = targetFileName
                                    )
                                )
                            }
                        )
                    }
                }
                targetFile.setLastModified(compressedFile.lastModified())
                1
            } catch (t: Throwable) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                throw t
            }
        }
    }

    /**
     * Extracts selected entries (for standalone file, extracts if path matches).
     */
    suspend fun extractEntries(
        compressedFile: File,
        entryPaths: Set<String>,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        val targetName = resolveExtractedFileName(compressedFile)
        if (entryPaths.isEmpty() || entryPaths.contains(targetName)) {
            extractAll(compressedFile, destinationDir, conflictResolution, onProgress)
        } else {
            Result.success(0)
        }
    }

    /**
     * Compresses a single local file into GZIP, BZIP2, or XZ format.
     */
    suspend fun compressFile(
        sourceFile: File,
        destinationFile: File,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            if (!sourceFile.exists() || !sourceFile.isFile) {
                throw IOException("Source file does not exist: ${sourceFile.absolutePath}")
            }

            destinationFile.parentFile?.mkdirs()
            val type = StandaloneCompressionType.fromFile(destinationFile)
                ?: throw IOException("Unsupported compression format for destination: ${destinationFile.name}")

            var bytesRead = 0L
            val totalBytes = sourceFile.length()

            try {
                FileInputStream(sourceFile).use { fis ->
                    openCompressorOutputStream(destinationFile, type).use { cos ->
                        StreamCopier.copyStream(
                            source = fis,
                            destination = cos,
                            onBytesCopied = { chunk ->
                                bytesRead += chunk
                                onProgress?.invoke(
                                    OperationProgress(
                                        bytesProcessed = bytesRead,
                                        totalBytes = totalBytes,
                                        filesProcessed = 0,
                                        totalFiles = 1,
                                        currentFileName = sourceFile.name
                                    )
                                )
                            }
                        )
                    }
                }
                destinationFile
            } catch (t: Throwable) {
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                throw t
            }
        }
    }

    /**
     * Tests archive integrity by decompressing the entire stream into a null sink.
     */
    suspend fun testArchive(
        compressedFile: File,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): ArchiveIntegrityReport = withContext(ioDispatcher) {
        val startTime = System.currentTimeMillis()
        val targetName = resolveExtractedFileName(compressedFile)
        val type = StandaloneCompressionType.fromFile(compressedFile)

        if (type == null) {
            return@withContext ArchiveIntegrityReport(
                archiveName = compressedFile.name,
                totalEntries = 1,
                verifiedEntries = 0,
                corruptedEntries = listOf(compressedFile.name),
                elapsedMs = System.currentTimeMillis() - startTime
            )
        }

        val nullSink = object : OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray, off: Int, len: Int) {}
        }

        var verified = false
        var bytesDecompressed = 0L
        val totalExpected = probeUncompressedSize(compressedFile, type)
        try {
            openCompressorInputStream(compressedFile, type).use { cis ->
                StreamCopier.copyStream(
                    source = cis,
                    destination = nullSink,
                    onBytesCopied = { chunk ->
                        bytesDecompressed += chunk
                        onProgress?.invoke(
                            OperationProgress(
                                bytesProcessed = bytesDecompressed,
                                totalBytes = if (totalExpected > 0L) totalExpected else compressedFile.length(),
                                filesProcessed = 0,
                                totalFiles = 1,
                                currentFileName = targetName
                            )
                        )
                    }
                )
            }
            verified = true
        } catch (_: Exception) {
            verified = false
        }

        val elapsed = System.currentTimeMillis() - startTime
        ArchiveIntegrityReport(
            archiveName = compressedFile.name,
            totalEntries = 1,
            verifiedEntries = if (verified) 1 else 0,
            corruptedEntries = if (verified) emptyList() else listOf(targetName),
            elapsedMs = elapsed
        )
    }

    /**
     * Computes metadata and properties for the standalone compressed file.
     */
    suspend fun getArchiveProperties(compressedFile: File): ArchiveProperties = withContext(ioDispatcher) {
        val type = StandaloneCompressionType.fromFile(compressedFile)
        val uncompressed = if (type != null) probeUncompressedSize(compressedFile, type) else compressedFile.length()

        ArchiveProperties(
            archiveName = compressedFile.name,
            archivePath = compressedFile.absolutePath,
            totalFiles = 1,
            totalFolders = 0,
            uncompressedSize = uncompressed,
            compressedSize = compressedFile.length(),
            lastModified = compressedFile.lastModified(),
            formatDisplayName = type?.displayName ?: "Compressed File",
            compressionMethod = type?.displayName ?: "Standalone Stream",
            isEncrypted = false,
            encryptionDetails = "None"
        )
    }

    private fun openCompressorInputStream(file: File, type: StandaloneCompressionType): CompressorInputStream {
        val buffered = BufferedInputStream(FileInputStream(file), 64 * 1024)
        return when (type) {
            StandaloneCompressionType.GZIP -> GzipCompressorInputStream(buffered)
            StandaloneCompressionType.BZIP2 -> BZip2CompressorInputStream(buffered)
            StandaloneCompressionType.XZ -> XZCompressorInputStream(buffered)
            StandaloneCompressionType.Z -> ZCompressorInputStream(buffered)
        }
    }

    private fun openCompressorOutputStream(file: File, type: StandaloneCompressionType): CompressorOutputStream {
        val buffered = BufferedOutputStream(FileOutputStream(file), 64 * 1024)
        return when (type) {
            StandaloneCompressionType.GZIP -> GzipCompressorOutputStream(buffered)
            StandaloneCompressionType.BZIP2 -> BZip2CompressorOutputStream(buffered)
            StandaloneCompressionType.XZ -> XZCompressorOutputStream(buffered)
            StandaloneCompressionType.Z -> throw UnsupportedOperationException("Creation of Unix Compress (.Z) files is not supported.")
        }
    }

    private fun resolveConflictFile(targetFile: File, conflictResolution: ConflictResolution): File? {
        if (!targetFile.exists()) return targetFile

        return when (conflictResolution) {
            ConflictResolution.OVERWRITE -> {
                targetFile.delete()
                targetFile
            }
            ConflictResolution.SKIP -> null
            ConflictResolution.CANCEL -> throw CancellationException("Extraction cancelled due to file conflict: ${targetFile.name}")
            ConflictResolution.RENAME_AUTO -> {
                val parent = targetFile.parentFile ?: return targetFile
                val nameWithoutExt = targetFile.nameWithoutExtension
                val ext = targetFile.extension
                var index = 1
                var candidate: File
                do {
                    val newName = if (ext.isNotEmpty()) "$nameWithoutExt ($index).$ext" else "$nameWithoutExt ($index)"
                    candidate = File(parent, newName)
                    index++
                } while (candidate.exists())
                candidate
            }
        }
    }
}
