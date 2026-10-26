package com.Hrshd1eux.filemanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.search.engine.FileSearchEngine
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.DateFilter
import com.Hrshd1eux.filemanager.core.search.model.SearchCriteria
import com.Hrshd1eux.filemanager.core.search.model.SizeFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider
import com.Hrshd1eux.filemanager.core.trash.manager.TrashManager
import com.Hrshd1eux.filemanager.feature.category.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Searching(val currentItems: List<StorageNode> = emptyList()) : SearchUiState
    data class Success(val items: List<StorageNode>) : SearchUiState
    data object Empty : SearchUiState
}

@OptIn(FlowPreview::class)
class FileSearchViewModel @JvmOverloads constructor(
    val storageProvider: StorageProvider = LocalFileSystemProvider(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val searchEngine: FileSearchEngine = FileSearchEngine(ioDispatcher),
    private val repository: CategoryRepository = CategoryRepository(ioDispatcher),
    private val operationEngine: FileOperationEngine = FileOperationEngine(ioDispatcher)
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _category = MutableStateFlow(CategoryFilter.ALL)
    val category: StateFlow<CategoryFilter> = _category.asStateFlow()

    private val _sizeFilter = MutableStateFlow(SizeFilter.ANY)
    val sizeFilter: StateFlow<SizeFilter> = _sizeFilter.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.ANY)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _selectedNodes = MutableStateFlow<Set<StorageNode>>(emptySet())
    val selectedNodes: StateFlow<Set<StorageNode>> = _selectedNodes.asStateFlow()

    val isSelectionMode: Boolean
        get() = _selectedNodes.value.isNotEmpty()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private val _operationProgress = MutableStateFlow<OperationProgress?>(null)
    val operationProgress: StateFlow<OperationProgress?> = _operationProgress.asStateFlow()

    private var activeRootPath: String = storageProvider.getRootNode().path
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(_query, _category, _sizeFilter, _dateFilter) { q, cat, size, date ->
                SearchCriteria(
                    query = q,
                    rootPath = activeRootPath,
                    category = cat,
                    sizeFilter = size,
                    dateFilter = date
                )
            }
                .debounce(300L)
                .distinctUntilChanged()
                .collectLatest { criteria ->
                    executeSearch(criteria)
                }
        }
    }

    fun setRootPath(path: String?) {
        activeRootPath = path ?: storageProvider.getRootNode().path
        if (_query.value.isNotEmpty() || _category.value != CategoryFilter.ALL || _sizeFilter.value != SizeFilter.ANY || _dateFilter.value != DateFilter.ANY) {
            executeSearch(
                SearchCriteria(
                    query = _query.value,
                    rootPath = activeRootPath,
                    category = _category.value,
                    sizeFilter = _sizeFilter.value,
                    dateFilter = _dateFilter.value
                )
            )
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onCategorySelect(newCategory: CategoryFilter) {
        _category.value = if (_category.value == newCategory) CategoryFilter.ALL else newCategory
    }

    fun onSizeFilterSelect(newSizeFilter: SizeFilter) {
        _sizeFilter.value = if (_sizeFilter.value == newSizeFilter) SizeFilter.ANY else newSizeFilter
    }

    fun onDateFilterSelect(newDateFilter: DateFilter) {
        _dateFilter.value = if (_dateFilter.value == newDateFilter) DateFilter.ANY else newDateFilter
    }

    fun clearQuery() {
        _query.value = ""
    }

    // --- Multi-Selection Controls ---

    fun startSelection(node: StorageNode) {
        _selectedNodes.value = setOf(node)
    }

    fun toggleSelection(node: StorageNode) {
        val current = _selectedNodes.value.toMutableSet()
        val existing = current.find { it.path == node.path }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(node)
        }
        _selectedNodes.value = current
    }

    fun selectAll(items: List<StorageNode>) {
        _selectedNodes.value = items.toSet()
    }

    fun toggleSelectAll(items: List<StorageNode>) {
        if (_selectedNodes.value.size >= items.size && items.isNotEmpty()) {
            _selectedNodes.value = emptySet()
        } else {
            _selectedNodes.value = items.toSet()
        }
    }

    fun clearSelection() {
        _selectedNodes.value = emptySet()
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
            if (result.isSuccess) {
                removeNodesFromResults(targets)
            }
            clearSelection()
            onDone(result.isSuccess)
        }
    }

    fun deleteSelected(permanent: Boolean = true, onDone: (Boolean) -> Unit = {}) {
        val targets = _selectedNodes.value.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            _isOperating.value = true
            val success = if (!permanent) {
                TrashManager.getInstance().moveToTrash(targets).isSuccess
            } else {
                operationEngine.delete(
                    targets = targets,
                    onProgress = { _operationProgress.value = it }
                ).isSuccess
            }
            _isOperating.value = false
            _operationProgress.value = null
            if (success) {
                removeNodesFromResults(targets)
            }
            clearSelection()
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
                removeNodesFromResults(listOf(node))
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
                val current = _uiState.value
                if (current is SearchUiState.Success) {
                    _uiState.value = SearchUiState.Success(
                        current.items.map { if (it.path == node.path) updatedNode else it }
                    )
                }
                onDone(true)
            } else {
                onDone(false)
            }
        }
    }

    private fun removeNodesFromResults(nodesToRemove: List<StorageNode>) {
        val removedPaths = nodesToRemove.map { it.path }.toSet()
        val current = _uiState.value
        if (current is SearchUiState.Success) {
            val remaining = current.items.filter { it.path !in removedPaths }
            _uiState.value = if (remaining.isEmpty()) SearchUiState.Empty else SearchUiState.Success(remaining)
        }
    }

    private fun executeSearch(criteria: SearchCriteria) {
        searchJob?.cancel()

        if (criteria.query.isBlank() &&
            criteria.category == CategoryFilter.ALL &&
            criteria.sizeFilter == SizeFilter.ANY &&
            criteria.dateFilter == DateFilter.ANY
        ) {
            _uiState.value = SearchUiState.Idle
            return
        }

        searchJob = viewModelScope.launch(ioDispatcher) {
            _uiState.value = SearchUiState.Searching()

            searchEngine.search(criteria).collect { matchingNodes ->
                if (matchingNodes.isEmpty()) {
                    _uiState.value = SearchUiState.Empty
                } else {
                    _uiState.value = SearchUiState.Success(matchingNodes)
                }
            }
        }
    }
}
