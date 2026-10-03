package com.Hrshd1eux.filemanager.core.operations.clipboard

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Type of transfer for staged operations.
 */
enum class StagedTransferType {
    COPY,
    MOVE
}

/**
 * Represents items staged for transfer across directories or server boundaries.
 */
data class StagedTransfer(
    val sources: List<StorageNode>,
    val type: StagedTransferType,
    val sourceLocationName: String
)

/**
 * Global clipboard / staging manager for cross-screen, cross-provider file transfers:
 * - Phone → NAS
 * - NAS → Phone
 * - Phone → Server
 * - Local → Local
 * - Remote → Remote
 */
object OperationClipboard {

    private val _stagedTransfer = MutableStateFlow<StagedTransfer?>(null)
    val stagedTransfer: StateFlow<StagedTransfer?> = _stagedTransfer.asStateFlow()

    /**
     * Stages a list of items for copy or move.
     */
    fun stage(
        sources: List<StorageNode>,
        type: StagedTransferType,
        sourceLocationName: String = "Storage"
    ) {
        if (sources.isEmpty()) return
        _stagedTransfer.value = StagedTransfer(
            sources = sources,
            type = type,
            sourceLocationName = sourceLocationName
        )
    }

    /**
     * Clears any active staged transfer.
     */
    fun clear() {
        _stagedTransfer.value = null
    }

    /**
     * Returns true if there are items currently staged for transfer.
     */
    fun hasStagedItems(): Boolean = _stagedTransfer.value != null
}
