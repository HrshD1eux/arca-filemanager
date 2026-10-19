package com.Hrshd1eux.filemanager.feature.cleaner.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import com.Hrshd1eux.filemanager.core.storage.analyzer.InstalledAppsReport
import com.Hrshd1eux.filemanager.core.storage.analyzer.InstalledAppsRepository
import com.Hrshd1eux.filemanager.core.storage.analyzer.SystemStorageRepository
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareManager
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareMode
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareResult
import com.Hrshd1eux.filemanager.core.storage.model.AppTypeFilter
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppSortOrder
import com.Hrshd1eux.filemanager.core.storage.model.SystemStorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.Job
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
    val typeFilter: AppTypeFilter = AppTypeFilter.USER, // Default to User Apps for clean separation
    val sortOrder: InstalledAppSortOrder = InstalledAppSortOrder.SIZE_DESC,
    val showPackageNames: Boolean = false,
    val selectedAppForDetails: InstalledAppInfo? = null,
    val selectedAppForShare: InstalledAppInfo? = null,
    val systemStorageBreakdown: SystemStorageBreakdown = SystemStorageBreakdown(),
    val showSystemStorageDetails: Boolean = false,
    val isUsageAccessGranted: Boolean = false,
    val selectedPackageNames: Set<String> = emptySet(),
    val isBatchExporting: Boolean = false,
    val batchExportProgress: String? = null,
    val batchExportPercent: Int = 0,
    val errorMessage: String? = null
) {
    val isSelectionMode: Boolean
        get() = selectedPackageNames.isNotEmpty()
}

class InstalledAppsViewModel(
    application: Application,
    private val repository: InstalledAppsRepository = InstalledAppsRepository.getInstance(application),
    private val systemStorageRepository: SystemStorageRepository = SystemStorageRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(InstalledAppsUiState())
    val uiState: StateFlow<InstalledAppsUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var batchExportJob: Job? = null

    init {
        loadApps(forceRefresh = false)
    }

    fun loadApps(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val usageGranted = repository.hasUsageStatsPermission()
                val report = repository.getInstalledApps(forceRefresh = forceRefresh)
                val systemBreakdown = systemStorageRepository.getSystemStorageBreakdown(forceRefresh = forceRefresh)
                Triple(usageGranted, report, systemBreakdown)
            }.onSuccess { (usageGranted, report, systemBreakdown) ->
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
                    systemStorageBreakdown = systemBreakdown,
                    isUsageAccessGranted = usageGranted,
                    errorMessage = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.localizedMessage ?: "Failed to inspect applications & system storage"
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

    fun selectAppForShare(app: InstalledAppInfo?) {
        _uiState.value = _uiState.value.copy(selectedAppForShare = app)
    }

    fun toggleSystemStorageDetails(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSystemStorageDetails = show)
    }

    fun toggleAppSelection(packageName: String) {
        val current = _uiState.value.selectedPackageNames
        val newSelection = if (current.contains(packageName)) {
            current - packageName
        } else {
            current + packageName
        }
        _uiState.value = _uiState.value.copy(selectedPackageNames = newSelection)
    }

    fun selectAll() {
        val allPackageNames = _uiState.value.filteredApps.map { it.packageName }.toSet()
        val newSelection = if (_uiState.value.selectedPackageNames.containsAll(allPackageNames)) {
            emptySet()
        } else {
            allPackageNames
        }
        _uiState.value = _uiState.value.copy(selectedPackageNames = newSelection)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPackageNames = emptySet())
    }

    fun cancelBatchExport() {
        batchExportJob?.cancel()
        batchExportJob = null
        _uiState.value = _uiState.value.copy(
            isBatchExporting = false,
            batchExportProgress = null,
            batchExportPercent = 0
        )
    }

    fun exportSelectedApks(
        context: Context,
        apkShareManager: ApkShareManager = ApkShareManager.getInstance(),
        onSuccess: (Intent) -> Unit
    ) {
        val selectedApps = _uiState.value.filteredApps.filter { it.packageName in _uiState.value.selectedPackageNames }
        if (selectedApps.isEmpty()) {
            emitMessage("No applications selected for export.")
            return
        }

        batchExportJob?.cancel()
        batchExportJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBatchExporting = true,
                batchExportProgress = "Preparing ${selectedApps.size} apps...",
                batchExportPercent = 0
            )

            val result = apkShareManager.prepareBatchShareIntent(
                context = context,
                apps = selectedApps,
                shareMode = ApkShareMode.ALL_SPLITS,
                onProgress = { copiedBytes, totalBytes, appName ->
                    val percent = if (totalBytes > 0) ((copiedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                    val formattedCopied = FileFormatter.formatFileSize(copiedBytes)
                    val formattedTotal = FileFormatter.formatFileSize(totalBytes)
                    _uiState.value = _uiState.value.copy(
                        batchExportProgress = "Exporting $appName ($formattedCopied / $formattedTotal)",
                        batchExportPercent = percent
                    )
                }
            )

            _uiState.value = _uiState.value.copy(
                isBatchExporting = false,
                batchExportProgress = null,
                batchExportPercent = 0
            )

            when (result) {
                is ApkShareResult.Success -> {
                    clearSelection()
                    onSuccess(result.chooserIntent)
                }
                is ApkShareResult.InsufficientStorage -> {
                    emitMessage("Insufficient storage: Required ${result.formattedRequired}, Available ${result.formattedAvailable}.")
                }
                is ApkShareResult.Inaccessible -> {
                    emitMessage(result.reason)
                }
                is ApkShareResult.ExtractionFailed -> {
                    emitMessage(result.message)
                }
                is ApkShareResult.Cancelled -> {
                    emitMessage("Batch APK export cancelled.")
                }
            }
        }
    }

    fun emitMessage(message: String) {
        viewModelScope.launch {
            _userMessage.emit(message)
        }
    }
}
