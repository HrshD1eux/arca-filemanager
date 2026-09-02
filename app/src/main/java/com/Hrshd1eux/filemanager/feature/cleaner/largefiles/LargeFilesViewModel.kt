package com.Hrshd1eux.filemanager.feature.cleaner.largefiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.cleaner.model.LargeFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.scanner.LargeFilesScanner
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.model.OperationResult
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LargeFilesUiState {
    data object Loading : LargeFilesUiState
    data class Success(
        val files: List<StorageNode>,
        val totalBytes: Long
    ) : LargeFilesUiState
    data object Empty : LargeFilesUiState
}

class LargeFilesViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scanner: LargeFilesScanner = LargeFilesScanner(ioDispatcher),
    private val operationEngine: FileOperationEngine = FileOperationEngine(ioDispatcher)
) : ViewModel() {

    private val _threshold = MutableStateFlow(LargeFileThreshold.MB_100)
    val threshold: StateFlow<LargeFileThreshold> = _threshold.asStateFlow()

    private val _uiState = MutableStateFlow<LargeFilesUiState>(LargeFilesUiState.Loading)
    val uiState: StateFlow<LargeFilesUiState> = _uiState.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _activeProgress = MutableStateFlow<OperationProgress?>(null)
    val activeProgress: StateFlow<OperationProgress?> = _activeProgress.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var currentRootPath: String? = null

    init {
        scanFiles()
    }

    fun setRootPath(path: String?) {
        if (currentRootPath != path) {
            currentRootPath = path
            scanFiles()
        }
    }

    fun setThreshold(newThreshold: LargeFileThreshold) {
        if (_threshold.value != newThreshold) {
            _threshold.value = newThreshold
            clearSelection()
            scanFiles()
        }
    }

    fun refresh() {
        scanFiles()
    }

    fun toggleSelection(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current
    }

    fun selectAll() {
        val state = _uiState.value
        if (state is LargeFilesUiState.Success) {
            _selectedIds.value = state.files.map { it.id }.toSet()
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val state = _uiState.value
        if (state !is LargeFilesUiState.Success) return

        val toDelete = state.files.filter { _selectedIds.value.contains(it.id) }
        if (toDelete.isEmpty()) return

        viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = toDelete.size)
            val result = operationEngine.delete(
                targets = toDelete,
                onProgress = { _activeProgress.value = it }
            )
            _activeProgress.value = null
            result.onSuccess {
                clearSelection()
                _userMessage.emit("Deleted ${toDelete.size} large files")
                scanFiles()
            }.onFailure { error ->
                _userMessage.emit("Error: ${error.localizedMessage ?: "Deletion failed"}")
            }
        }
    }

    private fun scanFiles() {
        viewModelScope.launch {
            _uiState.value = LargeFilesUiState.Loading
            val files = scanner.scanLargeFiles(currentRootPath, _threshold.value)
            if (files.isEmpty()) {
                _uiState.value = LargeFilesUiState.Empty
            } else {
                val totalBytes = files.sumOf { it.size }
                _uiState.value = LargeFilesUiState.Success(files, totalBytes)
            }
        }
    }
}
