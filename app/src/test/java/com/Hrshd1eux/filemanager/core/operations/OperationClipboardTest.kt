package com.Hrshd1eux.filemanager.core.operations

import com.Hrshd1eux.filemanager.core.operations.clipboard.OperationClipboard
import com.Hrshd1eux.filemanager.core.operations.clipboard.StagedTransferType
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OperationClipboardTest {

    @Before
    fun setUp() {
        OperationClipboard.clear()
    }

    @After
    fun tearDown() {
        OperationClipboard.clear()
    }

    @Test
    fun stage_copyOperation_updatesStateCorrectly() {
        val nodes = listOf(
            StorageNode(id = "1", name = "file1.txt", path = "/storage/file1.txt"),
            StorageNode(id = "2", name = "file2.txt", path = "/storage/file2.txt")
        )

        assertFalse(OperationClipboard.hasStagedItems())

        OperationClipboard.stage(nodes, StagedTransferType.COPY, "Phone")

        assertTrue(OperationClipboard.hasStagedItems())
        val transfer = OperationClipboard.stagedTransfer.value
        assertNotNull(transfer)
        assertEquals(2, transfer?.sources?.size)
        assertEquals(StagedTransferType.COPY, transfer?.type)
        assertEquals("Phone", transfer?.sourceLocationName)
    }

    @Test
    fun stage_moveOperation_updatesStateCorrectly() {
        val remoteNode = StorageNode(
            id = "sftp://nas/share/data.csv",
            name = "data.csv",
            path = "/share/data.csv"
        )

        OperationClipboard.stage(listOf(remoteNode), StagedTransferType.MOVE, "Synology NAS")

        val transfer = OperationClipboard.stagedTransfer.value
        assertNotNull(transfer)
        assertEquals(1, transfer?.sources?.size)
        assertEquals(StagedTransferType.MOVE, transfer?.type)
        assertEquals("Synology NAS", transfer?.sourceLocationName)
    }

    @Test
    fun clear_resetsStagedTransferToNull() {
        val node = StorageNode(id = "1", name = "item", path = "/item")
        OperationClipboard.stage(listOf(node), StagedTransferType.COPY, "Phone")
        assertTrue(OperationClipboard.hasStagedItems())

        OperationClipboard.clear()
        assertFalse(OperationClipboard.hasStagedItems())
        assertNull(OperationClipboard.stagedTransfer.value)
    }

    @Test
    fun stage_emptyList_doesNotStage() {
        OperationClipboard.stage(emptyList(), StagedTransferType.COPY, "Phone")
        assertFalse(OperationClipboard.hasStagedItems())
        assertNull(OperationClipboard.stagedTransfer.value)
    }
}
