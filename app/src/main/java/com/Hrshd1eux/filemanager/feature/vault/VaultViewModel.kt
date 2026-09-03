package com.Hrshd1eux.filemanager.feature.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.vault.VaultManager
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry
import com.Hrshd1eux.filemanager.core.vault.model.VaultUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    val vaultManager: VaultManager by lazy {
        val vaultDir = File(application.filesDir, "vault")
        VaultManager(vaultDir = vaultDir)
    }

    private val _uiState = MutableStateFlow<VaultUiState>(VaultUiState.Loading)
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        if (!vaultManager.isConfigured()) {
            _uiState.value = VaultUiState.Unconfigured
        } else if (!vaultManager.isUnlocked()) {
            _uiState.value = VaultUiState.Locked()
        } else {
            val files = vaultManager.getFiles()
            _uiState.value = VaultUiState.Unlocked(
                files = files,
                totalSize = files.sumOf { it.size }
            )
        }
    }

    fun createVault(password: CharArray) {
        viewModelScope.launch {
            _uiState.value = VaultUiState.Loading
            vaultManager.createVault(password).onSuccess {
                val files = vaultManager.getFiles()
                _uiState.value = VaultUiState.Unlocked(
                    files = files,
                    totalSize = files.sumOf { it.size }
                )
                _userMessage.emit("Private Vault created successfully 🔐")
            }.onFailure { error ->
                _uiState.value = VaultUiState.Unconfigured
                _userMessage.emit("Failed to create vault: ${error.localizedMessage}")
            }
        }
    }

    fun unlockVault(password: CharArray) {
        viewModelScope.launch {
            _uiState.value = VaultUiState.Loading
            vaultManager.unlock(password).onSuccess { files ->
                _uiState.value = VaultUiState.Unlocked(
                    files = files,
                    totalSize = files.sumOf { it.size }
                )
                _userMessage.emit("Vault unlocked")
            }.onFailure { error ->
                _uiState.value = VaultUiState.Locked(
                    error = error.localizedMessage ?: "Incorrect password. Please try again."
                )
            }
        }
    }

    fun lockVault() {
        vaultManager.lock()
        _uiState.value = VaultUiState.Locked()
    }

    fun importFile(sourceFile: File, deleteOriginal: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value as? VaultUiState.Unlocked ?: return@launch
            _uiState.value = current.copy(
                activeProgress = OperationProgress(totalFiles = 1, currentFileName = sourceFile.name)
            )

            vaultManager.importFile(
                sourceFile = sourceFile,
                deleteOriginal = deleteOriginal,
                onProgress = { bytesRead ->
                    _uiState.value = current.copy(
                        activeProgress = OperationProgress(
                            bytesProcessed = bytesRead,
                            totalBytes = sourceFile.length(),
                            totalFiles = 1,
                            currentFileName = sourceFile.name
                        )
                    )
                }
            ).onSuccess { entry ->
                val files = vaultManager.getFiles()
                _uiState.value = VaultUiState.Unlocked(
                    files = files,
                    totalSize = files.sumOf { it.size }
                )
                _userMessage.emit("Imported '${entry.originalName}' into Vault")
            }.onFailure { error ->
                val files = vaultManager.getFiles()
                _uiState.value = VaultUiState.Unlocked(
                    files = files,
                    totalSize = files.sumOf { it.size }
                )
                _userMessage.emit("Import failed: ${error.localizedMessage}")
            }
        }
    }

    fun exportFile(entry: VaultFileEntry, destinationDir: File) {
        viewModelScope.launch {
            val current = _uiState.value as? VaultUiState.Unlocked ?: return@launch
            _uiState.value = current.copy(
                activeProgress = OperationProgress(totalFiles = 1, currentFileName = entry.originalName)
            )

            vaultManager.exportFile(
                entry = entry,
                destinationDir = destinationDir,
                onProgress = { bytesWritten ->
                    _uiState.value = current.copy(
                        activeProgress = OperationProgress(
                            bytesProcessed = bytesWritten,
                            totalBytes = entry.size,
                            totalFiles = 1,
                            currentFileName = entry.originalName
                        )
                    )
                }
            ).onSuccess { exportedFile ->
                val files = vaultManager.getFiles()
                _uiState.value = VaultUiState.Unlocked(
                    files = files,
                    totalSize = files.sumOf { it.size }
                )
                _userMessage.emit("Exported '${exportedFile.name}' to ${destinationDir.name}")
            }.onFailure { error ->
                val files = vaultManager.getFiles()
                _uiState.value = VaultUiState.Unlocked(
                    files = files,
                    totalSize = files.sumOf { it.size }
                )
                _userMessage.emit("Export failed: ${error.localizedMessage}")
            }
        }
    }

    fun deleteFile(entry: VaultFileEntry) {
        viewModelScope.launch {
            vaultManager.deleteFile(entry).onSuccess {
                val files = vaultManager.getFiles()
                _uiState.value = VaultUiState.Unlocked(
                    files = files,
                    totalSize = files.sumOf { it.size }
                )
                _userMessage.emit("Deleted '${entry.originalName}' from Vault")
            }.onFailure { error ->
                _userMessage.emit("Delete failed: ${error.localizedMessage}")
            }
        }
    }
}
