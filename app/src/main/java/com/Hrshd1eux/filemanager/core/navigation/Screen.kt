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
}
