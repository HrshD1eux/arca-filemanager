package com.Hrshd1eux.filemanager.core.recents.model

import kotlinx.serialization.Serializable

@Serializable
data class RecentItem(
    val path: String,
    val displayName: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val mimeType: String? = null,
    val accessedAt: Long = System.currentTimeMillis()
)
