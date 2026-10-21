package com.Hrshd1eux.filemanager.feature.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.backup.engine.BackupEngine
import com.Hrshd1eux.filemanager.core.backup.engine.BackupProgress
import com.Hrshd1eux.filemanager.core.backup.engine.BackupReport
import com.Hrshd1eux.filemanager.core.backup.model.BackupPreviewReport
import com.Hrshd1eux.filemanager.core.backup.model.BackupProfile
import com.Hrshd1eux.filemanager.core.backup.model.MirrorDeletionNotConfirmedException
import com.Hrshd1eux.filemanager.core.backup.repository.BackupProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: BackupProfileRepository = BackupProfileRepository.getInstance(application),
    private val backupEngine: BackupEngine = BackupEngine(repository = repository)
) : AndroidViewModel(application) {

    val profiles: StateFlow<List<BackupProfile>> = repository.profiles

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _isPreviewing = MutableStateFlow(false)
    val isPreviewing: StateFlow<Boolean> = _isPreviewing.asStateFlow()

    private val _progress = MutableStateFlow<BackupProgress?>(null)
    val progress: StateFlow<BackupProgress?> = _progress.asStateFlow()

    private val _previewReport = MutableStateFlow<BackupPreviewReport?>(null)
    val previewReport: StateFlow<BackupPreviewReport?> = _previewReport.asStateFlow()

    private val _lastReport = MutableStateFlow<BackupReport?>(null)
    val lastReport: StateFlow<BackupReport?> = _lastReport.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentExecutionJob: Job? = null

    fun saveProfile(profile: BackupProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            repository.deleteProfile(id)
        }
    }

    fun generatePreview(profile: BackupProfile) {
        viewModelScope.launch {
            _isPreviewing.value = true
            _errorMessage.value = null
            try {
                val preview = withContext(Dispatchers.IO) {
                    backupEngine.previewBackup(profile)
                }
                _previewReport.value = preview
            } catch (e: Exception) {
                _errorMessage.value = "Failed generating preview: ${e.localizedMessage}"
            } finally {
                _isPreviewing.value = false
            }
        }
    }

    fun executeBackup(profile: BackupProfile, confirmedMirrorDeletion: Boolean = false) {
        currentExecutionJob?.cancel()
        currentExecutionJob = viewModelScope.launch {
            _isExecuting.value = true
            _progress.value = BackupProgress()
            _errorMessage.value = null

            try {
                val report = withContext(Dispatchers.IO) {
                    backupEngine.executeBackup(
                        profile = profile,
                        confirmedMirrorDeletion = confirmedMirrorDeletion,
                        onProgress = { p ->
                            _progress.value = p
                        }
                    )
                }
                _lastReport.value = report
                _previewReport.value = null
            } catch (mde: MirrorDeletionNotConfirmedException) {
                _errorMessage.value = mde.localizedMessage
            } catch (_: CancellationException) {
                _errorMessage.value = "Backup was cancelled."
            } catch (e: Exception) {
                _errorMessage.value = "Backup error: ${e.localizedMessage}"
            } finally {
                _isExecuting.value = false
                _progress.value = null
            }
        }
    }

    fun cancelExecution() {
        currentExecutionJob?.cancel()
        _isExecuting.value = false
        _progress.value = null
    }

    fun dismissPreview() {
        _previewReport.value = null
    }

    fun dismissLastReport() {
        _lastReport.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
