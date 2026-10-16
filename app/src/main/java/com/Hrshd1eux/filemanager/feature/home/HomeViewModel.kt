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

class HomeViewModel(
    application: Application,
    private val favoritesRepository: FavoritesRepository = FavoritesRepository(),
    private val recentsRepository: RecentsRepository = RecentsRepository(),
    private val storageAnalyzer: LocalStorageAnalyzer = LocalStorageAnalyzer(),
    private val vaultLockManager: VaultLockManager = VaultLockManager.getInstance(application)
) : AndroidViewModel(application) {

    val favorites: StateFlow<List<FavoriteItem>> = favoritesRepository.favorites
    val recents: StateFlow<List<RecentItem>> = recentsRepository.recents
    val lockSettings: StateFlow<VaultLockSettings> = vaultLockManager.lockSettings

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
}
