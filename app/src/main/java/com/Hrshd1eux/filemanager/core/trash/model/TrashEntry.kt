package com.Hrshd1eux.filemanager.core.trash.model

import kotlinx.serialization.Serializable

@Serializable
data class TrashEntry(
    val id: String,
    val originalPath: String,
    val originalName: String,
    val trashedPath: String,
    val sizeBytes: Long,
    val trashedTimestampMillis: Long,
    val isDirectory: Boolean = false,
    val mimeType: String? = null
)
