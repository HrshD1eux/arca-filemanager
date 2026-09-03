package com.Hrshd1eux.filemanager.core.vault.model

import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedPayloadDto(
    val ivHex: String,
    val ciphertextHex: String
)

@Serializable
data class VaultMetadata(
    val saltHex: String,
    val verificationPayload: EncryptedPayloadDto,
    val createdAt: Long,
    val version: Int = 1
)

@Serializable
data class VaultFileEntry(
    val id: String,
    val originalName: String,
    val encryptedFileName: String = "",
    val parentVirtualPath: String = "",
    val isDirectory: Boolean = false,
    val size: Long = 0L,
    val mimeType: String = "*/*",
    val addedDate: Long = System.currentTimeMillis(),
    val lastModified: Long = addedDate
)

@Serializable
data class VaultManifest(
    val files: List<VaultFileEntry> = emptyList()
)

sealed interface VaultUiState {
    data object Loading : VaultUiState
    data object Unconfigured : VaultUiState
    data class Locked(val error: String? = null) : VaultUiState
    data class Unlocked(
        val currentVirtualPath: String = "",
        val currentEntries: List<VaultFileEntry> = emptyList(),
        val totalFilesCount: Int = 0,
        val totalSize: Long = 0L,
        val selectedIds: Set<String> = emptySet(),
        val activeProgress: OperationProgress? = null
    ) : VaultUiState
}
