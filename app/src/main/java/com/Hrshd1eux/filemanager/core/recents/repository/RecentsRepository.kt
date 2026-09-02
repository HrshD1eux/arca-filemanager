package com.Hrshd1eux.filemanager.core.recents.repository

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.recents.model.RecentItem
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecentsRepository(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("arca_recents_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _recents = MutableStateFlow<List<RecentItem>>(loadRecents())
    val recents: StateFlow<List<RecentItem>> = _recents.asStateFlow()

    fun recordAccess(node: StorageNode) {
        val current = _recents.value.filter { it.path != node.path }.toMutableList()
        val newItem = RecentItem(
            path = node.path,
            displayName = node.name.ifEmpty { node.path },
            isDirectory = node.isDirectory,
            size = node.size,
            mimeType = node.mimeType,
            accessedAt = System.currentTimeMillis()
        )
        current.add(0, newItem)

        val trimmed = if (current.size > MAX_RECENTS) current.take(MAX_RECENTS) else current
        updateRecents(trimmed)
    }

    fun removeRecent(path: String) {
        val current = _recents.value.filter { it.path != path }
        updateRecents(current)
    }

    fun clearRecents() {
        updateRecents(emptyList())
    }

    private fun updateRecents(newList: List<RecentItem>) {
        _recents.value = newList
        val encoded = runCatching { json.encodeToString(newList) }.getOrNull()
        prefs?.edit()?.putString(KEY_RECENTS_JSON, encoded)?.apply()
    }

    private fun loadRecents(): List<RecentItem> {
        if (prefs == null) return emptyList()
        val rawJson = prefs.getString(KEY_RECENTS_JSON, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<RecentItem>>(rawJson)
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_RECENTS_JSON = "recents_history_json"
        const val MAX_RECENTS = 50
    }
}
