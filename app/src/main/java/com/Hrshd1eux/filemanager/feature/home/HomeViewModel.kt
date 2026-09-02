package com.Hrshd1eux.filemanager.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.recents.model.RecentItem
import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import com.Hrshd1eux.filemanager.core.storage.analyzer.LocalStorageAnalyzer
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val favoritesRepository: FavoritesRepository = FavoritesRepository(),
    private val recentsRepository: RecentsRepository = RecentsRepository(),
    private val storageAnalyzer: LocalStorageAnalyzer = LocalStorageAnalyzer()
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteItem>> = favoritesRepository.favorites
    val recents: StateFlow<List<RecentItem>> = recentsRepository.recents

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
