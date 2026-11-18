package com.Hrshd1eux.filemanager.core.storage.apk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/**
 * Installation status of an APK relative to what is currently installed on the device.
 */
enum class ApkInstallStatus {
    NEW_APP,
    UPDATE,
    SAME_VERSION,
    DOWNGRADE
}

/**
 * Rich package metadata inspected directly from an .apk file.
 */
data class ApkPackageDetails(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
    val fileSize: Long,
    val filePath: String,
    val icon: Bitmap?,
    val installStatus: ApkInstallStatus,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null
)

object ApkInstaller {

    const val MIME_TYPE_APK = "application/vnd.android.package-archive"

    /**
     * Checks if ARCA possesses the permission to request package installations.
     * On Android 8.0 (API 26) and later, this requires user-granted "Install unknown apps" permission.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Creates an Intent to prompt the user to enable "Install unknown apps" for ARCA.
     */
    fun createManageUnknownAppSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Creates an Intent to invoke the Android system PackageInstaller via FileProvider.
     */
    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, MIME_TYPE_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Invokes the system PackageInstaller to install the given APK file.
     */
    fun install(context: Context, apkFile: File): Result<Unit> = runCatching {
        if (!apkFile.exists() || !apkFile.isFile) {
            throw IllegalArgumentException("APK file does not exist: ${apkFile.absolutePath}")
        }
        val intent = createInstallIntent(context, apkFile)
        context.startActivity(intent)
    }

    /**
     * Installs one or more APK files (such as a base APK and split configuration APKs)
     * using Android's modern [PackageInstaller.Session] API.
     *
     * On standard unrooted devices, Android OS enqueues the session and presents the system
     * confirmation prompt via [ApkInstallReceiver].
     * On Android 12+ (API 31+), requests unattended update if ARCA is the installer of record.
     *
     * @param context Application or activity context
     * @param apkFiles List of APK files to be committed as part of this atomic session
     * @param packageName Optional package name of the app being installed/updated
     * @return Result containing the created sessionId
     */
    fun installViaSession(
        context: Context,
        apkFiles: List<File>,
        packageName: String? = null
    ): Result<Int> = runCatching {
        if (apkFiles.isEmpty()) {
            throw IllegalArgumentException("At least one APK file must be provided")
        }
        for (apk in apkFiles) {
            if (!apk.exists() || !apk.isFile) {
                throw IllegalArgumentException("APK file does not exist: ${apk.absolutePath}")
            }
        }
        if (!canInstallPackages(context)) {
            throw SecurityException("Install unknown apps permission not granted")
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (packageName != null) {
            params.setAppPackageName(packageName)
        }

        // Android 12+ (API 31+): enable unattended installation if ARCA is the installer of record
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            params.setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST)
        }

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            for (file in apkFiles) {
                val streamName = file.name
                val sizeBytes = file.length()
                session.openWrite(streamName, 0, sizeBytes).use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    session.fsync(out)
                }
            }

            val receiverIntent = Intent(context, ApkInstallReceiver::class.java).apply {
                action = ApkInstallReceiver.ACTION_INSTALL_COMMIT
                putExtra(ApkInstallReceiver.EXTRA_SESSION_ID, sessionId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, receiverIntent, flags)
            session.commit(pendingIntent.intentSender)
            sessionId
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    /**
     * Checks if a file is a bundled Split APK container (.xapk, .apks, or .apkm).
     */
    fun isSplitApkArchive(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".xapk") || name.endsWith(".apks") || name.endsWith(".apkm")
    }

    /**
     * Extracts all .apk files from a Split APK archive (.xapk, .apks, .apkm) into [outputDir],
     * validating against zip-slip directory traversal vulnerabilities.
     */
    fun extractSplitApk(archiveFile: File, outputDir: File): List<File> {
        if (!archiveFile.exists() || !archiveFile.isFile) return emptyList()
        if (!outputDir.exists()) outputDir.mkdirs()

        val canonicalDest = outputDir.canonicalPath
        val extractedApks = mutableListOf<File>()

        java.util.zip.ZipInputStream(archiveFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    val targetFile = File(outputDir, File(entry.name).name)
                    if (targetFile.canonicalPath.startsWith(canonicalDest)) {
                        targetFile.outputStream().buffered().use { out ->
                            zis.copyTo(out)
                        }
                        extractedApks.add(targetFile)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extractedApks
    }

    /**
     * Checks if a `su` binary is present on the device filesystem.
     */
    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }

    /**
     * Attempts to install packages silently via root shell (`su`).
     *
     * Only works on rooted devices where su permissions are granted.
     */
    fun installSilentlyViaRoot(apkFiles: List<File>): Result<Unit> = runCatching {
        if (!isRootAvailable()) {
            throw IllegalStateException("Root access is not available on this device")
        }
        if (apkFiles.isEmpty()) {
            throw IllegalArgumentException("At least one APK file must be provided")
        }

        val command = if (apkFiles.size == 1) {
            "pm install -r \"${apkFiles[0].absolutePath}\""
        } else {
            val filePaths = apkFiles.joinToString(" ") { "\"${it.absolutePath}\"" }
            "pm install-create && pm install-write 0 base \"${apkFiles[0].absolutePath}\" && pm install-commit 0 || pm install -r $filePaths"
        }

        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()
        val output = process.inputStream.bufferedReader().use { it.readText() }

        if (exitCode != 0 || !output.contains("Success", ignoreCase = true)) {
            throw RuntimeException("Root installation failed (code $exitCode): $output")
        }
    }

    /**
     * Checks if the Shizuku manager app is installed on the device.
     */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Parses package details and compares against existing installed packages on the device.
     */
    fun parseApk(context: Context, apkFile: File): ApkPackageDetails? {
        if (!apkFile.exists() || !apkFile.isFile) return null

        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_PERMISSIONS
            } else {
                0
            }
            val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return null

            val appInfo = pkgInfo.applicationInfo ?: return null
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath

            val appName = try {
                appInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkgInfo.packageName
            } catch (_: Exception) {
                pkgInfo.packageName
            }

            val icon = try {
                val drawable = appInfo.loadIcon(pm)
                drawableToBitmap(drawable)
            } catch (_: Exception) {
                null
            }

            val versionName = pkgInfo.versionName ?: "Unknown"
            val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo.minSdkVersion
            } else null
            val targetSdk = appInfo.targetSdkVersion

            // Check if already installed
            val (status, instName, instCode) = checkInstallStatus(pm, pkgInfo.packageName, versionCode)

            ApkPackageDetails(
                appName = appName,
                packageName = pkgInfo.packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdkVersion = minSdk,
                targetSdkVersion = targetSdk,
                fileSize = apkFile.length(),
                filePath = apkFile.absolutePath,
                icon = icon,
                installStatus = status,
                installedVersionName = instName,
                installedVersionCode = instCode
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Determines whether an APK is a new installation, an update, the same version, or a downgrade.
     */
    fun determineStatus(apkVersionCode: Long, installedVersionCode: Long?): ApkInstallStatus {
        if (installedVersionCode == null) return ApkInstallStatus.NEW_APP
        return when {
            apkVersionCode > installedVersionCode -> ApkInstallStatus.UPDATE
            apkVersionCode < installedVersionCode -> ApkInstallStatus.DOWNGRADE
            else -> ApkInstallStatus.SAME_VERSION
        }
    }

    fun checkInstallStatus(
        pm: PackageManager,
        packageName: String,
        apkVersionCode: Long
    ): Triple<ApkInstallStatus, String?, Long?> {
        return try {
            val installed = pm.getPackageInfo(packageName, 0)
            val instCode = PackageInfoCompat.getLongVersionCode(installed)
            val instName = installed.versionName
            val status = determineStatus(apkVersionCode, instCode)
            Triple(status, instName, instCode)
        } catch (_: PackageManager.NameNotFoundException) {
            Triple(ApkInstallStatus.NEW_APP, null, null)
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(192)
        val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
