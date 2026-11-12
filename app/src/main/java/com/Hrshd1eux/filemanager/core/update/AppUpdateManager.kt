package com.Hrshd1eux.filemanager.core.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import com.Hrshd1eux.filemanager.core.storage.apk.ApkInstaller
import com.Hrshd1eux.filemanager.core.update.model.DownloadProgress
import com.Hrshd1eux.filemanager.core.update.model.ReleaseInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages GitHub release checks, background resumable downloads via Android DownloadManager,
 * and seamless package installation.
 */
object AppUpdateManager {

    private const val GITHUB_REPO = "HrshD1eux/arca-filemanager"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val ALL_RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases"

    /**
     * Queries GitHub Releases API for the latest published release.
     * Checks /releases/latest first, and falls back to /releases if needed.
     * Returns null if no release exists or repository is empty.
     */
    suspend fun checkForUpdates(currentVersion: String): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val json = fetchLatestReleaseJson(currentVersion) ?: return@runCatching null

            val tagName = json.optString("tag_name", "")
            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val cleanCurrentVersion = currentVersion.removePrefix("v").removePrefix("V").trim()

            // Find APK asset
            var apkUrl = ""
            var apkFileName = "ARCA-$cleanRemoteVersion.apk"
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkFileName = name
                        apkUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val isNewer = isNewerVersion(cleanCurrentVersion, cleanRemoteVersion)

            ReleaseInfo(
                tagName = tagName,
                versionName = cleanRemoteVersion,
                releaseName = releaseName,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
                apkUrl = apkUrl,
                apkFileName = apkFileName,
                apkSize = apkSize,
                isUpdateAvailable = isNewer
            )
        }
    }

    private fun fetchLatestReleaseJson(currentVersion: String): JSONObject? {
        // Try /releases/latest first
        try {
            val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "ARCA-FileManager/$currentVersion")
                connectTimeout = 12000
                readTimeout = 12000
            }
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = InputStreamReader(conn.inputStream).readText()
                    return JSONObject(text)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: java.io.IOException) {
            if (e !is java.io.FileNotFoundException) throw e
        }

        // Fallback to /releases (which includes pre-releases)
        val connList = (URL(ALL_RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "ARCA-FileManager/$currentVersion")
            connectTimeout = 12000
            readTimeout = 12000
        }
        try {
            if (connList.responseCode == HttpURLConnection.HTTP_OK) {
                val text = InputStreamReader(connList.inputStream).readText()
                val array = org.json.JSONArray(text)
                if (array.length() > 0) {
                    return array.getJSONObject(0)
                }
            }
        } finally {
            connList.disconnect()
        }

        return null
    }

    /**
     * Compares semver version strings (e.g., "1.6.13" vs "1.6.12", "v1.6.19" vs "1.6.19").
     * Returns true if remoteVersion is strictly newer than currentVersion.
     */
    fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
        if (currentVersion.isBlank() || remoteVersion.isBlank()) return false
        val cleanCurrent = currentVersion.removePrefix("v").removePrefix("V").trim()
        val cleanRemote = remoteVersion.removePrefix("v").removePrefix("V").trim()
        if (cleanCurrent.equals(cleanRemote, ignoreCase = true)) return false

        val currentParts = cleanCurrent.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = cleanRemote.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }

        if (currentParts.isEmpty() || remoteParts.isEmpty()) return false

        val maxLength = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLength) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        return false
    }

    /**
     * Returns true when [release] is strictly newer than [currentVersion] and has not already
     * been acknowledged/prompted for the same normalized version.
     */
    fun shouldPromptForUpdate(
        currentVersion: String,
        release: ReleaseInfo?,
        lastPromptedVersion: String?
    ): Boolean {
        if (release == null || !release.isUpdateAvailable) return false
        if (!isNewerVersion(currentVersion, release.versionName)) return false
        val normalizedRemote = release.versionName.removePrefix("v").removePrefix("V").trim()
        val normalizedPrompted = lastPromptedVersion?.removePrefix("v")?.removePrefix("V")?.trim()
        return normalizedRemote.isNotEmpty() && normalizedRemote != normalizedPrompted
    }

    /**
     * Starts a background resumable download via Android DownloadManager.
     * Returns the downloadId.
     */
    fun startDownload(context: Context, releaseInfo: ReleaseInfo): Long {
        require(releaseInfo.apkUrl.isNotBlank()) { "Release does not contain a valid APK download URL." }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(releaseInfo.apkUrl)).apply {
            setTitle("Downloading ARCA Update")
            setDescription("Version ${releaseInfo.versionName}")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, releaseInfo.apkFileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        return downloadManager.enqueue(request)
    }

    /**
     * Streams the release APK directly via HTTPS (following GitHub CDN redirects) for immediate
     * in-dialog "Update Now" downloads with reliable progress and cancellation support.
     */
    fun downloadApkDirect(context: Context, releaseInfo: ReleaseInfo): Flow<DownloadProgress> = flow {
        require(releaseInfo.apkUrl.isNotBlank()) { "Release does not contain a valid APK download URL." }

        val targetDir = getUpdateDownloadsDir(context)
        val finalFile = File(targetDir, releaseInfo.apkFileName)
        val tempFile = File(targetDir, "${releaseInfo.apkFileName}.part")
        if (tempFile.exists()) tempFile.delete()
        if (finalFile.exists()) finalFile.delete()

        emit(
            DownloadProgress(
                status = DownloadManager.STATUS_RUNNING,
                bytesDownloaded = 0L,
                totalBytes = releaseInfo.apkSize
            )
        )

        var conn: HttpURLConnection? = null
        try {
            conn = openWithRedirects(releaseInfo.apkUrl, releaseInfo.versionName)
            val contentLength = conn.contentLengthLong.takeIf { it > 0L } ?: releaseInfo.apkSize
            val buffer = ByteArray(32 * 1024)
            var downloaded = 0L
            var lastEmitTimeMs = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val nowMs = SystemClock.elapsedRealtime()
                        if (nowMs - lastEmitTimeMs >= 120L) {
                            lastEmitTimeMs = nowMs
                            emit(
                                DownloadProgress(
                                    status = DownloadManager.STATUS_RUNNING,
                                    bytesDownloaded = downloaded,
                                    totalBytes = maxOf(contentLength, downloaded)
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded <= 0L) {
                tempFile.delete()
                emit(
                    DownloadProgress(
                        status = DownloadManager.STATUS_FAILED,
                        bytesDownloaded = 0L,
                        totalBytes = contentLength,
                        isFailed = true,
                        errorMessage = "Downloaded file was empty."
                    )
                )
                return@flow
            }

            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            emit(
                DownloadProgress(
                    status = DownloadManager.STATUS_SUCCESSFUL,
                    bytesDownloaded = downloaded,
                    totalBytes = downloaded,
                    isSuccess = true
                )
            )
        } catch (ce: CancellationException) {
            tempFile.delete()
            throw ce
        } catch (e: Exception) {
            tempFile.delete()
            emit(
                DownloadProgress(
                    status = DownloadManager.STATUS_FAILED,
                    bytesDownloaded = 0L,
                    totalBytes = releaseInfo.apkSize,
                    isFailed = true,
                    errorMessage = e.localizedMessage ?: "Download failed. Please check your connection."
                )
            )
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun openWithRedirects(initialUrl: String, versionTag: String): HttpURLConnection {
        var currentUrl = initialUrl
        for (hop in 0 until 6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "ARCA-FileManager/$versionTag")
                connectTimeout = 15000
                readTimeout = 30000
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw java.io.IOException("Redirect missing Location header (HTTP $code)")
                }
                val resolved = URL(URL(currentUrl), location)
                if (!resolved.protocol.equals("https", ignoreCase = true)) {
                    throw java.io.IOException("Refusing non-HTTPS redirect to ${resolved.protocol}")
                }
                currentUrl = resolved.toString()
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw java.io.IOException("HTTP $code while downloading update")
            }
            return conn
        }
        throw java.io.IOException("Too many redirects while downloading update")
    }

    /**
     * Observes live progress of the DownloadManager task until completion or failure.
     */
    fun observeDownloadProgress(context: Context, downloadId: Long): Flow<DownloadProgress> = flow {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var isRunning = true
        var missingRowRetries = 0

        while (isRunning) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                missingRowRetries = 0
                try {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
                    val bytesDownloaded = if (bytesDownloadedIdx >= 0) cursor.getLong(bytesDownloadedIdx) else 0L
                    val totalBytes = if (totalBytesIdx >= 0) cursor.getLong(totalBytesIdx) else 0L

                    val isSuccess = status == DownloadManager.STATUS_SUCCESSFUL
                    val isFailed = status == DownloadManager.STATUS_FAILED

                    emit(
                        DownloadProgress(
                            status = status,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            isSuccess = isSuccess,
                            isFailed = isFailed
                        )
                    )

                    if (isSuccess || isFailed) {
                        isRunning = false
                    }
                } finally {
                    cursor.close()
                }
            } else {
                cursor?.close()
                missingRowRetries++
                if (missingRowRetries >= 5) {
                    emit(
                        DownloadProgress(
                            status = DownloadManager.STATUS_FAILED,
                            bytesDownloaded = 0L,
                            totalBytes = 0L,
                            isFailed = true,
                            errorMessage = "System DownloadManager did not start the download."
                        )
                    )
                    isRunning = false
                }
            }

            if (isRunning) {
                delay(500)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun getUpdateDownloadsDir(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.cacheDir, "updates").apply { mkdirs() }
    }

    /**
     * Returns the downloaded APK file in the app update downloads dir if it exists.
     */
    fun getDownloadedApkFile(context: Context, releaseInfo: ReleaseInfo): File? {
        val downloadsDir = getUpdateDownloadsDir(context)
        val apkFile = File(downloadsDir, releaseInfo.apkFileName)
        return if (apkFile.exists() && apkFile.isFile && apkFile.length() > 0) apkFile else null
    }

    /**
     * Deletes any previously downloaded APK file for this release if present.
     */
    fun deleteDownloadedApkFile(context: Context, releaseInfo: ReleaseInfo): Boolean {
        val downloadsDir = getUpdateDownloadsDir(context)
        val apkFile = File(downloadsDir, releaseInfo.apkFileName)
        val tempFile = File(downloadsDir, "${releaseInfo.apkFileName}.part")
        if (tempFile.exists()) tempFile.delete()
        return if (apkFile.exists()) apkFile.delete() else false
    }

    /**
     * Prompts the system PackageInstaller to install the downloaded APK.
     */
    fun installUpdate(context: Context, apkFile: File): Result<Unit> {
        return ApkInstaller.install(context, apkFile)
    }
}
