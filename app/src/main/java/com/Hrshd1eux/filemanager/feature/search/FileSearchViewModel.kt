package com.Hrshd1eux.filemanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.search.engine.FileSearchEngine
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.DateFilter
import com.Hrshd1eux.filemanager.core.search.model.SearchCriteria
import com.Hrshd1eux.filemanager.core.search.model.SizeFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider
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
class FileSearchViewModel(
    val storageProvider: StorageProvider = LocalFileSystemProvider(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val searchEngine: FileSearchEngine = FileSearchEngine(ioDispatcher)
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
