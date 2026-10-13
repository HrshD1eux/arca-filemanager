package com.Hrshd1eux.filemanager.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val repository: CategoryRepository = CategoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoryUiState>(CategoryUiState.Loading)
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    private val _category = MutableStateFlow(CategoryFilter.ALL)
    val category: StateFlow<CategoryFilter> = _category.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        SortOrder(field = SortField.DATE, direction = SortDirection.DESCENDING, foldersFirst = false)
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private var rawItems: List<StorageNode> = emptyList()
    private var currentRootPath: String? = null
    private var isInitialized = false

    fun loadCategory(categoryName: String, rootPath: String? = null) {
        val cat = CategoryFilter.entries.firstOrNull {
            it.name.equals(categoryName, ignoreCase = true) || it.displayName.equals(categoryName, ignoreCase = true)
        } ?: CategoryFilter.ALL

        if (isInitialized && _category.value == cat && currentRootPath == rootPath) {
            // Already initialized for this category, preserve state
            return
        }

        isInitialized = true
        _category.value = cat
        currentRootPath = rootPath

        // Default ViewMode: Grid for Images and Videos, List for documents/archives/etc.
        if (cat == CategoryFilter.IMAGES || cat == CategoryFilter.VIDEOS) {
            _viewMode.value = ViewMode.GRID
        } else {
            _viewMode.value = ViewMode.LIST
        }

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = CategoryUiState.Loading
            try {
                val files = repository.getFilesForCategory(_category.value, currentRootPath)
                rawItems = files
                if (files.isEmpty()) {
                    _uiState.value = CategoryUiState.Empty
                } else {
                    applySortingAndEmit()
                }
            } catch (e: Exception) {
                _uiState.value = CategoryUiState.Error(e.localizedMessage ?: "Failed to scan files")
            }
        }
    }

    fun updateSortOrder(newOrder: SortOrder) {
        _sortOrder.value = newOrder
        if (rawItems.isNotEmpty()) {
            applySortingAndEmit()
        }
    }

    fun toggleViewMode() {
        _viewMode.value = when (_viewMode.value) {
            ViewMode.LIST -> ViewMode.GRID
            ViewMode.GRID -> ViewMode.LIST
            ViewMode.COMPACT -> ViewMode.LIST
        }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    private fun applySortingAndEmit() {
        val sorted = repository.sortItems(rawItems, _sortOrder.value)
        val totalSize = sorted.sumOf { it.size }
        _uiState.value = CategoryUiState.Success(
            items = sorted,
            totalSizeBytes = totalSize,
            fileCount = sorted.size
        )
    }

    fun deleteFile(node: StorageNode, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.deleteFile(node)
            if (res.isSuccess) {
                rawItems = rawItems.filter { it.path != node.path }
                if (rawItems.isEmpty()) {
                    _uiState.value = CategoryUiState.Empty
                } else {
                    applySortingAndEmit()
                }
                onDone(true)
            } else {
                onDone(false)
            }
        }
    }

    fun renameFile(node: StorageNode, newName: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.renameFile(node, newName)
            if (res.isSuccess) {
                val updatedNode = res.getOrThrow()
                rawItems = rawItems.map { if (it.path == node.path) updatedNode else it }
                applySortingAndEmit()
                onDone(true)
            } else {
                onDone(false)
            }
        }
    }
}
