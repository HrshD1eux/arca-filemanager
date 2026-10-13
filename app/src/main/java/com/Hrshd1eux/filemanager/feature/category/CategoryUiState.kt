package com.Hrshd1eux.filemanager.feature.category

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

sealed interface CategoryUiState {
    data object Loading : CategoryUiState
    data class Success(
        val items: List<StorageNode>,
        val totalSizeBytes: Long,
        val fileCount: Int
    ) : CategoryUiState
    data object Empty : CategoryUiState
    data class Error(val message: String) : CategoryUiState
}
