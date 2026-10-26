package com.Hrshd1eux.filemanager.core.ftp.server

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Configuration for hosting ARCA's local embedded FTP Server.
 */
data class FtpServerConfig(
    val port: Int = DEFAULT_PORT,
    val username: String = DEFAULT_USERNAME,
    val password: String = DEFAULT_PASSWORD,
    val isAnonymousAllowed: Boolean = false,
    val rootDirectoryPath: String = defaultRootPath,
    val isReadOnly: Boolean = false,
    val shareApkEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_PORT = 2121
        const val DEFAULT_USERNAME = "arca"
        const val DEFAULT_PASSWORD = "1234"

        val defaultRootPath: String
            get() = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    }

    val rootFile: File
        get() = File(rootDirectoryPath)

    val rootDisplayName: String
        get() {
            val defaultPath = defaultRootPath
            return if (rootDirectoryPath == defaultPath || rootDirectoryPath == "/storage/emulated/0") {
                "Internal Storage"
            } else {
                rootFile.name.ifBlank { rootDirectoryPath }
            }
        }
}

/**
 * Persistence manager for saving and loading FtpServerConfig.
 */
object FtpServerPreferences {
    private const val PREFS_NAME = "arca_ftp_server_prefs"
    private const val KEY_PORT = "ftp_port"
    private const val KEY_USERNAME = "ftp_username"
    private const val KEY_PASSWORD = "ftp_password"
    private const val KEY_ANONYMOUS = "ftp_anonymous"
    private const val KEY_ROOT_PATH = "ftp_root_path"
    private const val KEY_READ_ONLY = "ftp_read_only"
    private const val KEY_SHARE_APK = "ftp_share_apk"

    fun loadConfig(context: Context): FtpServerConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return FtpServerConfig(
            port = prefs.getInt(KEY_PORT, FtpServerConfig.DEFAULT_PORT),
            username = prefs.getString(KEY_USERNAME, FtpServerConfig.DEFAULT_USERNAME) ?: FtpServerConfig.DEFAULT_USERNAME,
            password = prefs.getString(KEY_PASSWORD, FtpServerConfig.DEFAULT_PASSWORD) ?: FtpServerConfig.DEFAULT_PASSWORD,
            isAnonymousAllowed = prefs.getBoolean(KEY_ANONYMOUS, false),
            rootDirectoryPath = prefs.getString(KEY_ROOT_PATH, FtpServerConfig.defaultRootPath) ?: FtpServerConfig.defaultRootPath,
            isReadOnly = prefs.getBoolean(KEY_READ_ONLY, false),
            shareApkEnabled = prefs.getBoolean(KEY_SHARE_APK, true)
        )
    }

    fun saveConfig(context: Context, config: FtpServerConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_PORT, config.port)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putBoolean(KEY_ANONYMOUS, config.isAnonymousAllowed)
            .putString(KEY_ROOT_PATH, config.rootDirectoryPath)
            .putBoolean(KEY_READ_ONLY, config.isReadOnly)
            .putBoolean(KEY_SHARE_APK, config.shareApkEnabled)
            .apply()
    }
}
