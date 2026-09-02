package com.Hrshd1eux.filemanager.feature.home

import androidx.lifecycle.ViewModel
import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.recents.model.RecentItem
import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    private val favoritesRepository: FavoritesRepository = FavoritesRepository(),
    private val recentsRepository: RecentsRepository = RecentsRepository()
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteItem>> = favoritesRepository.favorites
    val recents: StateFlow<List<RecentItem>> = recentsRepository.recents

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
