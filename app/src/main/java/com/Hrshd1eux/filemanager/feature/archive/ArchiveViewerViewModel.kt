package com.Hrshd1eux.filemanager.feature.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface ArchiveViewerUiState {
    data object Loading : ArchiveViewerUiState
    data class Success(
        val archiveName: String,
        val currentVirtualPath: String,
        val breadcrumbs: List<String>,
        val items: List<ArchiveEntry>,
        val totalUncompressedBytes: Long,
        val totalCompressedBytes: Long,
        val totalFilesCount: Int
    ) : ArchiveViewerUiState
    data class Error(val message: String) : ArchiveViewerUiState
}

class ArchiveViewerViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zipEngine: ZipEngine = ZipEngine(ioDispatcher)
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArchiveViewerUiState>(ArchiveViewerUiState.Loading)
    val uiState: StateFlow<ArchiveViewerUiState> = _uiState.asStateFlow()

    private val _selectedEntryPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedEntryPaths: StateFlow<Set<String>> = _selectedEntryPaths.asStateFlow()

    private val _activeProgress = MutableStateFlow<OperationProgress?>(null)
    val activeProgress: StateFlow<OperationProgress?> = _activeProgress.asStateFlow()

    private val _testReport = MutableStateFlow<ArchiveIntegrityReport?>(null)
    val testReport: StateFlow<ArchiveIntegrityReport?> = _testReport.asStateFlow()

    private val _archiveProperties = MutableStateFlow<ArchiveProperties?>(null)
    val archiveProperties: StateFlow<ArchiveProperties?> = _archiveProperties.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var archiveFile: File? = null
    private var allEntries: List<ArchiveEntry> = emptyList()
    private var currentVirtualPath: String = ""

    fun loadArchive(path: String) {
        val file = File(path)
        archiveFile = file
        if (!file.exists() || !file.isFile) {
            _uiState.value = ArchiveViewerUiState.Error("Archive file does not exist: $path")
            return
        }

        viewModelScope.launch {
            _uiState.value = ArchiveViewerUiState.Loading
            try {
                allEntries = zipEngine.listEntries(file)
                currentVirtualPath = ""
                updateVisibleState()
            } catch (e: Exception) {
                _uiState.value = ArchiveViewerUiState.Error("Failed to open archive: ${e.localizedMessage}")
            }
        }
    }

    fun navigateInto(entry: ArchiveEntry) {
        if (!entry.isDirectory) return
        currentVirtualPath = entry.path
        _selectedEntryPaths.value = emptySet()
        updateVisibleState()
    }

    fun navigateUp(): Boolean {
        if (currentVirtualPath.isEmpty()) return false
        val parts = currentVirtualPath.split('/')
        currentVirtualPath = if (parts.size > 1) {
            parts.dropLast(1).joinToString("/")
        } else {
            ""
        }
        _selectedEntryPaths.value = emptySet()
        updateVisibleState()
        return true
    }

    fun navigateToBreadcrumb(targetVirtualPath: String) {
        currentVirtualPath = targetVirtualPath
        _selectedEntryPaths.value = emptySet()
        updateVisibleState()
    }

    fun toggleSelection(entryPath: String) {
        val current = _selectedEntryPaths.value.toMutableSet()
        if (current.contains(entryPath)) {
            current.remove(entryPath)
        } else {
            current.add(entryPath)
        }
        _selectedEntryPaths.value = current
    }

    fun clearSelection() {
        _selectedEntryPaths.value = emptySet()
    }

    private var activeJob: kotlinx.coroutines.Job? = null

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _activeProgress.value = null
    }

    fun extractAll(
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        onComplete: () -> Unit = {}
    ) {
        val file = archiveFile ?: return
        activeJob = viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = allEntries.size)
            val result = zipEngine.extractAll(
                zipFile = file,
                destinationDir = destinationDir,
                conflictResolution = conflictResolution,
                onProgress = { _activeProgress.value = it }
            )
            _activeProgress.value = null
            activeJob = null
            result.onSuccess { count ->
                _userMessage.emit("Extracted $count items to ${destinationDir.name}")
                onComplete()
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    _userMessage.emit("Extraction failed: ${error.localizedMessage}")
                }
            }
        }
    }

    fun extractSelected(
        destinationDir: File,
        conflictResolution: ConflictResolution = ConflictResolution.RENAME_AUTO,
        onComplete: () -> Unit = {}
    ) {
        val file = archiveFile ?: return
        val selected = _selectedEntryPaths.value
        if (selected.isEmpty()) return

        activeJob = viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = selected.size)
            val result = zipEngine.extractEntries(
                zipFile = file,
                entryPaths = selected,
                destinationDir = destinationDir,
                conflictResolution = conflictResolution,
                onProgress = { _activeProgress.value = it }
            )
            _activeProgress.value = null
            activeJob = null
            result.onSuccess { count ->
                clearSelection()
                _userMessage.emit("Extracted $count items to ${destinationDir.name}")
                onComplete()
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    _userMessage.emit("Extraction failed: ${error.localizedMessage}")
                }
            }
        }
    }

    fun testArchive() {
        val file = archiveFile ?: return
        viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = allEntries.size)
            val result = zipEngine.testArchive(
                zipFile = file,
                onProgress = { _activeProgress.value = it }
            )
            _activeProgress.value = null
            result.onSuccess { report ->
                _testReport.value = report
            }.onFailure { error ->
                _userMessage.emit("Integrity test failed to execute: ${error.localizedMessage}")
            }
        }
    }

    fun dismissTestReport() {
        _testReport.value = null
    }

    fun loadArchiveProperties() {
        val file = archiveFile ?: return
        viewModelScope.launch {
            _archiveProperties.value = zipEngine.getArchiveProperties(file)
        }
    }

    fun dismissArchiveProperties() {
        _archiveProperties.value = null
    }

    fun addFiles(sources: List<File>) {
        val file = archiveFile ?: return
        if (sources.isEmpty()) return

        viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = sources.size)
            val result = zipEngine.addEntries(
                zipFile = file,
                newSources = sources,
                targetVirtualPath = currentVirtualPath,
                onProgress = { _activeProgress.value = it }
            )
            _activeProgress.value = null
            result.onSuccess { newSize ->
                _userMessage.emit("Added ${sources.size} items (${FileFormatter.formatFileSize(newSize)})")
                // Reload archive entries
                allEntries = zipEngine.listEntries(file)
                updateVisibleState()
            }.onFailure { error ->
                _userMessage.emit("Failed to add items: ${error.localizedMessage}")
            }
        }
    }

    private fun updateVisibleState() {
        val file = archiveFile ?: return

        // Filter direct children of currentVirtualPath
        val directChildren = allEntries.filter { it.parentPath == currentVirtualPath }
        val breadcrumbs = if (currentVirtualPath.isEmpty()) {
            emptyList()
        } else {
            currentVirtualPath.split('/')
        }

        val totalUncompressed = allEntries.filter { !it.isDirectory }.sumOf { it.uncompressedSize }
        val totalCompressed = allEntries.filter { !it.isDirectory }.sumOf { it.compressedSize }
        val totalFiles = allEntries.count { !it.isDirectory }

        _uiState.value = ArchiveViewerUiState.Success(
            archiveName = file.name,
            currentVirtualPath = currentVirtualPath,
            breadcrumbs = breadcrumbs,
            items = directChildren,
            totalUncompressedBytes = totalUncompressed,
            totalCompressedBytes = totalCompressed,
            totalFilesCount = totalFiles
        )
    }
}
