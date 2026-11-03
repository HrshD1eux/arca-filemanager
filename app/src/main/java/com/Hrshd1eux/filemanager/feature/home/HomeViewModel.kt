package com.Hrshd1eux.filemanager.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.recents.model.RecentItem
import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import com.Hrshd1eux.filemanager.core.storage.analyzer.LocalStorageAnalyzer
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val favoritesRepository: FavoritesRepository = FavoritesRepository(),
    private val recentsRepository: RecentsRepository = RecentsRepository.getInstance(application),
    private val storageAnalyzer: LocalStorageAnalyzer = LocalStorageAnalyzer(),
    private val vaultLockManager: VaultLockManager = VaultLockManager.getInstance(application),
    private val appSettingsRepository: com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository = com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository.getInstance(application),
    private val storageShortcutsRepository: com.Hrshd1eux.filemanager.core.storage.preferences.StorageShortcutsRepository = com.Hrshd1eux.filemanager.core.storage.preferences.StorageShortcutsRepository.getInstance(application)
) : AndroidViewModel(application) {

    val favorites: StateFlow<List<FavoriteItem>> = favoritesRepository.favorites
    val recents: StateFlow<List<RecentItem>> = recentsRepository.recents
    val lockSettings: StateFlow<VaultLockSettings> = vaultLockManager.lockSettings
    val firstRunExplanationCompleted: StateFlow<Boolean> = appSettingsRepository.firstRunExplanationCompleted
    val storageShortcuts: StateFlow<List<com.Hrshd1eux.filemanager.core.storage.model.StorageFolderShortcut>> = storageShortcutsRepository.shortcuts
    val isVaultConfigured: Boolean
        get() = com.Hrshd1eux.filemanager.core.vault.VaultManager(java.io.File(getApplication<Application>().filesDir, "vault")).isConfigured()

    private val _storageBreakdown = MutableStateFlow(StorageBreakdown(isCalculating = true))
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    init {
        refreshStorageBreakdown()
    }

    fun refreshStorageBreakdown() {
        viewModelScope.launch {
            _storageBreakdown.value = _storageBreakdown.value.copy(isCalculating = true)
            val result = storageAnalyzer.analyzeStorage()
            _storageBreakdown.value = result
        }
    }

    fun removeFavorite(path: String) {
        favoritesRepository.removeFavorite(path)
    }

    fun clearRecents() {
        recentsRepository.clearRecents()
    }

    fun removeRecent(path: String) {
        recentsRepository.removeRecent(path)
    }

    fun setFirstRunExplanationCompleted(completed: Boolean = true) {
        appSettingsRepository.setFirstRunExplanationCompleted(completed)
    }

    fun addStorageShortcut(name: String, path: String) {
        storageShortcutsRepository.addShortcut(name, path)
    }

    fun updateStorageShortcut(id: String, name: String, path: String) {
        storageShortcutsRepository.updateShortcut(id, name, path)
    }

    fun removeStorageShortcut(id: String) {
        storageShortcutsRepository.removeShortcut(id)
    }

    fun resetStorageShortcuts() {
        storageShortcutsRepository.resetToDefaults()
    }
}
