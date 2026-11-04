package com.Hrshd1eux.filemanager.core.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for ARCA.
 */
sealed interface Screen {
    @Serializable
    data object Home : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object About : Screen

    @Serializable
    data object PrivacyStatement : Screen

    @Serializable
    data class Browser(
        val initialPath: String? = null,
        val title: String = "Internal Storage"
    ) : Screen

    @Serializable
    data class Category(
        val categoryName: String,
        val rootPath: String? = null
    ) : Screen

    @Serializable
    data class Search(
        val rootPath: String? = null,
        val categoryName: String? = null
    ) : Screen

    @Serializable
    data class LargeFiles(
        val rootPath: String? = null
    ) : Screen

    @Serializable
    data class DuplicateFinder(
        val rootPath: String? = null
    ) : Screen

    @Serializable
    data object StorageAnalyzer : Screen

    @Serializable
    data object InstalledApps : Screen

    @Serializable
    data class ArchiveViewer(
        val archivePath: String,
        val title: String = ""
    ) : Screen

    @Serializable
    data class Preview(
        val initialPath: String,
        val categoryName: String? = null,
        val directoryPath: String? = null
    ) : Screen

    @Serializable
    data class ImageViewer(
        val initialPath: String,
        val categoryName: String? = null,
        val directoryPath: String? = null
    ) : Screen

    @Serializable
    data class VideoViewer(
        val initialPath: String,
        val categoryName: String? = null,
        val directoryPath: String? = null
    ) : Screen

    @Serializable
    data object Vault : Screen

    @Serializable
    data object SecurityCenter : Screen

    @Serializable
    data object NetworkDiagnostics : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object Trash : Screen

    @Serializable
    data object FtpServer : Screen

    @Serializable
    data object Favorites : Screen

    @Serializable
    data object Recent : Screen

    @Serializable
    data object Network : Screen

    @Serializable
    data object AboutDeveloper : Screen

    @Serializable
    data class NetworkBrowser(
        val connectionId: String,
        val initialPath: String? = null,
        val title: String = "Remote Server"
    ) : Screen
}
