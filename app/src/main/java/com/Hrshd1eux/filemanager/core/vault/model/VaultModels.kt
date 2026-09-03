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
    val encryptedFileName: String,
    val size: Long,
    val mimeType: String,
    val addedDate: Long
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
        val files: List<VaultFileEntry>,
        val totalSize: Long,
        val activeProgress: OperationProgress? = null
    ) : VaultUiState
}
