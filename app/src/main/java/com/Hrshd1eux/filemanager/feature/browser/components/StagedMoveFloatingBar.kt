package com.Hrshd1eux.filemanager.feature.browser.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.Hrshd1eux.filemanager.core.operations.clipboard.StagedTransfer
import com.Hrshd1eux.filemanager.core.operations.clipboard.StagedTransferType
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

/**
 * Floating action bar for staged move session (1.2.5 One-Tap Move):
 * Delegates to the universal StagedTransferFloatingBar.
 */
@Composable
fun StagedMoveFloatingBar(
    stagedItems: List<StorageNode>,
    currentFolder: StorageNode?,
    onMoveHere: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    StagedTransferFloatingBar(
        transfer = StagedTransfer(
            sources = stagedItems,
            type = StagedTransferType.MOVE,
            sourceLocationName = "Storage"
        ),
        currentFolder = currentFolder,
        onCommitTransfer = onMoveHere,
        onCancel = onCancel,
        modifier = modifier
    )
}
