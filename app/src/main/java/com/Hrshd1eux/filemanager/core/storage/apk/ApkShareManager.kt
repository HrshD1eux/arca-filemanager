package com.Hrshd1eux.filemanager.core.storage.apk

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import androidx.core.content.FileProvider
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Target export mode when sharing an installed application's APKs.
 */
enum class ApkShareMode {
    /**
     * Share only the base APK (standard standalone APK, directly installable on any Android device).
     */
    BASE_ONLY,

    /**
     * Package base APK plus all device-specific split APKs into a single unified .apks bundle archive.
     */
    BUNDLE_APKS,

    /**
     * Share all APK components: base APK plus all device-specific split APKs as individual files.
     */
    ALL_SPLITS
}

/**
 * Inspection report detailing accessible APK components, split detection, and file sizes.
 */
data class ApkShareInspection(
    val isShareable: Boolean,
    val isSplitApk: Boolean,
    val baseFile: File?,
    val baseSizeBytes: Long = 0L,
    val splitFiles: List<File> = emptyList(),
    val splitSizeBytes: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val inaccessibilityReason: String? = null
) {
    val formattedBaseSize: String
        get() = if (baseSizeBytes > 0L) FileFormatter.formatFileSize(baseSizeBytes) else "Unavailable"

    val formattedSplitSize: String
        get() = if (splitSizeBytes > 0L) FileFormatter.formatFileSize(splitSizeBytes) else "None"

    val formattedTotalSize: String
        get() = if (totalSizeBytes > 0L) FileFormatter.formatFileSize(totalSizeBytes) else "Unavailable"
}

/**
 * Result of preparing an APK for sharing.
 */
sealed class ApkShareResult {
    data class Success(
        val chooserIntent: Intent,
        val shareIntent: Intent,
        val sharedFileCount: Int,
        val totalBytes: Long,
        val targetFiles: List<File>,
        val uris: List<Uri>
    ) : ApkShareResult()

    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long,
        val formattedRequired: String,
        val formattedAvailable: String
    ) : ApkShareResult()

    data class Inaccessible(
        val reason: String
    ) : ApkShareResult()

    data class ExtractionFailed(
        val message: String,
        val cause: Throwable? = null
    ) : ApkShareResult()

    data object Cancelled : ApkShareResult()
}

/**
 * Manager responsible for securely extracting, preparing, and dispatching installed application
 * APK files through Android's standard sharing interface (Single & Batch export).
 *
 * Adheres strictly to security and platform constraints (Milestones 1.3.21–1.3.30):
 * 1. Checks accessibility of installed package APK binaries.
 * 2. Detects split APKs (Android App Bundles) and explains limitations.
 * 3. Verifies available cache storage before copying files to prevent storage exhaustion.
 * 4. Cleans up temporary copies to avoid storage leakage, reuses existing valid copies.
 * 5. Supports byte-level progress reporting and cooperative cancellation for large copies.
 * 6. Supports batch export for multiple accessible apps with clear UX.
 * 7. Uses secure FileProvider content URIs with FLAG_GRANT_READ_URI_PERMISSION and ClipData.
 * 8. Never exposes raw filesystem or file:// paths through sharing intents.
 * 9. Respects boundary: Only APK binary files are exported; zero app private data is copied.
 */
class ApkShareManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val safetyBufferBytes: Long = DEFAULT_SAFETY_BUFFER_BYTES,
    private val storageChecker: ((File) -> Long)? = null,
    private val uriProvider: ((Context?, String, File) -> Uri)? = null
) {

    companion object {
        const val MIME_TYPE_APK = "application/vnd.android.package-archive"
        const val SHARED_APKS_DIR_NAME = "shared_apks"
        const val DEFAULT_SAFETY_BUFFER_BYTES = 25 * 1024 * 1024L // 25 MB safety headroom
        const val DEFAULT_MAX_CACHE_AGE_MILLIS = 30 * 60 * 1000L // 30 minutes
        private const val STREAMING_BUFFER_BYTES = 64 * 1024 // 64 KB streaming buffer

        @Volatile
        private var instance: ApkShareManager? = null

        fun getInstance(): ApkShareManager {
            return instance ?: synchronized(this) {
                instance ?: ApkShareManager().also { instance = it }
            }
        }

        /**
         * Sanitizes a string for use in an extracted APK file name.
         */
        fun sanitizeFileName(name: String): String {
            val cleaned = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
            return cleaned.ifBlank { "app" }.take(36)
        }
    }

    /**
     * Inspects the installed application's APK binaries on disk to determine whether
     * they are readable, whether split APKs exist, and their respective byte sizes.
     */
    fun inspectShareableApks(app: InstalledAppInfo): ApkShareInspection {
        val sourceDir = app.sourceDir
        if (sourceDir.isNullOrBlank()) {
            return ApkShareInspection(
                isShareable = false,
                isSplitApk = false,
                baseFile = null,
                inaccessibilityReason = "No APK source directory was provided by the Android package manager."
            )
        }

        val baseFile = File(sourceDir)
        if (!baseFile.exists()) {
            return ApkShareInspection(
                isShareable = false,
                isSplitApk = false,
                baseFile = baseFile,
                inaccessibilityReason = "The base APK binary does not exist on disk at $sourceDir."
            )
        }

        if (!baseFile.canRead()) {
            return ApkShareInspection(
                isShareable = false,
                isSplitApk = false,
                baseFile = baseFile,
                inaccessibilityReason = "The operating system has restricted read access to this package binary."
            )
        }

        val baseLength = baseFile.length()
        if (baseLength <= 0L) {
            return ApkShareInspection(
                isShareable = false,
                isSplitApk = false,
                baseFile = baseFile,
                inaccessibilityReason = "The base APK binary has a zero-byte length on disk."
            )
        }

        // Inspect split APKs if any
        val splitFiles = ArrayList<File>()
        var splitLength = 0L
        for (split in app.extraInfo.splitApks) {
            val sFile = File(split.path)
            if (sFile.exists() && sFile.canRead() && sFile.length() > 0L) {
                splitFiles.add(sFile)
                splitLength += sFile.length()
            }
        }

        val isSplit = splitFiles.isNotEmpty() || app.extraInfo.splitApks.isNotEmpty()
        val totalLength = baseLength + splitLength

        return ApkShareInspection(
            isShareable = true,
            isSplitApk = isSplit,
            baseFile = baseFile,
            baseSizeBytes = baseLength,
            splitFiles = splitFiles,
            splitSizeBytes = splitLength,
            totalSizeBytes = totalLength
        )
    }

    /**
     * Checks available storage on the device's cache partition.
     */
    fun getAvailableCacheBytes(cacheDir: File): Long {
        return storageChecker?.invoke(cacheDir) ?: runCatching {
            val stat = StatFs(cacheDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
    }

    /**
     * Checks if there is sufficient cache storage to prepare APK copies.
     */
    fun hasSufficientSpace(cacheDir: File, requiredBytes: Long): Boolean {
        val available = getAvailableCacheBytes(cacheDir)
        return available >= (requiredBytes + safetyBufferBytes)
    }

    /**
     * Prunes old temporary APK copies in the cache directory to prevent storage buildup.
     */
    fun cleanupOldCopies(cacheDir: File, maxAgeMillis: Long = DEFAULT_MAX_CACHE_AGE_MILLIS) {
        val sharedDir = File(cacheDir, SHARED_APKS_DIR_NAME)
        if (!sharedDir.exists() || !sharedDir.isDirectory) return

        val now = System.currentTimeMillis()
        val files = sharedDir.listFiles() ?: return
        for (file in files) {
            if (now - file.lastModified() > maxAgeMillis) {
                file.delete()
            }
        }
    }

    /**
     * Clears all temporary APK copies in the cache directory immediately.
     */
    fun clearAllSharedCopies(cacheDir: File) {
        val sharedDir = File(cacheDir, SHARED_APKS_DIR_NAME)
        if (sharedDir.exists() && sharedDir.isDirectory) {
            sharedDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Prepares the installed application's APK binaries and generates an Android standard
     * sharing Intent configured with secure FileProvider content URIs, temporary read permissions,
     * progress reporting, and cooperative cancellation.
     */
    suspend fun prepareAndCreateShareIntent(
        context: Context?,
        app: InstalledAppInfo,
        shareMode: ApkShareMode = ApkShareMode.BASE_ONLY,
        customCacheDir: File? = null,
        onProgress: ((copiedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): ApkShareResult = withContext(ioDispatcher) {
        // 1. Inspect accessibility of APK binaries
        val inspection = inspectShareableApks(app)
        if (!inspection.isShareable || inspection.baseFile == null) {
            return@withContext ApkShareResult.Inaccessible(
                inspection.inaccessibilityReason ?: "The application package files are inaccessible."
            )
        }

        val cacheDir = customCacheDir ?: context?.cacheDir
        if (cacheDir == null) {
            return@withContext ApkShareResult.ExtractionFailed("No cache directory available for temporary APK extraction.")
        }

        val sanitizedApp = sanitizeFileName(app.appName)
        val sanitizedVer = sanitizeFileName(app.versionName ?: "v${app.versionCode ?: 0}")
        val authority = "${context?.packageName ?: "com.Hrshd1eux.filemanager"}.fileprovider"
        val buffer = ByteArray(STREAMING_BUFFER_BYTES)

        cleanupOldCopies(cacheDir)
        val sharedDir = File(cacheDir, SHARED_APKS_DIR_NAME).apply { if (!exists()) mkdirs() }

        val copiedFiles = ArrayList<File>()
        val uris = ArrayList<Uri>()

        if (shareMode == ApkShareMode.BUNDLE_APKS && inspection.isSplitApk && inspection.splitFiles.isNotEmpty()) {
            val allSourceFiles = listOf(inspection.baseFile) + inspection.splitFiles
            val requiredBytes = allSourceFiles.sumOf { it.length() }

            if (!hasSufficientSpace(cacheDir, requiredBytes)) {
                val available = getAvailableCacheBytes(cacheDir)
                val neededTotal = requiredBytes + safetyBufferBytes
                return@withContext ApkShareResult.InsufficientStorage(
                    requiredBytes = neededTotal,
                    availableBytes = available,
                    formattedRequired = FileFormatter.formatFileSize(neededTotal),
                    formattedAvailable = FileFormatter.formatFileSize(available)
                )
            }

            val bundleFile = File(sharedDir, "${sanitizedApp}_${sanitizedVer}.apks")
            val isCached = bundleFile.exists() && bundleFile.length() > 0L &&
                bundleFile.lastModified() >= inspection.baseFile.lastModified()

            if (!isCached) {
                if (bundleFile.exists()) bundleFile.delete()
                var overallCopied = 0L
                try {
                    java.util.zip.ZipOutputStream(bundleFile.outputStream().buffered(STREAMING_BUFFER_BYTES)).use { zipOut ->
                        // 1. base.apk
                        zipOut.putNextEntry(java.util.zip.ZipEntry("base.apk"))
                        inspection.baseFile.inputStream().buffered(STREAMING_BUFFER_BYTES).use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                if (!coroutineContext.isActive) {
                                    zipOut.close()
                                    bundleFile.delete()
                                    return@withContext ApkShareResult.Cancelled
                                }
                                zipOut.write(buffer, 0, read)
                                overallCopied += read
                                onProgress?.invoke(overallCopied, requiredBytes)
                            }
                        }
                        zipOut.closeEntry()

                        // 2. Splits
                        for (split in inspection.splitFiles) {
                            zipOut.putNextEntry(java.util.zip.ZipEntry(split.name))
                            split.inputStream().buffered(STREAMING_BUFFER_BYTES).use { input ->
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    if (!coroutineContext.isActive) {
                                        zipOut.close()
                                        bundleFile.delete()
                                        return@withContext ApkShareResult.Cancelled
                                    }
                                    zipOut.write(buffer, 0, read)
                                    overallCopied += read
                                    onProgress?.invoke(overallCopied, requiredBytes)
                                }
                            }
                            zipOut.closeEntry()
                        }
                    }
                } catch (e: Exception) {
                    bundleFile.delete()
                    return@withContext ApkShareResult.ExtractionFailed(
                        message = "Failed to bundle APK components: ${e.localizedMessage ?: "I/O error"}",
                        cause = e
                    )
                }
            } else {
                onProgress?.invoke(requiredBytes, requiredBytes)
            }

            copiedFiles.add(bundleFile)
            val contentUri = uriProvider?.invoke(context, authority, bundleFile)
                ?: if (context != null) FileProvider.getUriForFile(context, authority, bundleFile) else null
            if (contentUri != null) uris.add(contentUri)
        } else {
            // BASE_ONLY or ALL_SPLITS mode
            val filesToCopy = if (shareMode == ApkShareMode.BASE_ONLY || inspection.splitFiles.isEmpty()) {
                listOf(inspection.baseFile)
            } else {
                listOf(inspection.baseFile) + inspection.splitFiles
            }

            val requiredBytes = filesToCopy.sumOf { it.length() }
            if (!hasSufficientSpace(cacheDir, requiredBytes)) {
                val available = getAvailableCacheBytes(cacheDir)
                val neededTotal = requiredBytes + safetyBufferBytes
                return@withContext ApkShareResult.InsufficientStorage(
                    requiredBytes = neededTotal,
                    availableBytes = available,
                    formattedRequired = FileFormatter.formatFileSize(neededTotal),
                    formattedAvailable = FileFormatter.formatFileSize(available)
                )
            }

            var overallCopied = 0L
            try {
                for (sourceFile in filesToCopy) {
                    if (!coroutineContext.isActive) {
                        for (f in copiedFiles) f.delete()
                        return@withContext ApkShareResult.Cancelled
                    }

                    val isBase = sourceFile == inspection.baseFile
                    val destFileName = if (isBase) {
                        if (inspection.isSplitApk && shareMode == ApkShareMode.ALL_SPLITS) {
                            "${sanitizedApp}_${sanitizedVer}_base.apk"
                        } else {
                            "${sanitizedApp}_${sanitizedVer}.apk"
                        }
                    } else {
                        "${sanitizedApp}_${sanitizedVer}_${sourceFile.name}"
                    }

                    val destFile = File(sharedDir, destFileName)

                    // Temporary file reuse
                    if (destFile.exists() && destFile.length() == sourceFile.length() && destFile.lastModified() >= sourceFile.lastModified()) {
                        overallCopied += destFile.length()
                        onProgress?.invoke(overallCopied, requiredBytes)
                        copiedFiles.add(destFile)
                        val contentUri = uriProvider?.invoke(context, authority, destFile)
                            ?: if (context != null) FileProvider.getUriForFile(context, authority, destFile) else null
                        if (contentUri != null) uris.add(contentUri)
                        continue
                    }

                    if (destFile.exists()) {
                        destFile.delete()
                    }

                    sourceFile.inputStream().buffered(STREAMING_BUFFER_BYTES).use { input ->
                        destFile.outputStream().buffered(STREAMING_BUFFER_BYTES).use { output ->
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!coroutineContext.isActive) {
                                    output.close()
                                    destFile.delete()
                                    for (f in copiedFiles) f.delete()
                                    return@withContext ApkShareResult.Cancelled
                                }
                                output.write(buffer, 0, bytesRead)
                                overallCopied += bytesRead
                                onProgress?.invoke(overallCopied, requiredBytes)
                            }
                        }
                    }

                    copiedFiles.add(destFile)
                    val contentUri = uriProvider?.invoke(context, authority, destFile)
                        ?: if (context != null) FileProvider.getUriForFile(context, authority, destFile) else null
                    if (contentUri != null) {
                        uris.add(contentUri)
                    }
                }
            } catch (e: Exception) {
                for (f in copiedFiles) {
                    f.delete()
                }
                return@withContext ApkShareResult.ExtractionFailed(
                    message = "Failed to copy APK binaries for sharing: ${e.localizedMessage ?: "I/O error"}",
                    cause = e
                )
            }
        }

        if (uris.isEmpty()) {
            return@withContext ApkShareResult.ExtractionFailed("No APK files could be prepared for sharing.")
        }

        // Build standard Android sharing intent
        val isBundle = copiedFiles.firstOrNull()?.name?.endsWith(".apks", ignoreCase = true) == true
        val mimeType = if (isBundle) "application/vnd.android.package-archive" else MIME_TYPE_APK

        val subject = when {
            isBundle -> "${app.appName} (${app.displayVersion}) - App Bundle (.apks)"
            uris.size > 1 -> "${app.appName} (${app.displayVersion}) - Split APKs"
            else -> "${app.appName} (${app.displayVersion}) APK"
        }

        val shareText = when {
            isBundle -> "Sharing ${app.appName} (${app.displayVersion}) as a complete installable App Bundle (.apks)."
            uris.size > 1 -> "Sharing ${app.appName} (${app.displayVersion}) as ${uris.size} split APK files."
            else -> "Sharing ${app.appName} (${app.displayVersion}) APK."
        }

        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { clipData = ClipData.newRawUri(app.appName, uris.first()) }
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching {
                    val clip = ClipData.newRawUri(app.appName, uris.first())
                    for (i in 1 until uris.size) {
                        clip.addItem(ClipData.Item(uris[i]))
                    }
                    clipData = clip
                }
            }
        }

        val chooserIntent = runCatching {
            Intent.createChooser(shareIntent, "Share ${app.appName} APK via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull() ?: shareIntent

        ApkShareResult.Success(
            chooserIntent = chooserIntent,
            shareIntent = shareIntent,
            sharedFileCount = copiedFiles.size,
            totalBytes = copiedFiles.sumOf { it.length() },
            targetFiles = copiedFiles,
            uris = uris
        )
    }

    /**
     * Prepares batch export of APK binaries across multiple selected installed applications (Milestone 1.3.27).
     * Filters accessible apps, checks aggregate storage headroom, streams copies with progress,
     * and constructs an ACTION_SEND_MULTIPLE intent with FileProvider content URIs.
     */
    suspend fun prepareBatchShareIntent(
        context: Context?,
        apps: List<InstalledAppInfo>,
        shareMode: ApkShareMode = ApkShareMode.BASE_ONLY,
        customCacheDir: File? = null,
        onProgress: ((copiedBytes: Long, totalBytes: Long, appName: String) -> Unit)? = null
    ): ApkShareResult = withContext(ioDispatcher) {
        val accessibleApps = ArrayList<Pair<InstalledAppInfo, List<File>>>()
        var totalRequiredBytes = 0L

        for (app in apps) {
            val inspection = inspectShareableApks(app)
            if (inspection.isShareable && inspection.baseFile != null) {
                val files = if (shareMode == ApkShareMode.BASE_ONLY || inspection.splitFiles.isEmpty()) {
                    listOf(inspection.baseFile)
                } else {
                    listOf(inspection.baseFile) + inspection.splitFiles
                }
                accessibleApps.add(app to files)
                totalRequiredBytes += files.sumOf { it.length() }
            }
        }

        if (accessibleApps.isEmpty()) {
            return@withContext ApkShareResult.Inaccessible(
                "None of the selected applications contain accessible APK binaries."
            )
        }

        val cacheDir = customCacheDir ?: context?.cacheDir
        if (cacheDir == null) {
            return@withContext ApkShareResult.ExtractionFailed("No cache directory available for APK batch extraction.")
        }

        if (!hasSufficientSpace(cacheDir, totalRequiredBytes)) {
            val available = getAvailableCacheBytes(cacheDir)
            val neededTotal = totalRequiredBytes + safetyBufferBytes
            return@withContext ApkShareResult.InsufficientStorage(
                requiredBytes = neededTotal,
                availableBytes = available,
                formattedRequired = FileFormatter.formatFileSize(neededTotal),
                formattedAvailable = FileFormatter.formatFileSize(available)
            )
        }

        cleanupOldCopies(cacheDir)
        val sharedDir = File(cacheDir, SHARED_APKS_DIR_NAME).apply { if (!exists()) mkdirs() }
        val authority = "${context?.packageName ?: "com.Hrshd1eux.filemanager"}.fileprovider"

        val allCopiedFiles = ArrayList<File>()
        val allUris = ArrayList<Uri>()
        var overallCopiedBytes = 0L
        val buffer = ByteArray(STREAMING_BUFFER_BYTES)

        try {
            for ((app, files) in accessibleApps) {
                val sanitizedApp = sanitizeFileName(app.appName)
                val sanitizedVer = sanitizeFileName(app.versionName ?: "v${app.versionCode ?: 0}")

                for (sourceFile in files) {
                    if (!coroutineContext.isActive) {
                        for (f in allCopiedFiles) f.delete()
                        return@withContext ApkShareResult.Cancelled
                    }

                    val isBase = sourceFile == files.first()
                    val destFileName = if (isBase) {
                        if (files.size > 1 && shareMode == ApkShareMode.ALL_SPLITS) {
                            "${sanitizedApp}_${sanitizedVer}_base.apk"
                        } else {
                            "${sanitizedApp}_${sanitizedVer}.apk"
                        }
                    } else {
                        "${sanitizedApp}_${sanitizedVer}_${sourceFile.name}"
                    }

                    val destFile = File(sharedDir, destFileName)

                    if (destFile.exists() && destFile.length() == sourceFile.length() && destFile.lastModified() >= sourceFile.lastModified()) {
                        overallCopiedBytes += destFile.length()
                        onProgress?.invoke(overallCopiedBytes, totalRequiredBytes, app.appName)
                        allCopiedFiles.add(destFile)
                        val contentUri = uriProvider?.invoke(context, authority, destFile)
                            ?: if (context != null) FileProvider.getUriForFile(context, authority, destFile) else null
                        if (contentUri != null) allUris.add(contentUri)
                        continue
                    }

                    if (destFile.exists()) {
                        destFile.delete()
                    }

                    sourceFile.inputStream().buffered(STREAMING_BUFFER_BYTES).use { input ->
                        destFile.outputStream().buffered(STREAMING_BUFFER_BYTES).use { output ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                if (!coroutineContext.isActive) {
                                    output.close()
                                    destFile.delete()
                                    for (f in allCopiedFiles) f.delete()
                                    return@withContext ApkShareResult.Cancelled
                                }
                                output.write(buffer, 0, read)
                                overallCopiedBytes += read
                                onProgress?.invoke(overallCopiedBytes, totalRequiredBytes, app.appName)
                            }
                        }
                    }

                    allCopiedFiles.add(destFile)
                    val contentUri = uriProvider?.invoke(context, authority, destFile)
                        ?: if (context != null) FileProvider.getUriForFile(context, authority, destFile) else null
                    if (contentUri != null) allUris.add(contentUri)
                }
            }
        } catch (e: Exception) {
            for (f in allCopiedFiles) f.delete()
            return@withContext ApkShareResult.ExtractionFailed("Batch APK preparation failed: ${e.localizedMessage}", e)
        }

        if (allUris.isEmpty()) {
            return@withContext ApkShareResult.ExtractionFailed("No APK files could be prepared for batch sharing.")
        }

        val subject = "Sharing ${accessibleApps.size} Installed Applications (${allUris.size} APK files)"
        val shareText = "Exported APK files for ${accessibleApps.size} applications: " +
                accessibleApps.joinToString(", ") { "${it.first.appName} (${it.first.displayVersion})" }

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = MIME_TYPE_APK
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, allUris)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching {
                val clip = ClipData.newRawUri("Batch APKs", allUris.first())
                for (i in 1 until allUris.size) {
                    clip.addItem(ClipData.Item(allUris[i]))
                }
                clipData = clip
            }
        }

        val chooserIntent = runCatching {
            Intent.createChooser(shareIntent, "Share ${accessibleApps.size} Apps (${allUris.size} APKs) via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull() ?: shareIntent

        ApkShareResult.Success(
            chooserIntent = chooserIntent,
            shareIntent = shareIntent,
            sharedFileCount = allCopiedFiles.size,
            totalBytes = allCopiedFiles.sumOf { it.length() },
            targetFiles = allCopiedFiles,
            uris = allUris
        )
    }
}
