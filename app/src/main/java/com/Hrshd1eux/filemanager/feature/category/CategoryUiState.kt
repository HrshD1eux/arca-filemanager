package com.Hrshd1eux.filemanager.feature.category

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

sealed interface CategoryUiState {
    data object Loading : CategoryUiState
    data class Success(
        val items: List<StorageNode>,
        val totalSizeBytes: Long,
        val fileCount: Int,
        val dateGroups: List<CategoryDateGroup> = emptyList()
    ) : CategoryUiState
    data class Empty(
        val message: String = "No matching files found",
        val isSearchFilterEmpty: Boolean = false
    ) : CategoryUiState
    data class Error(val message: String) : CategoryUiState
}
