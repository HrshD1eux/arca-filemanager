package com.Hrshd1eux.filemanager.feature.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.trash.manager.TrashManager
import com.Hrshd1eux.filemanager.core.trash.model.TrashEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrashViewModel @JvmOverloads constructor(
    application: Application,
    private val trashManager: TrashManager = TrashManager.getInstance(application)
) : AndroidViewModel(application) {

    val entries: StateFlow<List<TrashEntry>> = trashManager.entries

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun restore(entry: TrashEntry) {
        viewModelScope.launch {
            _isOperating.value = true
            trashManager.restore(entry).onSuccess {
                _userMessage.tryEmit("Restored \"${entry.originalName}\"")
            }.onFailure { err ->
                _userMessage.tryEmit("Failed to restore: ${err.localizedMessage ?: "Unknown error"}")
            }
            _isOperating.value = false
        }
    }

    fun shredPermanently(entry: TrashEntry) {
        viewModelScope.launch {
            _isOperating.value = true
            trashManager.shred(entry).onSuccess {
                _userMessage.tryEmit("Cryptographically shredded \"${entry.originalName}\"")
            }.onFailure { err ->
                _userMessage.tryEmit("Failed to shred: ${err.localizedMessage ?: "Unknown error"}")
            }
            _isOperating.value = false
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _isOperating.value = true
            trashManager.emptyTrash().onSuccess { count ->
                _userMessage.tryEmit("Emptied trash ($count items shredded)")
            }.onFailure { err ->
                _userMessage.tryEmit("Failed to empty trash: ${err.localizedMessage ?: "Unknown error"}")
            }
            _isOperating.value = false
        }
    }
}
