package com.Hrshd1eux.filemanager.core.update.model

/**
 * Metadata for a release fetched from GitHub Releases.
 */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkUrl: String,
    val apkFileName: String,
    val apkSize: Long,
    val isUpdateAvailable: Boolean
)

/**
 * Live download progress emitted by DownloadManager.
 */
data class DownloadProgress(
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isSuccess: Boolean = false,
    val isFailed: Boolean = false
) {
    val progressRatio: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressRatio * 100).toInt()
}
