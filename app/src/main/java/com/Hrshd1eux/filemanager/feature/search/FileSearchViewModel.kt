package com.Hrshd1eux.filemanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.search.engine.FileSearchEngine
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.DateFilter
import com.Hrshd1eux.filemanager.core.search.model.FileTypeScope
import com.Hrshd1eux.filemanager.core.search.model.MatchType
import com.Hrshd1eux.filemanager.core.search.model.SearchCriteria
import com.Hrshd1eux.filemanager.core.search.model.SearchSortBy
import com.Hrshd1eux.filemanager.core.search.model.SizeFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider
import com.Hrshd1eux.filemanager.core.storage.util.PathJumpResolver
import com.Hrshd1eux.filemanager.core.storage.util.PathJumpTarget
import com.Hrshd1eux.filemanager.core.storage.util.PathResolutionResult
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

enum class SearchViewMode {
    LIST,
    GRID
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

    // Advanced search options
    private val _matchType = MutableStateFlow(MatchType.CONTAINS)
    val matchType: StateFlow<MatchType> = _matchType.asStateFlow()

    private val _matchCase = MutableStateFlow(false)
    val matchCase: StateFlow<Boolean> = _matchCase.asStateFlow()

    private val _fileTypeScope = MutableStateFlow(FileTypeScope.ALL)
    val fileTypeScope: StateFlow<FileTypeScope> = _fileTypeScope.asStateFlow()

    private val _extensionFilter = MutableStateFlow<String?>(null)
    val extensionFilter: StateFlow<String?> = _extensionFilter.asStateFlow()

    private val _sortBy = MutableStateFlow(SearchSortBy.RELEVANCE)
    val sortBy: StateFlow<SearchSortBy> = _sortBy.asStateFlow()

    private val _searchEverywhere = MutableStateFlow(true)
    val searchEverywhere: StateFlow<Boolean> = _searchEverywhere.asStateFlow()

    private val _viewMode = MutableStateFlow(SearchViewMode.LIST)
    val viewMode: StateFlow<SearchViewMode> = _viewMode.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _detectedPathTarget = MutableStateFlow<PathJumpTarget?>(null)
    val detectedPathTarget: StateFlow<PathJumpTarget?> = _detectedPathTarget.asStateFlow()

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
            combine(
                combine(_query, _category, _sizeFilter, _dateFilter) { q, cat, size, date ->
                    Tuple4(q, cat, size, date)
                },
                combine(_matchType, _matchCase, _fileTypeScope, _extensionFilter) { mt, mc, fts, ext ->
                    Tuple4(mt, mc, fts, ext)
                },
                combine(_sortBy, _searchEverywhere) { sb, se ->
                    Pair(sb, se)
                }
            ) { t1, t2, t3 ->
                SearchCriteria(
                    query = t1.a,
                    rootPath = activeRootPath,
                    category = t1.b,
                    sizeFilter = t1.c,
                    dateFilter = t1.d,
                    matchType = t2.a,
                    matchCase = t2.b,
                    fileTypeScope = t2.c,
                    extensionFilter = t2.d,
                    sortBy = t3.first,
                    searchEverywhere = t3.second
                )
            }
                .debounce(250L)
                .distinctUntilChanged()
                .collectLatest { criteria ->
                    executeSearch(criteria)
                }
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun setRootPath(path: String?) {
        activeRootPath = path ?: storageProvider.getRootNode().path
        if (path != null && path != storageProvider.getRootNode().path) {
            _searchEverywhere.value = false
        }
        updateDetectedPath(_query.value)
        triggerCurrentSearch()
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        updateDetectedPath(newQuery)
    }

    private fun updateDetectedPath(rawQuery: String) {
        if (!PathJumpResolver.looksLikeExplicitPath(rawQuery)) {
            _detectedPathTarget.value = null
            return
        }
        val resolution = PathJumpResolver.resolve(
            rawInput = rawQuery,
            externalStorageRoot = storageProvider.getRootNode().path
        )
        _detectedPathTarget.value = (resolution as? PathResolutionResult.Valid)?.target
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

    fun setMatchType(type: MatchType) {
        _matchType.value = type
    }

    fun toggleMatchCase() {
        _matchCase.value = !_matchCase.value
    }

    fun setFileTypeScope(scope: FileTypeScope) {
        _fileTypeScope.value = scope
    }

    fun setExtensionFilter(ext: String?) {
        _extensionFilter.value = if (_extensionFilter.value == ext) null else ext
    }

    fun setSortBy(sortBy: SearchSortBy) {
        _sortBy.value = sortBy
    }

    fun toggleSearchEverywhere() {
        _searchEverywhere.value = !_searchEverywhere.value
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == SearchViewMode.LIST) SearchViewMode.GRID else SearchViewMode.LIST
    }

    fun addRecentSearch(text: String) {
        val trimmed = text.trim()
        if (trimmed.length >= 2) {
            val list = (_recentSearches.value.filter { it != trimmed }).toMutableList()
            list.add(0, trimmed)
            _recentSearches.value = list.take(8)
        }
    }

    fun removeRecentSearch(text: String) {
        _recentSearches.value = _recentSearches.value.filter { it != text }
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
    }

    fun clearQuery() {
        _query.value = ""
        _detectedPathTarget.value = null
    }

    private fun triggerCurrentSearch() {
        executeSearch(
            SearchCriteria(
                query = _query.value,
                rootPath = activeRootPath,
                category = _category.value,
                sizeFilter = _sizeFilter.value,
                dateFilter = _dateFilter.value,
                matchType = _matchType.value,
                matchCase = _matchCase.value,
                fileTypeScope = _fileTypeScope.value,
                extensionFilter = _extensionFilter.value,
                sortBy = _sortBy.value,
                searchEverywhere = _searchEverywhere.value
            )
        )
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
            criteria.dateFilter == DateFilter.ANY &&
            criteria.extensionFilter == null
        ) {
            _uiState.value = SearchUiState.Idle
            return
        }

        if (criteria.query.isNotBlank()) {
            addRecentSearch(criteria.query)
        }

        if (_detectedPathTarget.value != null && PathJumpResolver.looksLikeExplicitPath(criteria.query)) {
            _uiState.value = SearchUiState.Empty
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
