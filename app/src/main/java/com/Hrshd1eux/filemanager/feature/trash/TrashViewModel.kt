package com.Hrshd1eux.filemanager.feature.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.trash.manager.TrashManager
import com.Hrshd1eux.filemanager.core.trash.model.TrashEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TrashSortBy(val displayName: String) {
    DELETED_DATE_DESC("Newest Deleted"),
    DELETED_DATE_ASC("Oldest Deleted"),
    NAME_ASC("Name (A - Z)"),
    NAME_DESC("Name (Z - A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

enum class TrashViewMode {
    LIST,
    GRID
}

class TrashViewModel @JvmOverloads constructor(
    application: Application,
    private val trashManager: TrashManager = TrashManager.getInstance(application)
) : AndroidViewModel(application) {

    val entries: StateFlow<List<TrashEntry>> = trashManager.entries

    private val _sortBy = MutableStateFlow(TrashSortBy.DELETED_DATE_DESC)
    val sortBy: StateFlow<TrashSortBy> = _sortBy.asStateFlow()

    private val _viewMode = MutableStateFlow(TrashViewMode.LIST)
    val viewMode: StateFlow<TrashViewMode> = _viewMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val sortedEntries: StateFlow<List<TrashEntry>> = combine(entries, _sortBy) { list, sort ->
        when (sort) {
            TrashSortBy.DELETED_DATE_DESC -> list.sortedByDescending { it.trashedTimestampMillis }
            TrashSortBy.DELETED_DATE_ASC -> list.sortedBy { it.trashedTimestampMillis }
            TrashSortBy.NAME_ASC -> list.sortedBy { it.originalName.lowercase() }
            TrashSortBy.NAME_DESC -> list.sortedByDescending { it.originalName.lowercase() }
            TrashSortBy.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            TrashSortBy.SIZE_ASC -> list.sortedBy { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun updateSortBy(sort: TrashSortBy) {
        _sortBy.value = sort
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == TrashViewMode.LIST) TrashViewMode.GRID else TrashViewMode.LIST
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
        _selectedIds.value = entries.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun restore(entry: TrashEntry) {
        viewModelScope.launch {
            _isOperating.value = true
            trashManager.restore(entry).onSuccess {
                _selectedIds.value = _selectedIds.value - entry.id
                _userMessage.tryEmit("Restored \"${entry.originalName}\"")
            }.onFailure { err ->
                _userMessage.tryEmit("Failed to restore: ${err.localizedMessage ?: "Unknown error"}")
            }
            _isOperating.value = false
        }
    }

    fun restoreMultiple(entriesToRestore: List<TrashEntry>) {
        viewModelScope.launch {
            _isOperating.value = true
            var successCount = 0
            for (entry in entriesToRestore) {
                trashManager.restore(entry).onSuccess {
                    successCount++
                }
            }
            _selectedIds.value = emptySet()
            _userMessage.tryEmit("Restored $successCount file(s)")
            _isOperating.value = false
        }
    }

    fun shredPermanently(entry: TrashEntry) {
        viewModelScope.launch {
            _isOperating.value = true
            trashManager.shred(entry).onSuccess {
                _selectedIds.value = _selectedIds.value - entry.id
                _userMessage.tryEmit("Cryptographically shredded \"${entry.originalName}\"")
            }.onFailure { err ->
                _userMessage.tryEmit("Failed to shred: ${err.localizedMessage ?: "Unknown error"}")
            }
            _isOperating.value = false
        }
    }

    fun shredMultiple(entriesToShred: List<TrashEntry>) {
        viewModelScope.launch {
            _isOperating.value = true
            var successCount = 0
            for (entry in entriesToShred) {
                trashManager.shred(entry).onSuccess {
                    successCount++
                }
            }
            _selectedIds.value = emptySet()
            _userMessage.tryEmit("Permanently shredded $successCount file(s)")
            _isOperating.value = false
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _isOperating.value = true
            trashManager.emptyTrash().onSuccess { count ->
                _selectedIds.value = emptySet()
                _userMessage.tryEmit("Emptied trash ($count items shredded)")
            }.onFailure { err ->
                _userMessage.tryEmit("Failed to empty trash: ${err.localizedMessage ?: "Unknown error"}")
            }
            _isOperating.value = false
        }
    }
}
