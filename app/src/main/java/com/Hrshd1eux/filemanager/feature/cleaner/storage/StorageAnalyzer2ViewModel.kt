package com.Hrshd1eux.filemanager.feature.cleaner.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.cleaner.engine.CleanupSafetyValidator
import com.Hrshd1eux.filemanager.core.cleaner.model.AnalyzerSortBy
import com.Hrshd1eux.filemanager.core.cleaner.model.CleanupCandidate
import com.Hrshd1eux.filemanager.core.cleaner.model.CleanupExecutionReport
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateScanResult
import com.Hrshd1eux.filemanager.core.cleaner.model.EmptyFolderItem
import com.Hrshd1eux.filemanager.core.cleaner.model.LargeFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.model.OldFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.model.ScanDiagnostics
import com.Hrshd1eux.filemanager.core.storage.analyzer.StorageAnalyzer2Engine
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StorageAnalyzerTab(val title: String) {
    OVERVIEW("Overview"),
    LARGE_FILES("Large Files"),
    DUPLICATES("Duplicates"),
    OLD_FILES("Old Files"),
    ARCHIVES("Archives"),
    EMPTY_FOLDERS("Empty Folders")
}

data class StorageAnalyzer2UiState(
    val activeTab: StorageAnalyzerTab = StorageAnalyzerTab.OVERVIEW,
    val isScanning: Boolean = false,
    val storageBreakdown: StorageBreakdown = StorageBreakdown(),
    val largeFiles: List<CleanupCandidate> = emptyList(),
    val duplicateResult: DuplicateScanResult = DuplicateScanResult(),
    val oldFiles: List<CleanupCandidate> = emptyList(),
    val archives: List<CleanupCandidate> = emptyList(),
    val emptyFolders: List<EmptyFolderItem> = emptyList(),
    val diagnostics: ScanDiagnostics? = null,
    val largeFileThreshold: LargeFileThreshold = LargeFileThreshold.MB_100,
    val oldFileThreshold: OldFileThreshold = OldFileThreshold.YEAR_1,
    val sortBy: AnalyzerSortBy = AnalyzerSortBy.SIZE_DESC,
    val selectedIds: Set<String> = emptySet(),
    val lastExecutionReport: CleanupExecutionReport? = null
)

class StorageAnalyzer2ViewModel @JvmOverloads constructor(
    private val engine: StorageAnalyzer2Engine = StorageAnalyzer2Engine(),
    private val safetyValidator: CleanupSafetyValidator = CleanupSafetyValidator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageAnalyzer2UiState())
    val uiState: StateFlow<StorageAnalyzer2UiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var currentRootPath: String? = null

    init {
        loadOverview()
    }

    fun setRootPath(path: String?) {
        if (currentRootPath != path) {
            currentRootPath = path
            refreshCurrentTab()
        }
    }

    fun selectTab(tab: StorageAnalyzerTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab, selectedIds = emptySet())
        when (tab) {
            StorageAnalyzerTab.OVERVIEW -> loadOverview()
            StorageAnalyzerTab.LARGE_FILES -> loadLargeFiles()
            StorageAnalyzerTab.DUPLICATES -> loadDuplicates()
            StorageAnalyzerTab.OLD_FILES -> loadOldFiles()
            StorageAnalyzerTab.ARCHIVES -> loadArchives()
            StorageAnalyzerTab.EMPTY_FOLDERS -> loadEmptyFolders()
        }
    }

    fun setLargeFileThreshold(threshold: LargeFileThreshold) {
        _uiState.value = _uiState.value.copy(largeFileThreshold = threshold, selectedIds = emptySet())
        loadLargeFiles()
    }

    fun setOldFileThreshold(threshold: OldFileThreshold) {
        _uiState.value = _uiState.value.copy(oldFileThreshold = threshold, selectedIds = emptySet())
        loadOldFiles()
    }

    fun setSortBy(sort: AnalyzerSortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sort)
        when (_uiState.value.activeTab) {
            StorageAnalyzerTab.LARGE_FILES -> loadLargeFiles()
            StorageAnalyzerTab.OLD_FILES -> loadOldFiles()
            StorageAnalyzerTab.ARCHIVES -> loadArchives()
            else -> {}
        }
    }

    fun refreshCurrentTab() {
        selectTab(_uiState.value.activeTab)
    }

    fun toggleSelection(id: String) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _uiState.value = _uiState.value.copy(selectedIds = current)
    }

    fun selectAll() {
        val allIds = when (_uiState.value.activeTab) {
            StorageAnalyzerTab.LARGE_FILES -> _uiState.value.largeFiles.map { it.id }.toSet()
            StorageAnalyzerTab.OLD_FILES -> _uiState.value.oldFiles.map { it.id }.toSet()
            StorageAnalyzerTab.ARCHIVES -> _uiState.value.archives.map { it.id }.toSet()
            StorageAnalyzerTab.EMPTY_FOLDERS -> _uiState.value.emptyFolders.map { it.id }.toSet()
            StorageAnalyzerTab.DUPLICATES -> {
                _uiState.value.duplicateResult.groups.flatMap { group ->
                    group.files.drop(1).map { it.id }
                }.toSet()
            }
            StorageAnalyzerTab.OVERVIEW -> emptySet()
        }
        _uiState.value = _uiState.value.copy(selectedIds = allIds)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedIds = emptySet())
    }

    /**
     * Gathers the currently selected candidates for the safe cleanup review screen.
     */
    fun getSelectedCleanupCandidates(): List<CleanupCandidate> {
        val selectedIds = _uiState.value.selectedIds
        return when (_uiState.value.activeTab) {
            StorageAnalyzerTab.LARGE_FILES -> _uiState.value.largeFiles.filter { selectedIds.contains(it.id) }
            StorageAnalyzerTab.OLD_FILES -> _uiState.value.oldFiles.filter { selectedIds.contains(it.id) }
            StorageAnalyzerTab.ARCHIVES -> _uiState.value.archives.filter { selectedIds.contains(it.id) }
            StorageAnalyzerTab.EMPTY_FOLDERS -> _uiState.value.emptyFolders.filter { selectedIds.contains(it.id) }.map {
                CleanupCandidate(
                    id = it.id,
                    path = it.path,
                    name = it.name,
                    size = 0L,
                    lastModified = it.lastModified,
                    isDirectory = true,
                    category = com.Hrshd1eux.filemanager.core.storage.model.StorageCategory.OTHER,
                    flagReason = "Empty directory (0 files, 0 subdirectories)"
                )
            }
            StorageAnalyzerTab.DUPLICATES -> {
                val candidates = mutableListOf<CleanupCandidate>()
                for (group in _uiState.value.duplicateResult.groups) {
                    val original = group.files.firstOrNull()
                    for (node in group.files) {
                        if (selectedIds.contains(node.id)) {
                            candidates.add(
                                CleanupCandidate(
                                    id = node.id,
                                    path = node.path,
                                    name = node.name,
                                    size = node.size,
                                    lastModified = node.lastModified,
                                    isDirectory = false,
                                    category = com.Hrshd1eux.filemanager.core.storage.model.StorageCategory.OTHER,
                                    flagReason = "Duplicate file (Matches ${original?.path ?: "original"})",
                                    duplicateReferencePath = original?.path,
                                    matchLevel = group.matchLevel
                                )
                            )
                        }
                    }
                }
                candidates
            }
            StorageAnalyzerTab.OVERVIEW -> emptyList()
        }
    }

    /**
     * Executes safe deletion with pre-flight revalidation.
     */
    fun executeSafeCleanup(targets: List<CleanupCandidate>) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val report = safetyValidator.executeSafeDeletion(targets)
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                lastExecutionReport = report,
                selectedIds = emptySet()
            )

            if (report.skippedStaleCount > 0) {
                _userMessage.emit("Deleted ${report.deletedCount} items. ${report.skippedStaleCount} items were skipped because they changed after the scan.")
            } else if (report.restrictedCount > 0) {
                _userMessage.emit("Deleted ${report.deletedCount} items. ${report.restrictedCount} items were restricted by Android.")
            } else {
                _userMessage.emit("Safely removed ${report.deletedCount} items.")
            }

            refreshCurrentTab()
        }
    }

    private fun loadOverview() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val (breakdown, diag) = engine.scanCategoryBreakdown(currentRootPath)
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                storageBreakdown = breakdown,
                diagnostics = diag
            )
        }
    }

    private fun loadLargeFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val (files, diag) = engine.scanLargeFiles(
                rootPath = currentRootPath,
                threshold = _uiState.value.largeFileThreshold,
                sortBy = _uiState.value.sortBy
            )
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                largeFiles = files,
                diagnostics = diag
            )
        }
    }

    private fun loadDuplicates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val (result, diag) = engine.scanDuplicateCandidatesStaged(
                rootPath = currentRootPath,
                computeHashes = true
            )
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                duplicateResult = result,
                diagnostics = diag
            )
        }
    }

    private fun loadOldFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val (files, diag) = engine.scanOldFiles(
                rootPath = currentRootPath,
                threshold = _uiState.value.oldFileThreshold,
                sortBy = _uiState.value.sortBy
            )
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                oldFiles = files,
                diagnostics = diag
            )
        }
    }

    private fun loadArchives() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val (files, diag) = engine.scanArchives(
                rootPath = currentRootPath,
                sortBy = _uiState.value.sortBy
            )
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                archives = files,
                diagnostics = diag
            )
        }
    }

    private fun loadEmptyFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val (folders, diag) = engine.scanEmptyFolders(currentRootPath)
            _uiState.value = _uiState.value.copy(
                isScanning = false,
                emptyFolders = folders,
                diagnostics = diag
            )
        }
    }
}
