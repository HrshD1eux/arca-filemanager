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

    private val _recentDestinations = MutableStateFlow<List<StorageNode>>(loadRecentDestinations())
    val recentDestinations: StateFlow<List<StorageNode>> = _recentDestinations.asStateFlow()

    /**
     * Records a destination used for copy, move, or transfer operations.
     * Maintains an LRU list of up to MAX_RECENT_DESTINATIONS valid directories.
     */
    fun recordDestination(node: StorageNode) {
        if (!node.isDirectory) return
        val file = File(node.path)
        if (!file.exists() || !file.isDirectory) return

        _lastMoveDestination.value = node

        val currentList = _recentDestinations.value.toMutableList()
        currentList.removeAll { it.path == node.path }
        currentList.add(0, node)

        val trimmed = if (currentList.size > MAX_RECENT_DESTINATIONS) {
            currentList.take(MAX_RECENT_DESTINATIONS)
        } else {
            currentList
        }
        _recentDestinations.value = trimmed

        persistDestinations(trimmed)
    }

    /**
     * Backward-compatible alias for recordDestination.
     */
    fun recordMoveDestination(node: StorageNode) {
        recordDestination(node)
    }

    /**
     * Removes a destination from the recent list.
     */
    fun removeDestination(path: String) {
        val updated = _recentDestinations.value.filter { it.path != path }
        _recentDestinations.value = updated
        if (_lastMoveDestination.value?.path == path) {
            _lastMoveDestination.value = updated.firstOrNull()
        }
        persistDestinations(updated)
    }

    fun clear() {
        _lastMoveDestination.value = null
        _recentDestinations.value = emptyList()
        prefs?.edit()?.clear()?.apply()
    }

    private fun persistDestinations(list: List<StorageNode>) {
        if (prefs == null) return
        val encoded = list.joinToString("\n") { "${it.path}\t${it.name}\t${it.lastModified}" }
        prefs.edit().apply {
            putString(KEY_RECENT_LIST, encoded)
            list.firstOrNull()?.let { first ->
                putString(KEY_PATH, first.path)
                putString(KEY_NAME, first.name)
                putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            } ?: run {
                remove(KEY_PATH)
                remove(KEY_NAME)
                remove(KEY_TIMESTAMP)
            }
            apply()
        }
    }

    private fun loadRecentDestinations(): List<StorageNode> {
        if (prefs == null) {
            val single = loadLastDestination()
            return if (single != null) listOf(single) else emptyList()
        }

        val raw = prefs.getString(KEY_RECENT_LIST, null)
        if (raw.isNullOrBlank()) {
            val single = loadLastDestination()
            return if (single != null) listOf(single) else emptyList()
        }

        val result = mutableListOf<StorageNode>()
        for (line in raw.lines()) {
            if (line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.isNotEmpty()) {
                val path = parts[0]
                val name = if (parts.size > 1) parts[1] else File(path).name
                val lastMod = if (parts.size > 2) parts[2].toLongOrNull() ?: 0L else 0L

                val file = File(path)
                if (file.exists() && file.isDirectory) {
                    result.add(
                        StorageNode(
                            id = path,
                            name = name,
                            path = path,
                            size = 0L,
                            lastModified = if (lastMod > 0L) lastMod else file.lastModified(),
                            isDirectory = true
                        )
                    )
                }
            }
        }
        return result
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
        const val MAX_RECENT_DESTINATIONS = 10
        private const val KEY_PATH = "last_destination_path"
        private const val KEY_NAME = "last_destination_name"
        private const val KEY_TIMESTAMP = "last_destination_timestamp"
        private const val KEY_RECENT_LIST = "recent_destinations_list"
    }
}

typealias DestinationHistoryRepository = LastDestinationRepository

