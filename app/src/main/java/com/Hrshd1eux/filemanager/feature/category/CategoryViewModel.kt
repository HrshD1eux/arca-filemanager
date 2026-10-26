package com.Hrshd1eux.filemanager.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.trash.manager.TrashManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryViewModel @JvmOverloads constructor(
    private val repository: CategoryRepository = CategoryRepository(),
    private val operationEngine: FileOperationEngine = FileOperationEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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

    // Multi-selection state
    private val _selectedNodes = MutableStateFlow<Set<StorageNode>>(emptySet())
    val selectedNodes: StateFlow<Set<StorageNode>> = _selectedNodes.asStateFlow()

    val isSelectionMode: Boolean
        get() = _selectedNodes.value.isNotEmpty()

    // Background operation progress state
    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

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
        clearSelection()

        // Default ViewMode: Grid for Images and Videos, List for documents/audio/archives
        if (cat == CategoryFilter.IMAGES || cat == CategoryFilter.VIDEOS) {
            _viewMode.value = ViewMode.GRID
        } else {
            _viewMode.value = ViewMode.LIST
        }

        refresh()
    }

    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = CategoryUiState.Loading
            try {
                val files = repository.getFilesForCategory(_category.value, currentRootPath)
                rawItems = files
                if (files.isEmpty()) {
                    _uiState.value = CategoryUiState.Empty(
                        message = getCategoryEmptyMessage(_category.value)
                    )
                } else {
                    applySortingAndEmit()
                }
            } catch (e: Exception) {
                _uiState.value = CategoryUiState.Error(
                    message = e.localizedMessage ?: "Failed to scan files in storage location"
                )
            }
        }
    }

    // --- Multi-Selection ---

    fun startSelection(node: StorageNode) {
        _selectedNodes.value = setOf(node)
    }

    fun toggleSelection(node: StorageNode) {
        val current = _selectedNodes.value.toMutableSet()
        if (current.any { it.path == node.path }) {
            current.removeAll { it.path == node.path }
        } else {
            current.add(node)
        }
        _selectedNodes.value = current
    }

    fun selectAll() {
        _selectedNodes.value = rawItems.toSet()
    }

    fun toggleSelectAll() {
        val items = (uiState.value as? CategoryUiState.Success)?.items ?: rawItems
        if (_selectedNodes.value.size >= items.size && items.isNotEmpty()) {
            _selectedNodes.value = emptySet()
        } else {
            _selectedNodes.value = items.toSet()
        }
    }

    fun clearSelection() {
        _selectedNodes.value = emptySet()
    }

    // --- Sorting Controls ---

    fun updateSortOrder(newOrder: SortOrder) {
        _sortOrder.value = newOrder
        if (rawItems.isNotEmpty()) {
            applySortingAndEmit()
        }
    }

    fun toggleSortDirection() {
        val current = _sortOrder.value
        val newDirection = if (current.direction == SortDirection.ASCENDING) {
            SortDirection.DESCENDING
        } else {
            SortDirection.ASCENDING
        }
        updateSortOrder(current.copy(direction = newDirection))
    }

    fun setSortField(field: SortField) {
        val current = _sortOrder.value
        if (current.field == field) {
            toggleSortDirection()
        } else {
            updateSortOrder(current.copy(field = field))
        }
    }

    // --- View Mode Controls ---

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

    // --- File Actions ---

    fun copySelected(destination: StorageNode, verifyIntegrity: Boolean, onDone: (Boolean) -> Unit = {}) {
        val targets = _selectedNodes.value.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            _isOperating.value = true
            val result = operationEngine.copy(
                sources = targets,
                destinationDir = destination,
                conflictResolution = ConflictResolution.RENAME_AUTO,
                verifyIntegrity = verifyIntegrity,
                onProgress = { _operationProgress.value = it }
            )
            _isOperating.value = false
            _operationProgress.value = null
            clearSelection()
            refresh()
            onDone(result.isSuccess)
        }
    }

    fun moveSelected(destination: StorageNode, verifyIntegrity: Boolean, onDone: (Boolean) -> Unit = {}) {
        val targets = _selectedNodes.value.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            _isOperating.value = true
            val result = operationEngine.move(
                sources = targets,
                destinationDir = destination,
                conflictResolution = ConflictResolution.RENAME_AUTO,
                onProgress = { _operationProgress.value = it }
            )
            _isOperating.value = false
            _operationProgress.value = null
            clearSelection()
            refresh()
            onDone(result.isSuccess)
        }
    }

    fun deleteSelected(permanent: Boolean = true, onDone: (Boolean) -> Unit = {}) {
        val targets = _selectedNodes.value.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            _isOperating.value = true
            val success = if (!permanent) {
                val res = TrashManager.getInstance().moveToTrash(targets)
                res.isSuccess
            } else {
                val result = operationEngine.delete(
                    targets = targets,
                    onProgress = { _operationProgress.value = it }
                )
                result.isSuccess
            }
            _isOperating.value = false
            _operationProgress.value = null
            clearSelection()
            refresh()
            onDone(success)
        }
    }

    fun deleteSingle(node: StorageNode, permanent: Boolean = true, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch(ioDispatcher) {
            val success = if (!permanent) {
                TrashManager.getInstance().moveToTrash(node).isSuccess
            } else {
                repository.deleteFile(node).isSuccess
            }
            if (success) {
                rawItems = rawItems.filter { it.path != node.path }
                if (rawItems.isEmpty()) {
                    _uiState.value = CategoryUiState.Empty(getCategoryEmptyMessage(_category.value))
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
        viewModelScope.launch(ioDispatcher) {
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

    private fun getCategoryEmptyMessage(category: CategoryFilter): String {
        return when (category) {
            CategoryFilter.IMAGES -> "No image files found in internal storage. Photos and pictures will appear here."
            CategoryFilter.VIDEOS -> "No video files found in internal storage. Video recordings and movies will appear here."
            CategoryFilter.AUDIO -> "No audio files found in internal storage. Music tracks, podcasts, and recordings will appear here."
            CategoryFilter.DOCUMENTS -> "No document files found in internal storage. PDFs, Word docs, and text files will appear here."
            CategoryFilter.DOWNLOADS -> "No downloaded files found in the Downloads folder."
            CategoryFilter.ARCHIVES -> "No archive or compressed files found (ZIP, RAR, 7Z, TAR)."
            CategoryFilter.APKS -> "No Android APK installation packages found."
            CategoryFilter.OTHER -> "No miscellaneous files found in internal storage."
            CategoryFilter.ALL -> "No files found in this storage location."
        }
    }
}
