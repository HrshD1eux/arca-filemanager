package com.Hrshd1eux.filemanager.core.storage.model

import kotlinx.serialization.Serializable

@Serializable
data class StorageFolderShortcut(
    val id: String,
    val name: String,
    val path: String,
    val isDefault: Boolean = false
)
