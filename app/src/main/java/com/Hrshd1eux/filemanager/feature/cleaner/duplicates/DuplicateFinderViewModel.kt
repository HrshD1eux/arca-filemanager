package com.Hrshd1eux.filemanager.feature.cleaner.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.cleaner.engine.DuplicateFinderEngine
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateScanResult
import com.Hrshd1eux.filemanager.core.cleaner.model.ScanStage
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.trash.manager.TrashManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DuplicateFinderUiState {
    data object Idle : DuplicateFinderUiState
    data class Scanning(
        val stage: ScanStage,
        val progress: Float,
        val currentFileName: String
    ) : DuplicateFinderUiState
    data class Success(val result: DuplicateScanResult) : DuplicateFinderUiState
    data object Empty : DuplicateFinderUiState
}

class DuplicateFinderViewModel @JvmOverloads constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val engine: DuplicateFinderEngine = DuplicateFinderEngine(ioDispatcher),
    private val operationEngine: FileOperationEngine = FileOperationEngine(ioDispatcher)
) : ViewModel() {

    private val _uiState = MutableStateFlow<DuplicateFinderUiState>(DuplicateFinderUiState.Idle)
    val uiState: StateFlow<DuplicateFinderUiState> = _uiState.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _activeProgress = MutableStateFlow<OperationProgress?>(null)
    val activeProgress: StateFlow<OperationProgress?> = _activeProgress.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var currentRootPath: String? = null

    init {
        startScan()
    }

    fun setRootPath(path: String?) {
        if (currentRootPath != path) {
            currentRootPath = path
            startScan()
        }
    }

    fun startScan() {
        viewModelScope.launch {
            _selectedIds.value = emptySet()
            _uiState.value = DuplicateFinderUiState.Scanning(
                stage = ScanStage.INDEXING_SIZES,
                progress = 0f,
                currentFileName = ""
            )

            val result = engine.scanDuplicates(
                rootPath = currentRootPath,
                onProgress = { stage, progress, currentFile ->
                    _uiState.value = DuplicateFinderUiState.Scanning(
                        stage = stage,
                        progress = progress,
                        currentFileName = currentFile
                    )
                }
            )

            if (result.groups.isEmpty()) {
                _uiState.value = DuplicateFinderUiState.Empty
            } else {
                _uiState.value = DuplicateFinderUiState.Success(result)
            }
        }
    }

    fun toggleSelection(nodeId: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(nodeId)) {
            current.remove(nodeId)
        } else {
            current.add(nodeId)
        }
        _selectedIds.value = current
    }

    fun selectKeepNewest() {
        val state = _uiState.value
        if (state !is DuplicateFinderUiState.Success) return

        val toSelect = mutableSetOf<String>()
        for (group in state.result.groups) {
            // Sort by lastModified descending -> first item is newest
            val sorted = group.files.sortedByDescending { it.lastModified }
            // Select all except the newest
            toSelect.addAll(sorted.drop(1).map { it.id })
        }
        _selectedIds.value = toSelect
    }

    fun selectKeepOldest() {
        val state = _uiState.value
        if (state !is DuplicateFinderUiState.Success) return

        val toSelect = mutableSetOf<String>()
        for (group in state.result.groups) {
            // Sort by lastModified ascending -> first item is oldest
            val sorted = group.files.sortedBy { it.lastModified }
            // Select all except the oldest
            toSelect.addAll(sorted.drop(1).map { it.id })
        }
        _selectedIds.value = toSelect
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected(permanent: Boolean = true) {
        val state = _uiState.value
        if (state !is DuplicateFinderUiState.Success) return

        val allNodes = state.result.groups.flatMap { it.files }
        val toDelete = allNodes.filter { _selectedIds.value.contains(it.id) }
        if (toDelete.isEmpty()) return

        viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = toDelete.size)
            if (!permanent) {
                val result = TrashManager.getInstance().moveToTrash(toDelete)
                _activeProgress.value = null
                result.onSuccess { count ->
                    clearSelection()
                    _userMessage.emit("Moved $count duplicate copies to Trash")
                    startScan()
                }.onFailure { error ->
                    _userMessage.emit("Error: ${error.localizedMessage ?: "Failed to move to Trash"}")
                }
            } else {
                val result = operationEngine.delete(
                    targets = toDelete,
                    onProgress = { _activeProgress.value = it }
                )
                _activeProgress.value = null
                result.onSuccess {
                    clearSelection()
                    _userMessage.emit("Permanently deleted ${toDelete.size} duplicate copies")
                    startScan()
                }.onFailure { error ->
                    _userMessage.emit("Error: ${error.localizedMessage ?: "Deletion failed"}")
                }
            }
        }
    }
}
