package com.Hrshd1eux.filemanager.core.storage.apk

import android.content.Context
import android.content.Intent
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
