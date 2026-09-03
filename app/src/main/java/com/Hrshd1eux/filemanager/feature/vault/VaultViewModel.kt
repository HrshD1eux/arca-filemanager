package com.Hrshd1eux.filemanager.feature.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.vault.VaultManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry
import com.Hrshd1eux.filemanager.core.vault.model.VaultUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    val vaultManager: VaultManager by lazy {
        val vaultDir = File(application.filesDir, "vault")
        VaultManager(vaultDir = vaultDir)
    }

    val vaultLockManager: VaultLockManager by lazy {
        VaultLockManager.getInstance(application, vaultManager)
    }

    private val _uiState = MutableStateFlow<VaultUiState>(VaultUiState.Loading)
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    val lockSettings: StateFlow<VaultLockSettings> = vaultLockManager.lockSettings

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var currentPath: String = ""
    private var selectedIds = mutableSetOf<String>()
    private var idleCheckJob: Job? = null

    init {
        refreshState()

        viewModelScope.launch {
            vaultLockManager.lockEvents.collect {
                lockVault()
            }
        }
    }

    fun onUserInteraction() {
        vaultLockManager.onUserInteraction()
    }

    fun updateLockSettings(settings: VaultLockSettings) {
        vaultLockManager.updateSettings(settings)
        viewModelScope.launch {
            _userMessage.emit("Vault lock settings updated")
        }
    }

    fun refreshState() {
        if (!vaultManager.isConfigured()) {
            stopIdleChecker()
            _uiState.value = VaultUiState.Unconfigured
        } else if (!vaultManager.isUnlocked()) {
            stopIdleChecker()
            _uiState.value = VaultUiState.Locked()
        } else {
            startIdleChecker()
            publishUnlockedState()
        }
    }

    private fun startIdleChecker() {
        idleCheckJob?.cancel()
        idleCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000L)
                if (vaultManager.isUnlocked()) {
                    vaultLockManager.checkIdleTimeout()
                }
            }
        }
    }

    private fun stopIdleChecker() {
        idleCheckJob?.cancel()
        idleCheckJob = null
    }

    private fun publishUnlockedState(activeProgress: OperationProgress? = null) {
        val allFiles = vaultManager.getAllFiles()
        val entriesInPath = vaultManager.getEntriesInPath(currentPath)
        _uiState.value = VaultUiState.Unlocked(
            currentVirtualPath = currentPath,
            currentEntries = entriesInPath,
            totalFilesCount = allFiles.size,
            totalSize = allFiles.filterNot { it.isDirectory }.sumOf { it.size },
            selectedIds = selectedIds.toSet(),
            activeProgress = activeProgress
        )
    }

    fun createVault(password: CharArray) {
        viewModelScope.launch {
            _uiState.value = VaultUiState.Loading
            vaultManager.createVault(password).onSuccess {
                currentPath = ""
                selectedIds.clear()
                vaultLockManager.onUserInteraction()
                startIdleChecker()
                publishUnlockedState()
                _userMessage.emit("Private Vault created successfully 🔐")
            }.onFailure { error ->
                stopIdleChecker()
                _uiState.value = VaultUiState.Unconfigured
                _userMessage.emit("Failed to create vault: ${error.localizedMessage}")
            }
        }
    }

    fun unlockVault(password: CharArray) {
        viewModelScope.launch {
            _uiState.value = VaultUiState.Loading
            vaultManager.unlock(password).onSuccess {
                currentPath = ""
                selectedIds.clear()
                vaultLockManager.onUserInteraction()
                startIdleChecker()
                publishUnlockedState()
                _userMessage.emit("Vault unlocked")
            }.onFailure { error ->
                stopIdleChecker()
                _uiState.value = VaultUiState.Locked(
                    error = error.localizedMessage ?: "Incorrect password. Please try again."
                )
            }
        }
    }

    fun lockVault() {
        stopIdleChecker()
        vaultManager.lock()
        currentPath = ""
        selectedIds.clear()
        _uiState.value = VaultUiState.Locked()
    }

    fun navigateInto(folder: VaultFileEntry) {
        if (!folder.isDirectory) return
        onUserInteraction()
        currentPath = if (currentPath.isEmpty()) folder.originalName else "$currentPath/${folder.originalName}"
        selectedIds.clear()
        publishUnlockedState()
    }

    fun navigateUp(): Boolean {
        onUserInteraction()
        if (currentPath.isEmpty()) return false
        val segments = currentPath.split("/").filter { it.isNotEmpty() }
        currentPath = if (segments.size <= 1) "" else segments.dropLast(1).joinToString("/")
        selectedIds.clear()
        publishUnlockedState()
        return true
    }

    fun navigateToBreadcrumb(index: Int) {
        onUserInteraction()
        val segments = currentPath.split("/").filter { it.isNotEmpty() }
        currentPath = if (index < 0 || index >= segments.size) "" else segments.take(index + 1).joinToString("/")
        selectedIds.clear()
        publishUnlockedState()
    }

    fun toggleSelection(id: String) {
        onUserInteraction()
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        publishUnlockedState()
    }

    fun selectAll() {
        onUserInteraction()
        val currentEntries = vaultManager.getEntriesInPath(currentPath)
        selectedIds.clear()
        selectedIds.addAll(currentEntries.map { it.id })
        publishUnlockedState()
    }

    fun clearSelection() {
        onUserInteraction()
        selectedIds.clear()
        publishUnlockedState()
    }

    fun createFolder(folderName: String) {
        onUserInteraction()
        viewModelScope.launch {
            vaultManager.createFolder(folderName, currentPath).onSuccess { folder ->
                publishUnlockedState()
                _userMessage.emit("Created folder '${folder.originalName}'")
            }.onFailure { error ->
                _userMessage.emit("Failed to create folder: ${error.localizedMessage}")
            }
        }
    }

    fun renameEntry(entryId: String, newName: String) {
        onUserInteraction()
        viewModelScope.launch {
            vaultManager.renameEntry(entryId, newName).onSuccess { entry ->
                publishUnlockedState()
                _userMessage.emit("Renamed to '${entry.originalName}'")
            }.onFailure { error ->
                _userMessage.emit("Rename failed: ${error.localizedMessage}")
            }
        }
    }

    fun moveSelected(targetVirtualPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            val ids = selectedIds.toSet()
            vaultManager.moveEntries(ids, targetVirtualPath).onSuccess {
                selectedIds.clear()
                publishUnlockedState()
                _userMessage.emit("Moved ${ids.size} item(s)")
            }.onFailure { error ->
                _userMessage.emit("Move failed: ${error.localizedMessage}")
            }
        }
    }

    fun moveEntry(entryId: String, targetVirtualPath: String) {
        onUserInteraction()
        viewModelScope.launch {
            vaultManager.moveEntries(setOf(entryId), targetVirtualPath).onSuccess {
                publishUnlockedState()
                _userMessage.emit("Moved item")
            }.onFailure { error ->
                _userMessage.emit("Move failed: ${error.localizedMessage}")
            }
        }
    }

    fun deleteSelected() {
        onUserInteraction()
        viewModelScope.launch {
            val ids = selectedIds.toSet()
            vaultManager.deleteEntries(ids).onSuccess {
                selectedIds.clear()
                publishUnlockedState()
                _userMessage.emit("Deleted ${ids.size} item(s) permanently")
            }.onFailure { error ->
                _userMessage.emit("Delete failed: ${error.localizedMessage}")
            }
        }
    }

    fun deleteEntry(entry: VaultFileEntry) {
        onUserInteraction()
        viewModelScope.launch {
            vaultManager.deleteEntries(setOf(entry.id)).onSuccess {
                publishUnlockedState()
                _userMessage.emit("Deleted '${entry.originalName}' permanently")
            }.onFailure { error ->
                _userMessage.emit("Delete failed: ${error.localizedMessage}")
            }
        }
    }

    fun importFiles(sources: List<File>, deleteOriginals: Boolean = false) {
        onUserInteraction()
        viewModelScope.launch {
            publishUnlockedState(
                activeProgress = OperationProgress(totalFiles = sources.size, currentFileName = sources.firstOrNull()?.name ?: "")
            )

            vaultManager.importFiles(
                sources = sources,
                targetVirtualPath = currentPath,
                deleteOriginals = deleteOriginals,
                onProgress = { bytesRead ->
                    publishUnlockedState(
                        activeProgress = OperationProgress(
                            bytesProcessed = bytesRead,
                            totalFiles = sources.size,
                            currentFileName = "Encrypting files into Vault..."
                        )
                    )
                }
            ).onSuccess { imported ->
                publishUnlockedState()
                _userMessage.emit("Imported ${imported.size} file(s) into Vault")
            }.onFailure { error ->
                publishUnlockedState()
                _userMessage.emit("Import failed: ${error.localizedMessage}")
            }
        }
    }

    fun exportSelected(destinationDir: File) {
        onUserInteraction()
        viewModelScope.launch {
            val ids = selectedIds.toSet()
            publishUnlockedState(
                activeProgress = OperationProgress(totalFiles = ids.size, currentFileName = "Exporting...")
            )

            vaultManager.exportEntries(
                entryIds = ids,
                destinationDir = destinationDir,
                onProgress = { bytesWritten ->
                    publishUnlockedState(
                        activeProgress = OperationProgress(
                            bytesProcessed = bytesWritten,
                            totalFiles = ids.size,
                            currentFileName = "Decrypting files to storage..."
                        )
                    )
                }
            ).onSuccess { count ->
                selectedIds.clear()
                publishUnlockedState()
                _userMessage.emit("Exported $count file(s) to ${destinationDir.name}")
            }.onFailure { error ->
                publishUnlockedState()
                _userMessage.emit("Export failed: ${error.localizedMessage}")
            }
        }
    }

    fun exportSingle(entry: VaultFileEntry, destinationDir: File) {
        onUserInteraction()
        viewModelScope.launch {
            publishUnlockedState(
                activeProgress = OperationProgress(totalFiles = 1, currentFileName = entry.originalName)
            )

            vaultManager.exportEntries(
                entryIds = setOf(entry.id),
                destinationDir = destinationDir,
                onProgress = { bytesWritten ->
                    publishUnlockedState(
                        activeProgress = OperationProgress(
                            bytesProcessed = bytesWritten,
                            totalFiles = 1,
                            currentFileName = entry.originalName
                        )
                    )
                }
            ).onSuccess {
                publishUnlockedState()
                _userMessage.emit("Exported '${entry.originalName}' to ${destinationDir.name}")
            }.onFailure { error ->
                publishUnlockedState()
                _userMessage.emit("Export failed: ${error.localizedMessage}")
            }
        }
    }
}
