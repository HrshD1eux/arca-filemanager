package com.Hrshd1eux.filemanager.feature.browser

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.model.StorageStats
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem

/**
 * UI State machine for the Directory Browser.
 */
sealed interface BrowserUiState {
    data object Loading : BrowserUiState

    data class PermissionRequired(
        val reason: String = "Storage access is required to browse files."
    ) : BrowserUiState

    data class Error(
        val message: String
    ) : BrowserUiState

    data class Success(
        val currentPath: String,
        val currentTitle: String,
        val breadcrumbs: List<BreadcrumbItem>,
        val items: List<StorageNode>,
        val stats: StorageStats? = null
    ) : BrowserUiState
}
