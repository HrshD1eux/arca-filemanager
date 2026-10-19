package com.Hrshd1eux.filemanager.feature.cleaner.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.storage.analyzer.InstalledAppsReport
import com.Hrshd1eux.filemanager.core.storage.analyzer.InstalledAppsRepository
import com.Hrshd1eux.filemanager.core.storage.model.AppTypeFilter
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppSortOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InstalledAppsUiState(
    val isLoading: Boolean = true,
    val report: InstalledAppsReport = InstalledAppsReport(),
    val filteredApps: List<InstalledAppInfo> = emptyList(),
    val searchQuery: String = "",
    val typeFilter: AppTypeFilter = AppTypeFilter.ALL,
    val sortOrder: InstalledAppSortOrder = InstalledAppSortOrder.SIZE_DESC,
    val showPackageNames: Boolean = false,
    val selectedAppForDetails: InstalledAppInfo? = null,
    val isUsageAccessGranted: Boolean = false,
    val errorMessage: String? = null
)

class InstalledAppsViewModel(
    application: Application,
    private val repository: InstalledAppsRepository = InstalledAppsRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(InstalledAppsUiState())
    val uiState: StateFlow<InstalledAppsUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        loadApps(forceRefresh = false)
    }

    fun loadApps(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val usageGranted = repository.hasUsageStatsPermission()
                val report = repository.getInstalledApps(forceRefresh = forceRefresh)
                usageGranted to report
            }.onSuccess { (usageGranted, report) ->
                val current = _uiState.value
                val filtered = repository.filterAndSort(
                    apps = report.apps,
                    query = current.searchQuery,
                    filter = current.typeFilter,
                    sortOrder = current.sortOrder
                )
                _uiState.value = current.copy(
                    isLoading = false,
                    report = report,
                    filteredApps = filtered,
                    isUsageAccessGranted = usageGranted,
                    errorMessage = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.localizedMessage ?: "Failed to inspect installed applications"
                )
            }
        }
    }

    /**
     * Checks if usage access permission has changed on resume, refreshing storage stats if newly granted.
     */
    fun onResumeCheck() {
        val currentPermission = repository.hasUsageStatsPermission()
        if (currentPermission != _uiState.value.isUsageAccessGranted) {
            loadApps(forceRefresh = true)
        }
    }

    fun setSearchQuery(query: String) {
        val current = _uiState.value
        val filtered = repository.filterAndSort(
            apps = current.report.apps,
            query = query,
            filter = current.typeFilter,
            sortOrder = current.sortOrder
        )
        _uiState.value = current.copy(
            searchQuery = query,
            filteredApps = filtered
        )
    }

    fun setTypeFilter(filter: AppTypeFilter) {
        val current = _uiState.value
        val filtered = repository.filterAndSort(
            apps = current.report.apps,
            query = current.searchQuery,
            filter = filter,
            sortOrder = current.sortOrder
        )
        _uiState.value = current.copy(
            typeFilter = filter,
            filteredApps = filtered
        )
    }

    fun setSortOrder(sortOrder: InstalledAppSortOrder) {
        val current = _uiState.value
        val filtered = repository.filterAndSort(
            apps = current.report.apps,
            query = current.searchQuery,
            filter = current.typeFilter,
            sortOrder = sortOrder
        )
        _uiState.value = current.copy(
            sortOrder = sortOrder,
            filteredApps = filtered
        )
    }

    fun toggleShowPackageNames() {
        val current = _uiState.value
        _uiState.value = current.copy(showPackageNames = !current.showPackageNames)
    }

    fun selectAppForDetails(app: InstalledAppInfo?) {
        _uiState.value = _uiState.value.copy(selectedAppForDetails = app)
    }

    fun emitMessage(message: String) {
        viewModelScope.launch {
            _userMessage.emit(message)
        }
    }
}
