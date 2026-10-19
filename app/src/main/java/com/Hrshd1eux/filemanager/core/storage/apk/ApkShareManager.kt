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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Target export mode when sharing an installed application's APKs.
 */
enum class ApkShareMode {
    /**
     * Share only the base APK (standard for standalone APKs, or standalone export of App Bundles).
     */
    BASE_ONLY,

    /**
     * Share all APK components: base APK plus all device-specific split APKs.
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
}

/**
 * Manager responsible for securely extracting, preparing, and dispatching installed application
 * APK files through Android's standard sharing interface.
 *
 * Adheres strictly to security and platform constraints:
 * 1. Checks accessibility of installed package APK binaries.
 * 2. Detects split APKs (Android App Bundles) and explains limitations.
 * 3. Verifies available cache storage before copying files to prevent storage exhaustion.
 * 4. Cleans up temporary copies to avoid storage leakage.
 * 5. Uses secure FileProvider content URIs with FLAG_GRANT_READ_URI_PERMISSION.
 * 6. Never exposes raw filesystem or file:// paths through sharing intents.
 * 7. Supports standard sharing targets like WhatsApp, Quick Share, and Drive.
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
     * sharing Intent configured with secure FileProvider content URIs and temporary read permissions.
     */
    suspend fun prepareAndCreateShareIntent(
        context: Context?,
        app: InstalledAppInfo,
        shareMode: ApkShareMode = ApkShareMode.ALL_SPLITS,
        customCacheDir: File? = null
    ): ApkShareResult = withContext(ioDispatcher) {
        // 1. Inspect accessibility of APK binaries
        val inspection = inspectShareableApks(app)
        if (!inspection.isShareable || inspection.baseFile == null) {
            return@withContext ApkShareResult.Inaccessible(
                inspection.inaccessibilityReason ?: "The application package files are inaccessible."
            )
        }

        // 2. Determine files to extract based on requested shareMode
        val filesToCopy = if (shareMode == ApkShareMode.BASE_ONLY || inspection.splitFiles.isEmpty()) {
            listOf(inspection.baseFile)
        } else {
            listOf(inspection.baseFile) + inspection.splitFiles
        }

        val requiredBytes = filesToCopy.sumOf { it.length() }

        // 3. Check available cache storage before copying
        val cacheDir = customCacheDir ?: context?.cacheDir
        if (cacheDir == null) {
            return@withContext ApkShareResult.ExtractionFailed("No cache directory available for temporary APK extraction.")
        }

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

        // 4. Prune stale copies before creating new ones
        cleanupOldCopies(cacheDir)

        val sharedDir = File(cacheDir, SHARED_APKS_DIR_NAME)
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }

        // 5. Copy files with clean, descriptive naming
        val sanitizedApp = sanitizeFileName(app.appName)
        val sanitizedVer = sanitizeFileName(app.versionName ?: "v${app.versionCode ?: 0}")
        val copiedFiles = ArrayList<File>()
        val uris = ArrayList<Uri>()
        val authority = "${context?.packageName ?: "com.Hrshd1eux.filemanager"}.fileprovider"

        try {
            for (sourceFile in filesToCopy) {
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
                // If destFile exists, replace it
                if (destFile.exists()) {
                    destFile.delete()
                }

                sourceFile.inputStream().buffered().use { input ->
                    destFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }

                copiedFiles.add(destFile)

                // 6. Generate secure content URI using FileProvider
                val contentUri = uriProvider?.invoke(context, authority, destFile)
                    ?: if (context != null) FileProvider.getUriForFile(context, authority, destFile) else null
                if (contentUri != null) {
                    uris.add(contentUri)
                }
            }
        } catch (e: Exception) {
            // Clean up any partially copied files
            for (f in copiedFiles) {
                f.delete()
            }
            return@withContext ApkShareResult.ExtractionFailed(
                message = "Failed to copy APK binaries for sharing: ${e.localizedMessage ?: "I/O error"}",
                cause = e
            )
        }

        if (uris.isEmpty()) {
            return@withContext ApkShareResult.ExtractionFailed("No APK files could be prepared for sharing.")
        }

        // 7. Build standard Android sharing intent
        val subject = if (uris.size > 1) {
            "${app.appName} (${app.displayVersion}) - Split APKs"
        } else {
            "${app.appName} (${app.displayVersion}) APK"
        }

        val shareText = if (uris.size > 1) {
            "Sharing ${app.appName} (${app.displayVersion}) as ${uris.size} split APK files."
        } else {
            "Sharing ${app.appName} (${app.displayVersion}) APK."
        }

        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_TYPE_APK
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { clipData = ClipData.newRawUri(app.appName, uris.first()) }
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = MIME_TYPE_APK
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
}
