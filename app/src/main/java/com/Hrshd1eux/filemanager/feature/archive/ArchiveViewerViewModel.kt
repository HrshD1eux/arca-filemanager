package com.Hrshd1eux.filemanager.feature.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.archive.engine.ExtendedArchiveEngine
import com.Hrshd1eux.filemanager.core.archive.engine.IsoEngine
import com.Hrshd1eux.filemanager.core.archive.engine.RarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.archive.engine.TarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveEntry
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveIntegrityReport
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveProperties
import com.Hrshd1eux.filemanager.core.archive.split.SplitArchiveResolver
import com.Hrshd1eux.filemanager.core.archive.split.SplitResolution
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
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
    private val rarEngine: RarEngine = RarEngine(ioDispatcher),
    private val isoEngine: IsoEngine = IsoEngine(ioDispatcher),
    private val extendedArchiveEngine: ExtendedArchiveEngine = ExtendedArchiveEngine(ioDispatcher)
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

    private fun isExtendedArchive(file: File?): Boolean {
        val ext = file?.extension?.lowercase() ?: return false
        return ext in setOf("arj", "cpio", "deb", "ar", "a")
    }

    private fun isIso(file: File?): Boolean {
        return file?.extension?.equals("iso", ignoreCase = true) == true
    }

    private fun isRar(file: File?): Boolean {
        return file?.extension?.equals("rar", ignoreCase = true) == true
    }

    private fun is7z(file: File?): Boolean {
        if (file == null) return false
        val lower = file.name.lowercase()
        return lower.endsWith(".7z") || FileTypeResolver.is7z(lower)
    }

    private fun isTar(file: File?): Boolean {
        val name = file?.name?.lowercase() ?: return false
        return name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz") ||
                name.endsWith(".tar.xz") || name.endsWith(".txz") ||
                name.endsWith(".tar.bz2") || name.endsWith(".tbz2") ||
                name.endsWith(".tar.z") || name.endsWith(".taz")
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
                    isExtendedArchive(file) -> extendedArchiveEngine.listEntries(file)
                    isIso(file) -> isoEngine.listEntries(file)
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
                isExtendedArchive(file) -> extendedArchiveEngine.extractAll(
                    archiveFile = file,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
                isIso(file) -> isoEngine.extractAll(
                    isoFile = file,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
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
                isExtendedArchive(file) -> extendedArchiveEngine.extractEntries(
                    archiveFile = file,
                    entryPaths = selected,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
                isIso(file) -> isoEngine.extractEntries(
                    isoFile = file,
                    entryPaths = selected,
                    destinationDir = destinationDir,
                    conflictResolution = conflictResolution,
                    verifyIntegrity = verifyIntegrity,
                    onProgress = { _activeProgress.value = it }
                )
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
                isExtendedArchive(file) -> extendedArchiveEngine.testArchive(
                    archiveFile = file,
                    onProgress = { _activeProgress.value = it }
                )
                isIso(file) -> isoEngine.testArchive(
                    isoFile = file,
                    onProgress = { _activeProgress.value = it }
                )
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
                isExtendedArchive(file) -> extendedArchiveEngine.getArchiveProperties(file)
                isIso(file) -> isoEngine.getArchiveProperties(file)
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

        if (isExtendedArchive(file)) {
            val ext = file.extension.lowercase()
            val msg = if (ext == "cpio") {
                "Appending to existing CPIO archives in-place is not supported. Please recreate the archive."
            } else {
                "${ext.uppercase()} is a read-only format in ARCA (extraction and viewing only). Modifying ${ext.uppercase()} archives is not supported."
            }
            viewModelScope.launch { _userMessage.emit(msg) }
            return
        }

        if (isIso(file)) {
            viewModelScope.launch {
                _userMessage.emit("ISO is a read-only disc image format in ARCA (extraction and viewing only). Modifying disc images is not supported.")
            }
            return
        }

        if (isRar(file)) {
            viewModelScope.launch {
                _userMessage.emit("RAR is a read-only format in ARCA (extraction and viewing only). Creating or modifying RAR archives is not supported.")
            }
            return
        }

        if (FileTypeResolver.isSplitArchive(file.name)) {
            viewModelScope.launch {
                _userMessage.emit("Appending to split archives in-place is not supported. Please recreate the archive.")
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
        val resolution = SplitArchiveResolver.resolveSplitParts(file)
        val splitSuccess = resolution as? SplitResolution.Success
        val isSplit = splitSuccess?.isSplit == true
        val displayName = if (isSplit && splitSuccess != null) {
            "${splitSuccess.baseName} (${splitSuccess.parts.size} parts)"
        } else {
            file.name
        }
        val isRarFormat = isRar(file)
        val isIsoFormat = isIso(file)
        val ext = file.extension.lowercase()
        val formatBadge = when {
            isSplit && splitSuccess != null -> "7Z Split Archive (${splitSuccess.parts.size} volumes)"
            isIsoFormat -> "ISO Disc Image (Extraction & Viewing only)"
            isRarFormat -> "RAR (Extraction & Viewing only)"
            ext == "arj" -> "ARJ Archive (Extraction & Viewing only)"
            ext == "cpio" -> "CPIO Archive"
            ext == "deb" -> "Debian Package (Extraction & Viewing only)"
            ext == "ar" || ext == "a" -> "AR Archive (Extraction & Viewing only)"
            file.name.lowercase().endsWith(".tar.z") || file.name.lowercase().endsWith(".taz") -> "TAR.Z (Extraction & Viewing only)"
            else -> null
        }
        val isReadOnly = isIsoFormat || isRarFormat || is7z(file) || isTar(file) || isSplit || (isExtendedArchive(file) && ext != "cpio")

        _uiState.value = ArchiveViewerUiState.Success(
            archiveName = displayName,
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
