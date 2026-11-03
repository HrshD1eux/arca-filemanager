package com.Hrshd1eux.filemanager.core.storage.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.Hrshd1eux.filemanager.core.storage.model.StorageFolderShortcut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class StorageShortcutsRepository(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("arca_storage_shortcuts_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _shortcuts = MutableStateFlow<List<StorageFolderShortcut>>(loadShortcuts())
    val shortcuts: StateFlow<List<StorageFolderShortcut>> = _shortcuts.asStateFlow()

    fun addShortcut(name: String, path: String) {
        val cleanPath = path.trim().removeSuffix("/")
        val cleanName = name.trim().ifEmpty { cleanPath.substringAfterLast('/').ifEmpty { "Folder" } }
        val current = _shortcuts.value.toMutableList()
        current.add(
            StorageFolderShortcut(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                path = cleanPath,
                isDefault = false
            )
        )
        updateShortcuts(current)
    }

    fun updateShortcut(id: String, newName: String, newPath: String) {
        val cleanPath = newPath.trim().removeSuffix("/")
        val cleanName = newName.trim().ifEmpty { cleanPath.substringAfterLast('/').ifEmpty { "Folder" } }
        val current = _shortcuts.value.map {
            if (it.id == id) {
                it.copy(name = cleanName, path = cleanPath)
            } else it
        }
        updateShortcuts(current)
    }

    fun removeShortcut(id: String) {
        val current = _shortcuts.value.filter { it.id != id }
        updateShortcuts(current)
    }

    fun resetToDefaults() {
        updateShortcuts(getDefaultShortcuts())
    }

    private fun updateShortcuts(newList: List<StorageFolderShortcut>) {
        _shortcuts.value = newList
        val encoded = runCatching { json.encodeToString(newList) }.getOrNull()
        prefs?.edit()?.putString(KEY_SHORTCUTS_JSON, encoded)?.apply()
    }

    private fun loadShortcuts(): List<StorageFolderShortcut> {
        val rawJson = prefs?.getString(KEY_SHORTCUTS_JSON, null)
        if (rawJson != null) {
            val list = runCatching {
                json.decodeFromString<List<StorageFolderShortcut>>(rawJson)
            }.getOrNull()
            if (!list.isNullOrEmpty()) return list
        }
        return getDefaultShortcuts()
    }

    private fun getDefaultShortcuts(): List<StorageFolderShortcut> {
        val downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "/storage/emulated/0/Download"
        val documentsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: "/storage/emulated/0/Documents"
        val picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.absolutePath ?: "/storage/emulated/0/Pictures"
        val moviesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.absolutePath ?: "/storage/emulated/0/Movies"

        return listOf(
            StorageFolderShortcut(id = "default_download", name = "Download", path = downloadPath, isDefault = true),
            StorageFolderShortcut(id = "default_documents", name = "Documents", path = documentsPath, isDefault = true),
            StorageFolderShortcut(id = "default_pictures", name = "Pictures", path = picturesPath, isDefault = true),
            StorageFolderShortcut(id = "default_movies", name = "Movies", path = moviesPath, isDefault = true)
        )
    }

    companion object {
        private const val KEY_SHORTCUTS_JSON = "storage_shortcuts_json"

        @Volatile
        private var instance: StorageShortcutsRepository? = null

        fun getInstance(context: Context): StorageShortcutsRepository {
            return instance ?: synchronized(this) {
                instance ?: StorageShortcutsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
