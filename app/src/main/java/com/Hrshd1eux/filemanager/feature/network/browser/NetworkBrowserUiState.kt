package com.Hrshd1eux.filemanager.feature.network.browser

import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem

/**
 * UI State for the Remote Network File Browser.
 * Mirrors BrowserUiState to maintain identical UX between local and remote browsing.
 */
sealed interface NetworkBrowserUiState {
    data class Loading(val message: String = "Connecting to remote server...") : NetworkBrowserUiState

    data class Error(
        val serverName: String? = null,
        val message: String = "Your local files are still available.",
        val detail: String? = null,
        val canRetry: Boolean = true
    ) : NetworkBrowserUiState

    data class Success(
        val currentPath: String,
        val currentTitle: String,
        val breadcrumbs: List<BreadcrumbItem>,
        val items: List<StorageNode>,
        val totalItemCount: Int,
        val serverConfig: NetworkServerConfig,
        val isEncryptedTransport: Boolean,
        val searchQuery: String = ""
    ) : NetworkBrowserUiState
}
