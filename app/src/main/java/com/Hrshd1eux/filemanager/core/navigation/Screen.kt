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
    data class ArchiveViewer(
        val archivePath: String,
        val title: String = ""
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
    data class NetworkBrowser(
        val connectionId: String,
        val initialPath: String? = null,
        val title: String = "Remote Server"
    ) : Screen
}
