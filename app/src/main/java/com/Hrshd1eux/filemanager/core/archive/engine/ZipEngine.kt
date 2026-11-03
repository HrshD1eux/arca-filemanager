package com.Hrshd1eux.filemanager.core.archive.engine

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.archive.security.ArchivePasswordRequiredException
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyException
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.archive.split.SplitArchiveResolver
import com.Hrshd1eux.filemanager.core.archive.split.SplitResolution
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.verification.FileVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

class ZipEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Checks if a ZIP archive is password-protected/encrypted.
     */
    fun isEncrypted(zipFile: File): Boolean {
        if (!zipFile.exists() || !zipFile.isFile) return false
        return runCatching {
            val zf = ZipFile(zipFile)
            zf.isEncrypted
        }.getOrDefault(false)
    }

    /**
     * Lists all entries inside a .zip file with safety validation and password checking.
     */
    suspend fun listEntries(zipFile: File, password: CharArray? = null): List<ArchiveEntry> = withContext(ioDispatcher) {
        if (!zipFile.exists() || !zipFile.isFile) return@withContext emptyList()

        val entries = mutableListOf<ArchiveEntry>()
        try {
            val zf = ZipFile(zipFile)
            if (password != null && password.isNotEmpty()) {
                zf.setPassword(password)
            }
            if (zf.isEncrypted && (password == null || password.isEmpty())) {
                throw ArchivePasswordRequiredException("ZIP archive is password protected. Password required.")
            }

            val headers = zf.fileHeaders
            ArchiveSafetyGuard.validateArchiveHeader(headers.size)

            for (header in headers) {
                val cleanPath = runCatching {
                    ArchiveSafetyGuard.sanitizeAndValidateEntryName(header.fileName)
                }.getOrDefault(header.fileName.replace('\\', '/'))

                val rawName = cleanPath.removeSuffix("/")
                val isDir = header.isDirectory || header.fileName.endsWith("/") || header.fileName.endsWith("\\")
                val pathParts = rawName.split("/")
                val name = pathParts.lastOrNull()?.ifEmpty { rawName } ?: rawName
                val parentPath = if (pathParts.size > 1) {
                    pathParts.dropLast(1).joinToString("/")
                } else ""

                val uncompressedSize = if (isDir) 0L else header.uncompressedSize.coerceAtLeast(0L)
                val compressedSize = if (isDir) 0L else header.compressedSize.coerceAtLeast(0L)
                val crcValue = header.crc and 0xFFFFFFFFL
                val lastModifiedEpoch = if (header.lastModifiedTimeEpoch > 0) {
                    header.lastModifiedTimeEpoch
                } else {
                    zipFile.lastModified()
                }

                entries.add(
                    ArchiveEntry(
                        path = rawName,
                        name = name,
                        parentPath = parentPath,
                        isDirectory = isDir,
                        uncompressedSize = uncompressedSize,
                        compressedSize = compressedSize,
                        lastModified = lastModifiedEpoch,
                        crc = crcValue
                    )
                )
            }
        } catch (e: ArchivePasswordRequiredException) {
            throw e
        } catch (e: Throwable) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("encrypted") || msg.contains("wrong password")) {
                throw ArchivePasswordRequiredException("Password required or incorrect password for ZIP archive.", e)
            }
            throw IOException("Corrupted or invalid ZIP archive (${e.localizedMessage ?: "Invalid structure"}).", e)
        }
        entries.sortedWith(compareBy<ArchiveEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Extracts all files from the ZIP into destination directory with password decryption and ArchiveSafetyGuard protections.
     */
    suspend fun extractAll(
        zipFile: File,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        password: CharArray? = null,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val zf = ZipFile(zipFile)
            if (password != null && password.isNotEmpty()) {
                zf.setPassword(password)
            }
            if (zf.isEncrypted && (password == null || password.isEmpty())) {
                throw ArchivePasswordRequiredException("ZIP archive is password protected. Password required.")
            }

            val headers = zf.fileHeaders
            ArchiveSafetyGuard.validateArchiveHeader(headers.size)
            val totalEntries = headers.size
            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            for (header in headers) {
                coroutineContext.ensureActive()

                val cleanName = header.fileName.replace('\\', '/')
                val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(cleanName, destinationDir)

                if (header.isDirectory || cleanName.endsWith("/")) {
                    validatedTarget.mkdirs()
                } else {
                    val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                    if (targetFile != null) {
                        targetFile.parentFile?.mkdirs()
                        var entryBytesRead = 0L

                        try {
                            zf.getInputStream(header).use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    var len: Int
                                    while (input.read(buffer).also { len = it } != -1) {
                                        coroutineContext.ensureActive()
                                        entryBytesRead += len
                                        cumulativeBytes += len

                                        ArchiveSafetyGuard.validateExpansionStream(
                                            entryName = header.fileName,
                                            uncompressedBytesRead = entryBytesRead,
                                            compressedEntrySize = header.compressedSize.coerceAtLeast(1L),
                                            cumulativeUncompressedBytes = cumulativeBytes
                                        )

                                        output.write(buffer, 0, len)
                                    }
                                }
                            }
                            if (header.lastModifiedTimeEpoch > 0) {
                                targetFile.setLastModified(header.lastModifiedTimeEpoch)
                            }
                            if (verifyIntegrity && header.crc > 0) {
                                FileVerifier.verifyCrc(targetFile, header.crc and 0xFFFFFFFFL, ioDispatcher)
                            }
                            extractedFilesCount++
                        } catch (e: Throwable) {
                            if (targetFile.exists()) {
                                targetFile.delete()
                            }
                            val msg = e.message?.lowercase() ?: ""
                            if (msg.contains("password") || msg.contains("wrong password") || msg.contains("encrypted")) {
                                throw ArchivePasswordRequiredException("Password required or incorrect password for ZIP archive.", e)
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
                        totalFiles = totalEntries,
                        currentFileName = header.fileName.substringAfterLast('/').substringAfterLast('\\')
                    )
                )
            }
            extractedFilesCount
        }.recoverCatching { e ->
            if (e is ArchivePasswordRequiredException) throw e
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("wrong password") || msg.contains("encrypted")) {
                throw ArchivePasswordRequiredException("Password required or incorrect password for ZIP archive.", e)
            }
            throw e
        }
    }

    /**
     * Extracts specific entries from the ZIP with password support and safety protections.
     */
    suspend fun extractEntries(
        zipFile: File,
        entryPaths: Set<String>,
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        password: CharArray? = null,
        verifyIntegrity: Boolean = false,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            val zf = ZipFile(zipFile)
            if (password != null && password.isNotEmpty()) {
                zf.setPassword(password)
            }
            if (zf.isEncrypted && (password == null || password.isEmpty())) {
                throw ArchivePasswordRequiredException("ZIP archive is password protected. Password required.")
            }

            var extractedFilesCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            val allHeaders = zf.fileHeaders

            for (entryPath in entryPaths) {
                coroutineContext.ensureActive()
                val cleanPath = entryPath.replace('\\', '/')
                val header = allHeaders.firstOrNull { h ->
                    val hClean = h.fileName.replace('\\', '/').removeSuffix("/")
                    hClean == cleanPath.removeSuffix("/") || h.fileName == entryPath
                } ?: continue

                val validatedTarget = ArchiveSafetyGuard.resolveAndValidateTarget(
                    header.fileName.substringAfterLast('/').substringAfterLast('\\'),
                    destinationDir
                )

                if (header.isDirectory || header.fileName.endsWith("/")) {
                    validatedTarget.mkdirs()
                } else {
                    val targetFile = resolveConflictFile(validatedTarget, conflictResolution)
                    if (targetFile != null) {
                        targetFile.parentFile?.mkdirs()
                        var entryBytesRead = 0L

                        try {
                            zf.getInputStream(header).use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    var len: Int
                                    while (input.read(buffer).also { len = it } != -1) {
                                        coroutineContext.ensureActive()
                                        entryBytesRead += len
                                        cumulativeBytes += len

                                        ArchiveSafetyGuard.validateExpansionStream(
                                            entryName = header.fileName,
                                            uncompressedBytesRead = entryBytesRead,
                                            compressedEntrySize = header.compressedSize.coerceAtLeast(1L),
                                            cumulativeUncompressedBytes = cumulativeBytes
                                        )

                                        output.write(buffer, 0, len)
                                    }
                                }
                            }
                            if (header.lastModifiedTimeEpoch > 0) {
                                targetFile.setLastModified(header.lastModifiedTimeEpoch)
                            }
                            if (verifyIntegrity && header.crc > 0) {
                                FileVerifier.verifyCrc(targetFile, header.crc and 0xFFFFFFFFL, ioDispatcher)
                            }
                            extractedFilesCount++
                        } catch (e: Throwable) {
                            if (targetFile.exists()) {
                                targetFile.delete()
                            }
                            val msg = e.message?.lowercase() ?: ""
                            if (msg.contains("password") || msg.contains("wrong password") || msg.contains("encrypted")) {
                                throw ArchivePasswordRequiredException("Password required or incorrect password for ZIP archive.", e)
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
                        currentFileName = header.fileName.substringAfterLast('/').substringAfterLast('\\')
                    )
                )
            }
            extractedFilesCount
        }.recoverCatching { e ->
            if (e is ArchivePasswordRequiredException) throw e
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("wrong password") || msg.contains("encrypted")) {
                throw ArchivePasswordRequiredException("Password required or incorrect password for ZIP archive.", e)
            }
            throw e
        }
    }

    /**
     * Compresses a list of files or directories into a standard .zip archive.
     */
    suspend fun createZip(
        sources: List<File>,
        destinationZip: File,
        compressionLevel: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            destinationZip.parentFile?.mkdirs()

            var totalBytes = 0L
            var totalFiles = 0
            sources.forEach { source ->
                if (source.isDirectory) {
                    source.walkTopDown().onFail { _, _ -> }.forEach { f ->
                        if (!f.isDirectory) {
                            totalBytes += f.length()
                            totalFiles++
                        }
                    }
                } else if (source.isFile) {
                    totalBytes += source.length()
                    totalFiles++
                }
            }

            var bytesProcessed = 0L
            var filesProcessed = 0
            val buffer = ByteArray(BUFFER_SIZE)

            try {
                ZipOutputStream(FileOutputStream(destinationZip).buffered()).use { zos ->
                    zos.setLevel(compressionLevel)
                    for (source in sources) {
                        coroutineContext.ensureActive()
                        if (!source.exists()) {
                            throw FileNotFoundException("Source file does not exist: ${source.absolutePath}")
                        }

                        if (source.isDirectory) {
                            val baseDir = source.parentFile ?: source
                            source.walkTopDown().onFail { _, _ -> }.forEach { file ->
                                coroutineContext.ensureActive()
                                val relativePath = file.relativeTo(baseDir).path.replace('\\', '/')
                                if (file.isDirectory) {
                                    val entryName = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
                                    zos.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
                                    zos.closeEntry()
                                } else {
                                    zos.putNextEntry(ZipEntry(relativePath).apply { time = file.lastModified() })
                                    FileInputStream(file).use { fis ->
                                        var len: Int
                                        while (fis.read(buffer).also { len = it } != -1) {
                                            coroutineContext.ensureActive()
                                            zos.write(buffer, 0, len)
                                            bytesProcessed += len
                                        }
                                    }
                                    zos.closeEntry()
                                    filesProcessed++
                                }

                                onProgress?.invoke(
                                    OperationProgress(
                                        bytesProcessed = bytesProcessed,
                                        totalBytes = totalBytes,
                                        filesProcessed = filesProcessed,
                                        totalFiles = totalFiles,
                                        currentFileName = file.name
                                    )
                                )
                            }
                        } else if (source.isFile) {
                            zos.putNextEntry(ZipEntry(source.name).apply { time = source.lastModified() })
                            FileInputStream(source).use { fis ->
                                var len: Int
                                while (fis.read(buffer).also { len = it } != -1) {
                                    coroutineContext.ensureActive()
                                    zos.write(buffer, 0, len)
                                    bytesProcessed += len
                                }
                            }
                            zos.closeEntry()
                            filesProcessed++

                            onProgress?.invoke(
                                OperationProgress(
                                    bytesProcessed = bytesProcessed,
                                    totalBytes = totalBytes,
                                    filesProcessed = filesProcessed,
                                    totalFiles = totalFiles,
                                    currentFileName = source.name
                                )
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (destinationZip.exists()) {
                    destinationZip.delete()
                }
                throw t
            }

            destinationZip.length()
        }
    }

    /**
     * Verifies the CRC-32 integrity of all entries in the ZIP archive without writing to disk.
     */
    suspend fun testArchive(
        zipFile: File,
        password: CharArray? = null,
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<ArchiveIntegrityReport> = withContext(ioDispatcher) {
        runCatching {
            val startTime = System.currentTimeMillis()
            val corruptedEntries = mutableListOf<String>()
            var verifiedCount = 0
            var cumulativeBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            val zf = ZipFile(zipFile)
            if (password != null && password.isNotEmpty()) {
                zf.setPassword(password)
            }
            if (zf.isEncrypted && (password == null || password.isEmpty())) {
                throw ArchivePasswordRequiredException("ZIP archive is password protected. Password required.")
            }

            val headers = zf.fileHeaders
            ArchiveSafetyGuard.validateArchiveHeader(headers.size)
            val totalEntries = headers.size

            for (header in headers) {
                coroutineContext.ensureActive()
                val crcCalculator = CRC32()
                var entryBytesRead = 0L

                try {
                    if (!header.isDirectory && !header.fileName.endsWith("/")) {
                        zf.getInputStream(header).use { input ->
                            var len: Int
                            while (input.read(buffer).also { len = it } != -1) {
                                entryBytesRead += len
                                cumulativeBytes += len

                                ArchiveSafetyGuard.validateExpansionStream(
                                    entryName = header.fileName,
                                    uncompressedBytesRead = entryBytesRead,
                                    compressedEntrySize = header.compressedSize.coerceAtLeast(1L),
                                    cumulativeUncompressedBytes = cumulativeBytes
                                )

                                crcCalculator.update(buffer, 0, len)
                            }
                        }
                        val expectedCrc = header.crc and 0xFFFFFFFFL
                        if (expectedCrc > 0 && crcCalculator.value != expectedCrc) {
                            corruptedEntries.add(header.fileName)
                        } else {
                            verifiedCount++
                        }
                    } else {
                        verifiedCount++
                    }
                } catch (e: Exception) {
                    if (e is ArchiveSafetyException) throw e
                    corruptedEntries.add(header.fileName)
                }

                onProgress?.invoke(
                    OperationProgress(
                        filesProcessed = verifiedCount + corruptedEntries.size,
                        totalFiles = totalEntries,
                        currentFileName = header.fileName.substringAfterLast('/').substringAfterLast('\\')
                    )
                )
            }

            val elapsed = System.currentTimeMillis() - startTime
            ArchiveIntegrityReport(
                archiveName = zipFile.name,
                totalEntries = totalEntries,
                verifiedEntries = verifiedCount,
                corruptedEntries = corruptedEntries,
                elapsedMs = elapsed
            )
        }
    }

    /**
     * Safely appends/adds new files or directories into an existing ZIP archive.
     */
    suspend fun addEntries(
        zipFile: File,
        newSources: List<File>,
        targetVirtualPath: String = "",
        onProgress: ((OperationProgress) -> Unit)? = null
    ): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val tempZip = File(zipFile.parentFile, "${zipFile.name}.tmp_${System.currentTimeMillis()}")
            val buffer = ByteArray(BUFFER_SIZE)

            val entriesToAdd = mutableListOf<Pair<String, File>>()
            for (source in newSources) {
                if (source.isDirectory) {
                    val baseDir = source.parentFile ?: source
                    source.walkTopDown().onFail { _, _ -> }.forEach { file ->
                        val rel = file.relativeTo(baseDir).path.replace('\\', '/')
                        val finalPath = if (targetVirtualPath.isEmpty()) rel else "$targetVirtualPath/$rel"
                        entriesToAdd.add(finalPath to file)
                    }
                } else if (source.isFile) {
                    val finalPath = if (targetVirtualPath.isEmpty()) source.name else "$targetVirtualPath/${source.name}"
                    entriesToAdd.add(finalPath to source)
                }
            }
            val newPathSet = entriesToAdd.map { it.first }.toSet()

            ZipOutputStream(FileOutputStream(tempZip).buffered()).use { zos ->
                if (zipFile.exists() && zipFile.length() > 0) {
                    val zf = ZipFile(zipFile)
                    val headers = zf.fileHeaders
                    for (header in headers) {
                        coroutineContext.ensureActive()
                        val cleanName = header.fileName.replace('\\', '/').removeSuffix("/")
                        if (!newPathSet.contains(cleanName) && !newPathSet.contains(header.fileName)) {
                            zos.putNextEntry(ZipEntry(header.fileName).apply {
                                time = header.lastModifiedTimeEpoch
                            })
                            if (!header.isDirectory && !header.fileName.endsWith("/")) {
                                zf.getInputStream(header).use { input ->
                                    var len: Int
                                    while (input.read(buffer).also { len = it } != -1) {
                                        zos.write(buffer, 0, len)
                                    }
                                }
                            }
                            zos.closeEntry()
                        }
                    }
                }

                for ((entryPath, file) in entriesToAdd) {
                    coroutineContext.ensureActive()
                    if (file.isDirectory) {
                        val dirPath = if (entryPath.endsWith("/")) entryPath else "$entryPath/"
                        zos.putNextEntry(ZipEntry(dirPath).apply { time = file.lastModified() })
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(entryPath).apply { time = file.lastModified() })
                        FileInputStream(file).use { fis ->
                            var len: Int
                            while (fis.read(buffer).also { len = it } != -1) {
                                zos.write(buffer, 0, len)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }

            if (zipFile.exists()) {
                zipFile.delete()
            }
            tempZip.renameTo(zipFile)
            zipFile.length()
        }
    }

    /**
     * Computes metadata and compression footprint properties for the ZIP file.
     */
    suspend fun getArchiveProperties(zipFile: File): ArchiveProperties = withContext(ioDispatcher) {
        if (!zipFile.exists() || !zipFile.isFile) {
            return@withContext ArchiveProperties(
                archiveName = zipFile.name,
                archivePath = zipFile.absolutePath,
                totalFiles = 0,
                totalFolders = 0,
                uncompressedSize = 0L,
                compressedSize = 0L,
                lastModified = zipFile.lastModified()
            )
        }

        var totalFiles = 0
        var totalFolders = 0
        var uncompressedSize = 0L
        var compressedSize = 0L
        var isEncrypted = false

        runCatching {
            val zf = ZipFile(zipFile)
            isEncrypted = zf.isEncrypted
            val headers = zf.fileHeaders
            for (header in headers) {
                if (header.isDirectory || header.fileName.endsWith("/") || header.fileName.endsWith("\\")) {
                    totalFolders++
                } else {
                    totalFiles++
                    uncompressedSize += header.uncompressedSize.coerceAtLeast(0L)
                    compressedSize += header.compressedSize.coerceAtLeast(0L)
                }
            }
        }

        val resolution = SplitArchiveResolver.resolveSplitParts(zipFile)
        val isSplit = resolution is SplitResolution.Success && resolution.isSplit
        val volumeCount = if (resolution is SplitResolution.Success && resolution.isSplit) resolution.parts.size else 1
        val finalName = if (isSplit) "${(resolution as SplitResolution.Success).baseName} ($volumeCount volumes)" else zipFile.name
        val finalCompressedSize = if (isSplit) (resolution as SplitResolution.Success).totalSize else (if (compressedSize > 0) compressedSize else zipFile.length())

        ArchiveProperties(
            archiveName = finalName,
            archivePath = zipFile.absolutePath,
            totalFiles = totalFiles,
            totalFolders = totalFolders,
            uncompressedSize = uncompressedSize,
            compressedSize = finalCompressedSize,
            lastModified = zipFile.lastModified(),
            formatDisplayName = "ZIP Archive",
            compressionMethod = "Standard Deflate",
            isEncrypted = isEncrypted,
            encryptionDetails = if (isEncrypted) "Password Protected (AES / ZipCrypto)" else "None",
            isSolid = false,
            isSplit = isSplit,
            splitVolumeCount = volumeCount,
            comment = null
        )
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

    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
    }
}
