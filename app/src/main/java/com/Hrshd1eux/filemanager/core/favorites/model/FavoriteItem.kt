package com.Hrshd1eux.filemanager.core.favorites.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteItem(
    val path: String,
    val displayName: String,
    val isDirectory: Boolean,
    val addedAt: Long = System.currentTimeMillis()
)
