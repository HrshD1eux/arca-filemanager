package com.Hrshd1eux.filemanager.feature.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.archive.engine.RarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.archive.engine.TarEngine
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
        val totalFilesCount: Int,
        val isReadOnly: Boolean = false,
        val formatBadge: String? = null
    ) : ArchiveViewerUiState
    data class Error(val message: String) : ArchiveViewerUiState
}

class ArchiveViewerViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zipEngine: ZipEngine = ZipEngine(ioDispatcher),
    private val sevenZEngine: SevenZEngine = SevenZEngine(ioDispatcher),
    private val tarEngine: TarEngine = TarEngine(ioDispatcher),
    private val rarEngine: RarEngine = RarEngine(ioDispatcher)
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

    private fun isRar(file: File?): Boolean {
        return file?.extension?.equals("rar", ignoreCase = true) == true
    }

    private fun is7z(file: File?): Boolean {
        return file?.extension?.equals("7z", ignoreCase = true) == true
    }

    private fun isTar(file: File?): Boolean {
        val name = file?.name?.lowercase() ?: return false
        return name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz") ||
                name.endsWith(".tar.xz") || name.endsWith(".txz") ||
                name.endsWith(".tar.bz2") || name.endsWith(".tbz2")
    }

    fun loadArchive(path: String, password: CharArray? = null) {
        val file = File(path)
        archiveFile = file
        if (!file.exists() || !file.isFile) {
            _uiState.value = ArchiveViewerUiState.Error("Archive file does not exist: $path")
            return
        }

        viewModelScope.launch {
            _uiState.value = ArchiveViewerUiState.Loading
            try {
                allEntries = when {
                    isRar(file) -> rarEngine.listEntries(file, password)
                    is7z(file) -> sevenZEngine.listEntries(file, password)
                    isTar(file) -> tarEngine.listEntries(file)
                    else -> zipEngine.listEntries(file)
                }
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
        password: CharArray? = null,
        verifyIntegrity: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        val file = archiveFile ?: return
        activeJob = viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = allEntries.size)
            val result = when {
                isRar(file) -> rarEngine.extractAll(
                    rarFile = file,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    password = password,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
                is7z(file) -> sevenZEngine.extractAll(
                    sevenZFile = file,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    password = password,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
                isTar(file) -> tarEngine.extractAll(
                    tarFile = file,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
                else -> zipEngine.extractAll(
                    zipFile = file,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
            }
            _activeProgress.value = null
            activeJob = null
            result.onSuccess { count ->
                val verifiedSuffix = if (verifyIntegrity) " (Verified ✓)" else ""
                _userMessage.emit("Extracted $count items to ${destinationDir.name}$verifiedSuffix")
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
        password: CharArray? = null,
        verifyIntegrity: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        val file = archiveFile ?: return
        val selected = _selectedEntryPaths.value
        if (selected.isEmpty()) return

        activeJob = viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = selected.size)
            val result = when {
                isRar(file) -> rarEngine.extractEntries(
                    rarFile = file,
                    entryPaths = selected,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    password = password,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
                is7z(file) -> sevenZEngine.extractEntries(
                    sevenZFile = file,
                    entryPaths = selected,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    password = password,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
                isTar(file) -> tarEngine.extractEntries(
                    tarFile = file,
                    entryPaths = selected,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
                else -> zipEngine.extractEntries(
                    zipFile = file,
                    entryPaths = selected,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
            }
            _activeProgress.value = null
            activeJob = null
            result.onSuccess { count ->
                clearSelection()
                val verifiedSuffix = if (verifyIntegrity) " (Verified ✓)" else ""
                _userMessage.emit("Extracted $count items to ${destinationDir.name}$verifiedSuffix")
                onComplete()
            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    _userMessage.emit("Extraction failed: ${error.localizedMessage}")
                }
            }
        }
    }

    fun testArchive(password: CharArray? = null) {
        val file = archiveFile ?: return
        viewModelScope.launch {
            _activeProgress.value = OperationProgress(totalFiles = allEntries.size)
            val result = when {
                isRar(file) -> rarEngine.testArchive(
                    rarFile = file,
                    password = password,
                    onProgress = { _activeProgress.value = it }
                )
                is7z(file) -> sevenZEngine.testArchive(
                    sevenZFile = file,
                    password = password,
                    onProgress = { _activeProgress.value = it }
                )
                isTar(file) -> tarEngine.testArchive(
                    tarFile = file,
                    onProgress = { _activeProgress.value = it }
                )
                else -> zipEngine.testArchive(
                    zipFile = file,
                    onProgress = { _activeProgress.value = it }
                )
            }
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

    fun loadArchiveProperties(password: CharArray? = null) {
        val file = archiveFile ?: return
        viewModelScope.launch {
            _archiveProperties.value = when {
                isRar(file) -> rarEngine.getArchiveProperties(file, password)
                is7z(file) -> sevenZEngine.getArchiveProperties(file)
                isTar(file) -> tarEngine.getArchiveProperties(file)
                else -> zipEngine.getArchiveProperties(file)
            }
        }
    }

    fun dismissArchiveProperties() {
        _archiveProperties.value = null
    }

    fun addFiles(sources: List<File>) {
        val file = archiveFile ?: return
        if (sources.isEmpty()) return

        if (isRar(file)) {
            viewModelScope.launch {
                _userMessage.emit("RAR is a read-only format in ARCA (extraction and viewing only). Creating or modifying RAR archives is not supported.")
            }
            return
        }

        if (is7z(file) || isTar(file)) {
            viewModelScope.launch {
                _userMessage.emit("Appending to existing archives is only supported for ZIP files. Please recreate the archive.")
            }
            return
        }

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
        val isRarFormat = isRar(file)
        val formatBadge = if (isRarFormat) "RAR (Extraction & Viewing only)" else null
        val isReadOnly = isRarFormat || is7z(file) || isTar(file)

        _uiState.value = ArchiveViewerUiState.Success(
            archiveName = file.name,
            currentVirtualPath = currentVirtualPath,
            breadcrumbs = breadcrumbs,
            items = directChildren,
            totalUncompressedBytes = totalUncompressed,
            totalCompressedBytes = totalCompressed,
            totalFilesCount = totalFiles,
            isReadOnly = isReadOnly,
            formatBadge = formatBadge
        )
    }
}
