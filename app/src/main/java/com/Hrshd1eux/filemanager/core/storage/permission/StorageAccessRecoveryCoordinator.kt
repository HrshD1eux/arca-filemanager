package com.Hrshd1eux.filemanager.core.storage.permission

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Events emitted when storage access permission state changes (Milestones 1.5.1 & 1.5.3).
 */
sealed class StorageRecoveryEvent {
    object AccessGranted : StorageRecoveryEvent()
    object AccessRevoked : StorageRecoveryEvent()
    data class ScopedFolderAdded(val treeUri: String) : StorageRecoveryEvent()
}

/**
 * Coordinator for storage access detection, lifecycle recovery, and path restoration (Milestone 1.5.3).
 *
 * Capabilities:
 * - Detects transition from denied to granted on app resume without requiring manual refresh.
 * - Detects background revocation by system or user, gracefully switching to safe limited mode.
 * - Remembers pending navigation path so the user is immediately returned to their intended directory upon granting access.
 */
class StorageAccessRecoveryCoordinator {

    private val _accessState = MutableStateFlow<StorageAccessState?>(null)
    val accessState: StateFlow<StorageAccessState?> = _accessState.asStateFlow()

    private val _recoveryEvents = MutableSharedFlow<StorageRecoveryEvent>()
    val recoveryEvents: SharedFlow<StorageRecoveryEvent> = _recoveryEvents.asSharedFlow()

    // Preserved navigation path before permission redirection
    private var pendingResumePath: String? = null

    /**
     * Records intended directory before sending user to System Settings.
     */
    fun setPendingResumePath(path: String?) {
        pendingResumePath = path
    }

    /**
     * Consumes and clears the pending resume path upon successful access restoration.
     */
    fun consumePendingResumePath(): String? {
        val path = pendingResumePath
        pendingResumePath = null
        return path
    }

    /**
     * Evaluates current platform permission state and emits events if transitions occurred.
     */
    suspend fun syncPermissionState(context: Context): StorageAccessState {
        val previous = _accessState.value
        val current = StoragePermissionManager.getStorageAccessState(context)
        _accessState.value = current

        if (previous != null) {
            val wasFull = previous.hasAllFilesAccess
            val isNowFull = current.hasAllFilesAccess

            if (!wasFull && isNowFull) {
                _recoveryEvents.emit(StorageRecoveryEvent.AccessGranted)
            } else if (wasFull && !isNowFull) {
                _recoveryEvents.emit(StorageRecoveryEvent.AccessRevoked)
            }
        }

        return current
    }

    companion object {
        @Volatile
        private var INSTANCE: StorageAccessRecoveryCoordinator? = null

        fun getInstance(): StorageAccessRecoveryCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StorageAccessRecoveryCoordinator().also { INSTANCE = it }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
