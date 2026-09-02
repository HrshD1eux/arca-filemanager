package com.Hrshd1eux.filemanager.core.favorites.repository

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FavoritesRepository(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("arca_favorites_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _favorites = MutableStateFlow<List<FavoriteItem>>(loadFavorites())
    val favorites: StateFlow<List<FavoriteItem>> = _favorites.asStateFlow()

    fun isFavorite(path: String): Boolean {
        return _favorites.value.any { it.path == path }
    }

    fun toggleFavorite(node: StorageNode): Boolean {
        return if (isFavorite(node.path)) {
            removeFavorite(node.path)
            false
        } else {
            addFavorite(node)
            true
        }
    }

    fun addFavorite(node: StorageNode) {
        val current = _favorites.value.toMutableList()
        if (current.none { it.path == node.path }) {
            current.add(
                FavoriteItem(
                    path = node.path,
                    displayName = node.name.ifEmpty { node.path },
                    isDirectory = node.isDirectory
                )
            )
            updateFavorites(current)
        }
    }

    fun removeFavorite(path: String) {
        val current = _favorites.value.filter { it.path != path }
        updateFavorites(current)
    }

    private fun updateFavorites(newList: List<FavoriteItem>) {
        _favorites.value = newList
        val encoded = runCatching { json.encodeToString(newList) }.getOrNull()
        prefs?.edit()?.putString(KEY_FAVORITES_JSON, encoded)?.apply()
    }

    private fun loadFavorites(): List<FavoriteItem> {
        if (prefs == null) return emptyList()
        val rawJson = prefs.getString(KEY_FAVORITES_JSON, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<FavoriteItem>>(rawJson)
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_FAVORITES_JSON = "favorites_list_json"
    }
}
