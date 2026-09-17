package com.Hrshd1eux.filemanager.core.storage.preferences

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class LastDestinationRepository(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("arca_last_destination_prefs", Context.MODE_PRIVATE)

    private val _lastMoveDestination = MutableStateFlow<StorageNode?>(loadLastDestination())
    val lastMoveDestination: StateFlow<StorageNode?> = _lastMoveDestination.asStateFlow()

    fun recordMoveDestination(node: StorageNode) {
        if (!node.isDirectory) return
        val file = File(node.path)
        if (!file.exists() || !file.isDirectory) return
        _lastMoveDestination.value = node
        prefs?.edit()?.apply {
            putString(KEY_PATH, node.path)
            putString(KEY_NAME, node.name)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    fun clear() {
        _lastMoveDestination.value = null
        prefs?.edit()?.clear()?.apply()
    }

    private fun loadLastDestination(): StorageNode? {
        if (prefs == null) return null
        val path = prefs.getString(KEY_PATH, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: File(path).name

        val file = File(path)
        if (!file.exists() || !file.isDirectory) {
            return null
        }

        return StorageNode(
            id = path,
            name = name,
            path = path,
            size = 0L,
            lastModified = file.lastModified(),
            isDirectory = true
        )
    }

    companion object {
        private const val KEY_PATH = "last_destination_path"
        private const val KEY_NAME = "last_destination_name"
        private const val KEY_TIMESTAMP = "last_destination_timestamp"
    }
}
