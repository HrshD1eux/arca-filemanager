package com.Hrshd1eux.filemanager.feature.home

import androidx.lifecycle.ViewModel
import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    private val favoritesRepository: FavoritesRepository = FavoritesRepository()
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteItem>> = favoritesRepository.favorites

    fun removeFavorite(path: String) {
        favoritesRepository.removeFavorite(path)
    }
}
