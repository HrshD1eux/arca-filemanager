package com.Hrshd1eux.filemanager.core.storage.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Platform-compliant storage permissions manager.
 * Handles Android 11+ (API 30+) All Files Access, legacy permissions, and SAF persistable URIs.
 */
object StoragePermissionManager {

    /**
     * Checks if the app possesses full filesystem read/write access.
     */
    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            val writeGranted = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            readGranted && writeGranted
        }
    }

    /**
     * Creates an Intent to prompt the user for full files management permission in System Settings.
     */
    fun createManageAllFilesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            } catch (_: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        }
    }

    /**
     * Persists read and write permissions for a user-selected SAF Document Tree URI.
     */
    fun takePersistableUriPermission(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Permission grant failure or already revoked
        }
    }

    /**
     * Returns true if the device runs Android 11+ (API 30+) where All Files Access is supported.
     */
    fun isAllFilesAccessSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Required runtime permissions for Android 10 and below.
     */
    fun getRequiredLegacyPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }
    }

    /**
     * Important Android platform limitation disclaimer.
     * Highlights that all-files access is not a universal pass to other apps' private sandboxes.
     */
    const val ANDROID_STORAGE_LIMITATION_DISCLAIMER: String =
        "Important Android limitation: All-Files Access is powerful, but it is not a universal pass to every file or another app's private storage. Android strictly isolates each app's private sandbox (/data/data/) and restricts access to Android/data. ARCA operates within platform security boundaries and cannot access other apps' private data."

    /**
     * Transparent explanation of why broader file access is useful in ARCA.
     */
    const val STORAGE_EXPLANATION_WHY_BROADER_ACCESS: String =
        "ARCA uses storage access to browse, organize, move, and compress files on your device and SD cards, as well as calculate storage metrics and verify archive integrity. ARCA operates local-first with zero telemetry, zero analytics tracking, and no cloud uploads. Remote networking (SFTP, FTP, SMB, WebDAV) and version checks are strictly user-initiated and isolated from offline core engines."

    /**
     * Explanation of functionality available in limited access mode.
     */
    const val LIMITED_ACCESS_MODE_EXPLANATION: String =
        "ARCA is running with limited storage access. Private Vault (encrypted app sandbox), Network servers (SFTP, FTP, SMB, WebDAV), Settings, and offline tools are fully functional. You can grant storage access at any time to browse device files."

    /**
     * Returns all currently active persisted SAF tree URIs.
     */
    fun getPersistedTreeUris(context: Context): List<Uri> {
        return try {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private const val PREFS_NAME = "arca_storage_permission_prefs"
    private const val KEY_WAS_GRANTED = "was_previously_granted"

    /**
     * Determines the comprehensive storage access state (Milestone 1.5.1).
     */
    fun getStorageAccessState(context: Context): StorageAccessState {
        val hasAll = hasAllFilesAccess(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasGranted = prefs.getBoolean(KEY_WAS_GRANTED, false)

        if (hasAll) {
            if (!wasGranted) {
                prefs.edit().putBoolean(KEY_WAS_GRANTED, true).apply()
            }
            return StorageAccessState.fullAccess()
        }

        // Check if user has scoped tree URIs granted (Milestone 1.5.4)
        val treeUris = getPersistedTreeUris(context)
        if (treeUris.isNotEmpty()) {
            return StorageAccessState.scopedAccess(treeUris.map { it.toString() })
        }

        val isRevoked = wasGranted
        val canRequestDirectly = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

        return StorageAccessState.denied(
            canRequestDirectly = canRequestDirectly,
            wasPreviouslyGranted = wasGranted,
            isRevoked = isRevoked
        )
    }

    /**
     * Creates an Intent to allow the user to select and grant a specific folder tree (Milestone 1.5.4).
     */
    fun createOpenDocumentTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
    }

    /**
     * Releases a previously persisted SAF document tree URI permission (Milestone 1.5.4).
     */
    fun releasePersistableUriPermission(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {}
    }

    /**
     * Returns a detailed diagnostics snapshot for transparent debugging (Milestone 1.5.5).
     */
    fun getDiagnostics(context: Context): StoragePermissionDiagnostics {
        val hasAll = hasAllFilesAccess(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasGranted = prefs.getBoolean(KEY_WAS_GRANTED, false)
        val isRevoked = wasGranted && !hasAll

        val isManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else false

        val legacyRead = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val legacyWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val treeUris = getPersistedTreeUris(context)

        val summary = buildString {
            append("Android API: ${Build.VERSION.SDK_INT}. ")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append("All Files Access (isExternalStorageManager): $isManager. ")
            } else {
                append("Legacy Permissions: Read=$legacyRead, Write=$legacyWrite. ")
            }
            append("Persisted SAF Trees: ${treeUris.size}. ")
            if (isRevoked) {
                append("Warning: Permission was previously granted and later revoked by system/user.")
            }
        }

        return StoragePermissionDiagnostics(
            apiLevel = Build.VERSION.SDK_INT,
            isAllFilesSupported = isAllFilesAccessSupported(),
            isExternalStorageManager = isManager,
            hasLegacyReadPermission = legacyRead,
            hasLegacyWritePermission = legacyWrite,
            persistedUriCount = treeUris.size,
            persistedUris = treeUris.map { it.toString() },
            wasEverGranted = wasGranted,
            isRevoked = isRevoked,
            diagnosticSummary = summary
        )
    }
}
