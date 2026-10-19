package com.Hrshd1eux.filemanager.core.storage.model

import android.graphics.Bitmap
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter

/**
 * Metadata about an individual split APK component (e.g. config.arm64_v8a, config.en, feature_split).
 */
data class SplitApkInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long?,
    val isAccessible: Boolean = sizeBytes != null
) {
    val formattedSize: String
        get() = sizeBytes?.let { FileFormatter.formatFileSize(it) } ?: "Unavailable"
}

/**
 * Other accessible package details exposed by Android's package manager and platform architecture.
 */
data class PackageExtraInfo(
    val splitApks: List<SplitApkInfo> = emptyList(),
    val nativeLibraryDir: String? = null,
    val primaryCpuAbi: String? = null,
    val isExternalStorage: Boolean = false,
    val targetSdkVersion: Int = 0,
    val minSdkVersion: Int? = null,
    val requestedPermissionsCount: Int = 0,
    val processName: String? = null,
    val storageUuid: String? = null,
    val isHardwareAccelerated: Boolean = false,
    val isDebuggable: Boolean = false,
    val isBackupAllowed: Boolean = false,
    val isLargeHeap: Boolean = false,
    val isCleartextTrafficPermitted: Boolean = false
) {
    val totalSplitBytes: Long
        get() = splitApks.mapNotNull { it.sizeBytes }.sum()

    val formattedSplitBytes: String
        get() = if (splitApks.isNotEmpty()) FileFormatter.formatFileSize(totalSplitBytes) else "None"
}

/**
 * Encapsulates metadata, classification, and storage measurements of an installed application.
 */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long?,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean = false,
    val installTimeMillis: Long = 0L,
    val lastUpdateTimeMillis: Long = 0L,
    val sourceDir: String? = null,
    val icon: Bitmap? = null,
    val storageMeasurement: AppStorageMeasurement,
    val extraInfo: PackageExtraInfo = PackageExtraInfo()
) {
    /**
     * An app is considered a User App if it is not a pre-installed system app,
     * or if it is an updated system app residing in user storage.
     */
    val isUserApp: Boolean
        get() = !isSystemApp || isUpdatedSystemApp

    val displayVersion: String
        get() = when {
            !versionName.isNullOrBlank() && versionCode != null && versionCode > 0 -> "v$versionName ($versionCode)"
            !versionName.isNullOrBlank() -> "v$versionName"
            versionCode != null && versionCode > 0 -> "Build $versionCode"
            else -> "Version Unavailable"
        }

    val appTypeLabel: String
        get() = when {
            isUpdatedSystemApp -> "Updated System App"
            isSystemApp -> "System App"
            else -> "User App"
        }
}
