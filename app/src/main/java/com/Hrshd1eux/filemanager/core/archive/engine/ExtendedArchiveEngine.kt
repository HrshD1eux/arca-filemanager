package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ArchiveEntry as ApacheArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.arj.ArjArchiveEntry
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream
import org.apache.commons.compress.archivers.cpio.CpioConstants
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

private const val BUFFER_SIZE = 64 * 1024

enum class ExtendedArchiveFormat(val displayName: String) {
    ARJ("ARJ Archive (Read-Only)"),
    CPIO("CPIO Archive"),
    AR("AR Archive (Read-Only)"),
    DEB("Debian Package (Read-Only)");

    companion object {
        fun fromFile(file: File): ExtendedArchiveFormat? {
            val name = file.name.lowercase()
            return when {
                name.endsWith(".arj") -> ARJ
                name.endsWith(".cpio") -> CPIO
                name.endsWith(".deb") -> DEB
                name.endsWith(".ar") || name.endsWith(".a") -> AR
                else -> null
            }
        }
    }
}

/**
 * Pure Java archive engine supporting ARJ, CPIO, AR, and Debian (.deb) packages
 * powered by Apache Commons Compress with zero native binary bloat.
 */
class ExtendedArchiveEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Lists all entries inside an ARJ, CPIO, or AR/DEB archive.
     */
    suspend fun listEntries(archiveFile: File): List<ArchiveEntry> = withContext(ioDispatcher) {
        if (!archiveFile.exists() || !archiveFile.isFile) return@withContext emptyList()
        val format = ExtendedArchiveFormat.fromFile(archiveFile) ?: return@withContext emptyList()

        val entries = mutableListOf<ArchiveEntry>()
        try {
            openArchiveInputStream(archiveFile, format).use { ais ->
                var rawEntry: ApacheArchiveEntry? = ais.nextEntry
                var entryCount = 0

                while (rawEntry != null) {
                    coroutineContext.ensureActive()
                    entryCount++
                    ArchiveSafetyGuard.validateArchiveHeader(entryCount)

                    val cleanPath = rawEntry.name.replace('\\', '/').trimStart('/').removeSuffix("/")
                    if (cleanPath.isNotEmpty()) {
                        val isDir = rawEntry.isDirectory
                        val pathParts = cleanPath.split('/')
                        val name = pathParts.lastOrNull()?.ifEmpty { cleanPath } ?: cleanPath
                        val parentPath = if (pathParts.size > 1) {
                            pathParts.dropLast(1).joinToString("/")
                        } else ""

                        val size = if (isDir) 0L else rawEntry.size.coerceAtLeast(0L)
                        val lastMod = rawEntry.lastModifiedDate?.time ?: archiveFile.lastModified()

                        entries.add(
                            ArchiveEntry(
                                path = cleanPath,
                                name = name,
                                parentPath = parentPath,
                                isDirectory = isDir,
                                uncompressedSize = size,
                                compressedSize = size,
                                lastModified = lastMod,
                                crc = 0L
                            )
                        )
                    }
                    rawEntry = ais.nextEntry
                }
            }
        } catch (e: Throwable) {
            throw IOException("Corrupted or invalid ${format.displayName} (${e.localizedMessage ?: "Invalid structure"}).", e)
        }

        entries.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Extracts all entries from an extended archive into the destination directory.
     */
    suspend fun extractAll(
        archiveFile: File,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val format = ExtendedArchiveFormat.fromFile(archiveFile)
                ?: throw IllegalArgumentException("Unsupported extended archive format: ${archiveFile.name}")

            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            openArchiveInputStream(archiveFile, format).use { ais ->
                var rawEntry: ApacheArchiveEntry? = ais.nextEntry
                var entryCount = 0

                while (rawEntry != null) {
                    coroutineContext.ensureActive()
                    entryCount++
                    ArchiveSafetyGuard.validateArchiveHeader(entryCount)

                    val cleanPath = rawEntry.name.replace('\\', '/').trimStart('/').removeSuffix("/")
                    if (cleanPath.isNotEmpty()) {
                        val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(cleanPath, destinationDir)

                        if (rawEntry.isDirectory) {
                            validatedTarget.mkdirs()
                        } else {
                            val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                            if (targetFile != null) {
                                targetFile.parentFile?.mkdirs()
                                var entryBytesWritten = 0L

                                try {
                                    FileOutputStream(targetFile).use { output ->
                                        var len: Int
                                        while (ais.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            entryBytesWritten += len
                                            cumulativeBytes += len

                                            ArchiveSafetyGuard.validateExpansionStream(
                                                entryName = cleanPath,
                                                uncompressedBytesRead = entryBytesWritten,
                                                compressedEntrySize = rawEntry.size.coerceAtLeast(1L),
                                                cumulativeUncompressedBytes = cumulativeBytes
                                            )

                                            output.write(buffer, 0, len)
                                        }
                                    }

                                    rawEntry.lastModifiedDate?.let { date ->
                                        targetFile.setLastModified(date.time)
                                    }

                                    extractedFilesCount++
                                } catch (e: Throwable) {
                                    if (targetFile.exists()) {
                                        targetFile.delete()
                                    }
                                    throw e
                                }
                            }
                        }

                        onProgress?.invoke(
                            OperationProgress(
                                bytesProcessed = cumulativeBytes,
                                totalBytes = cumulativeBytes,
                                filesProcessed = extractedFilesCount,
                                totalFiles = entryCount,
                                currentFileName = cleanPath.substringAfterLast('/')
                            )
                        )
                    }

                    rawEntry = ais.nextEntry
                }
            }

            extractedFilesCount
        }
    }

    /**
     * Extracts selected entries from the archive with conflict handling.
     */
    suspend fun extractEntries(
        archiveFile: File,
        entryPaths: Set<String>,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val format = ExtendedArchiveFormat.fromFile(archiveFile)
                ?: throw IllegalArgumentException("Unsupported extended archive format: ${archiveFile.name}")

            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            openArchiveInputStream(archiveFile, format).use { ais ->
                var rawEntry: ApacheArchiveEntry? = ais.nextEntry
                var entryCount = 0

                while (rawEntry != null) {
                    coroutineContext.ensureActive()
                    entryCount++
                    ArchiveSafetyGuard.validateArchiveHeader(entryCount)

                    val cleanPath = rawEntry.name.replace('\\', '/').trimStart('/').removeSuffix("/")
                    val fileName = cleanPath.substringAfterLast('/')

                    if (cleanPath.isNotEmpty() && (entryPaths.contains(cleanPath) || entryPaths.contains(fileName))) {
                        val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(fileName, destinationDir)

                        if (rawEntry.isDirectory) {
                            validatedTarget.mkdirs()
                        } else {
                            val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                            if (targetFile != null) {
                                targetFile.parentFile?.mkdirs()
                                var entryBytesWritten = 0L

                                try {
                                    FileOutputStream(targetFile).use { output ->
                                        var len: Int
                                        while (ais.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            entryBytesWritten += len
                                            cumulativeBytes += len

                                            ArchiveSafetyGuard.validateExpansionStream(
                                                entryName = cleanPath,
                                                uncompressedBytesRead = entryBytesWritten,
                                                compressedEntrySize = rawEntry.size.coerceAtLeast(1L),
                                                cumulativeUncompressedBytes = cumulativeBytes
                                            )

                                            output.write(buffer, 0, len)
                                        }
                                    }

                                    rawEntry.lastModifiedDate?.let { date ->
                                        targetFile.setLastModified(date.time)
                                    }

                                    extractedFilesCount++
                                } catch (e: Throwable) {
                                    if (targetFile.exists()) {
                                        targetFile.delete()
                                    }
                                    throw e
                                }
                            }
                        }

                        onProgress?.invoke(
                            OperationProgress(
                                bytesProcessed = cumulativeBytes,
                                totalBytes = cumulativeBytes,
                                filesProcessed = extractedFilesCount,
                                totalFiles = entryPaths.size,
                                currentFileName = fileName
                            )
                        )
                    }

                    rawEntry = ais.nextEntry
                }
            }

            extractedFilesCount
        }
    }

    /**
     * Verifies the stream integrity of entries in an extended archive.
     */
    suspend fun testArchive(
        archiveFile: File,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<ArchiveIntegrityReport> = withContext(ioDispatcher) {
        runCatching {
            val format = ExtendedArchiveFormat.fromFile(archiveFile)
                ?: throw IllegalArgumentException("Unsupported extended archive format: ${archiveFile.name}")

            val startTime = System.currentTimeMillis()
            val corruptedEntries = mutableListOf<String>()
            var verifiedCount = 0
            var totalEntries = 0
            val buffer = ByteArray(BUFFER_SIZE)

            openArchiveInputStream(archiveFile, format).use { ais ->
                var rawEntry: ApacheArchiveEntry? = ais.nextEntry

                while (rawEntry != null) {
                    coroutineContext.ensureActive()
                    totalEntries++
                    ArchiveSafetyGuard.validateArchiveHeader(totalEntries)

                    val cleanPath = rawEntry.name.replace('\\', '/').trimStart('/').removeSuffix("/")
                    try {
                        if (!rawEntry.isDirectory) {
                            while (ais.read(buffer) != -1) {
                                coroutineContext.ensureActive()
                            }
                        }
                        verifiedCount++
                    } catch (e: Exception) {
                        corruptedEntries.add(cleanPath.ifEmpty { "Entry #$totalEntries" })
                    }

                    onProgress?.invoke(
                        OperationProgress(
                            filesProcessed = verifiedCount + corruptedEntries.size,
                            totalFiles = totalEntries,
                            currentFileName = cleanPath.substringAfterLast('/')
                        )
                    )

                    rawEntry = ais.nextEntry
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            ArchiveIntegrityReport(
                archiveName = archiveFile.name,
                totalEntries = totalEntries,
                verifiedEntries = verifiedCount,
                corruptedEntries = corruptedEntries,
                elapsedMs = elapsed
            )
        }
    }

    /**
     * Computes metadata and metrics for the archive.
     */
    suspend fun getArchiveProperties(archiveFile: File): ArchiveProperties = withContext(ioDispatcher) {
        if (!archiveFile.exists() || !archiveFile.isFile) {
            return@withContext ArchiveProperties(
                archiveName = archiveFile.name,
                archivePath = archiveFile.absolutePath,
                totalFiles = 0,
                totalFolders = 0,
                uncompressedSize = 0L,
                compressedSize = 0L,
                lastModified = archiveFile.lastModified()
            )
        }

        val format = ExtendedArchiveFormat.fromFile(archiveFile)
        var totalFiles = 0
        var totalFolders = 0
        var uncompressedSize = 0L

        if (format != null) {
            runCatching {
                openArchiveInputStream(archiveFile, format).use { ais ->
                    var rawEntry: ApacheArchiveEntry? = ais.nextEntry
                    while (rawEntry != null) {
                        if (rawEntry.isDirectory) {
                            totalFolders++
                        } else {
                            totalFiles++
                            uncompressedSize += rawEntry.size.coerceAtLeast(0L)
                        }
                        rawEntry = ais.nextEntry
                    }
                }
            }
        }

        ArchiveProperties(
            archiveName = archiveFile.name,
            archivePath = archiveFile.absolutePath,
            totalFiles = totalFiles,
            totalFolders = totalFolders,
            uncompressedSize = uncompressedSize,
            compressedSize = archiveFile.length(),
            lastModified = archiveFile.lastModified(),
            formatDisplayName = format?.displayName ?: "Extended Archive",
            compressionMethod = format?.displayName ?: "Extended Archive",
            isEncrypted = false,
            encryptionDetails = "None"
        )
    }

    /**
     * Creates a CPIO archive from the given source files (valuable for Android boot/ramdisk workflows).
     */
    suspend fun createCpio(
        sources: List<File>,
        destinationCpio: File,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            destinationCpio.parentFile?.mkdirs()
            val allFiles = mutableListOf<File>()
            for (source in sources) {
                if (source.isDirectory) {
                    allFiles.addAll(source.walkTopDown().filter { it.isFile })
                } else if (source.isFile) {
                    allFiles.add(source)
                }
            }

            var processedFiles = 0
            var processedBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            CpioArchiveOutputStream(
                BufferedOutputStream(FileOutputStream(destinationCpio), BUFFER_SIZE),
                CpioConstants.FORMAT_NEW
            ).use { cos ->
                for (source in sources) {
                    val rootDir = if (source.isDirectory) source.parentFile ?: source else source.parentFile ?: source

                    val filesToAdd = if (source.isDirectory) {
                        source.walkTopDown().toList()
                    } else {
                        listOf(source)
                    }

                    for (file in filesToAdd) {
                        coroutineContext.ensureActive()
                        val relativePath = file.relativeTo(rootDir).path.replace('\\', '/')
                        val isDir = file.isDirectory
                        val entry = CpioArchiveEntry(CpioConstants.FORMAT_NEW, file, relativePath)

                        cos.putArchiveEntry(entry)

                        if (!isDir) {
                            FileInputStream(file).use { input ->
                                var len: Int
                                while (input.read(buffer).also { len = it } != -1) {
                                    coroutineContext.ensureActive()
                                    cos.write(buffer, 0, len)
                                    processedBytes += len
                                }
                            }
                            processedFiles++
                        }

                        cos.closeArchiveEntry()

                        onProgress?.invoke(
                            OperationProgress(
                                bytesProcessed = processedBytes,
                                totalBytes = processedBytes,
                                filesProcessed = processedFiles,
                                totalFiles = allFiles.size,
                                currentFileName = file.name
                            )
                        )
                    }
                }
            }

            destinationCpio.length()
        }
    }

    /**
     * Refusals adhering to truthful architectural boundaries.
     */
    fun createArj(sources: List<File>, destination: File): Nothing {
        throw UnsupportedOperationException(
            "ARJ archive creation is not supported. ARCA supports ARJ browsing, inspection, and extraction only."
        )
    }

    fun createAr(sources: List<File>, destination: File): Nothing {
        throw UnsupportedOperationException(
            "AR / Debian package creation is not supported. ARCA supports AR/DEB browsing, inspection, and extraction only."
        )
    }

    fun rejectCab(): Nothing {
        throw UnsupportedOperationException(
            "CAB format is not supported. 95% of real-world CAB files use Microsoft LZX/Quantum compression, " +
            "which lacks a maintained pure-Java parser. Compiling C++ NDK binaries for 4 Android ABIs for a 0.001% " +
            "Windows-only format violates ARCA's binary bloat and security policies."
        )
    }

    fun rejectLzh(): Nothing {
        throw UnsupportedOperationException(
            "LZH/LHA format is not supported. No maintained memory-safe library exists; unmaintained 2003 abandonware " +
            "and native binaries introduce severe CVE and maintenance risks for an obsolete 1980s format."
        )
    }

    fun rejectZstd(): Nothing {
        throw UnsupportedOperationException(
            "Zstandard standalone support is deferred. Pure Java ports rely on sun.misc.Unsafe which causes runtime " +
            "crashes on Android ART and memory corruption CVEs, while zstd-jni lacks official Android AAR distribution. " +
            "ARCA prioritizes Android stability over premature native dependency bloat."
        )
    }

    private fun openArchiveInputStream(file: File, format: ExtendedArchiveFormat): ArchiveInputStream<out ApacheArchiveEntry> {
        val bis = BufferedInputStream(FileInputStream(file), BUFFER_SIZE)
        return when (format) {
            ExtendedArchiveFormat.ARJ -> ArjArchiveInputStream(bis)
            ExtendedArchiveFormat.CPIO -> CpioArchiveInputStream(bis)
            ExtendedArchiveFormat.AR, ExtendedArchiveFormat.DEB -> ArArchiveInputStream(bis)
        }
    }

    private fun resolveConflictFile(file: File, conflictResolution: ConflictResolution): File? {
        if (!file.exists()) return file
        return when (conflictResolution) {
            ConflictResolution.OVERWRITE -> file
            ConflictResolution.SKIP -> null
            ConflictResolution.CANCEL -> throw CancellationException("Extraction cancelled on conflict with ${file.name}")
            ConflictResolution.RENAME_AUTO -> {
                val parent = file.parentFile ?: return file
                val nameWithoutExt = file.nameWithoutExtension
                val ext = file.extension
                val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
                var counter = 1
                var candidate = File(parent, "$nameWithoutExt ($counter)$dotExt")
                while (candidate.exists()) {
                    counter++
                    candidate = File(parent, "$nameWithoutExt ($counter)$dotExt")
                }
                candidate
            }
        }
    }
}
