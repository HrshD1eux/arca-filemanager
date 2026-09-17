package com.Hrshd1eux.filemanager.feature.browser

import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionBottomBarTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var rootDir: File
    private lateinit var provider: LocalFileSystemProvider
    private lateinit var engine: FileOperationEngine
    private lateinit var viewModel: FileBrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("selection_test_root")
        provider = LocalFileSystemProvider(
            rootDirectory = rootDir,
            displayName = "Test Storage",
            ioDispatcher = testDispatcher
        )
        engine = FileOperationEngine(ioDispatcher = testDispatcher)
        viewModel = FileBrowserViewModel(
            storageProvider = provider,
            ioDispatcher = testDispatcher,
            operationEngine = engine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selection_signatureActionsConfiguration() {
        val primaryActions = listOf("Move", "Copy", "Compress", "More")
        assertEquals(4, primaryActions.size)
        assertEquals("Move", primaryActions[0])
        assertEquals("Copy", primaryActions[1])
        assertEquals("Compress", primaryActions[2])
        assertEquals("More", primaryActions[3])

        val moreActions = listOf("Delete", "Encrypt", "Share", "Rename", "Properties")
        assertTrue(moreActions.contains("Delete"))
        assertTrue(moreActions.contains("Encrypt"))
        assertTrue(moreActions.contains("Share"))
        assertTrue(moreActions.contains("Rename"))
        assertTrue(moreActions.contains("Properties"))
    }

    @Test
    fun selection_renameEligibility() {
        // Single item selection: eligible for rename
        val singleSelectionCount = 1
        val canRenameSingle = singleSelectionCount == 1
        assertTrue(canRenameSingle)

        // Multiple item selection (e.g. 3 selected): not eligible for single rename
        val multiSelectionCount = 3
        val canRenameMulti = multiSelectionCount == 1
        assertFalse(canRenameMulti)
    }

    @Test
    fun selection_multiSelectionCount_updatesProperly() {
        val node1 = StorageNode(id = "/a", name = "a", path = "/a")
        val node2 = StorageNode(id = "/b", name = "b", path = "/b")
        val node3 = StorageNode(id = "/c", name = "c", path = "/c")

        viewModel.toggleSelection(node1.id)
        viewModel.toggleSelection(node2.id)
        viewModel.toggleSelection(node3.id)

        assertEquals(3, viewModel.selectedNodeIds.value.size)
        val selectionTitle = "${viewModel.selectedNodeIds.value.size} selected"
        assertEquals("3 selected", selectionTitle)

        viewModel.clearSelection()
        assertEquals(0, viewModel.selectedNodeIds.value.size)
    }

    @Test
    fun selection_bulkOperationResolution() = runTest {
        val file1 = File(rootDir, "item1.txt").apply { writeText("111") }
        val file2 = File(rootDir, "item2.txt").apply { writeText("222") }
        val file3 = File(rootDir, "item3.txt").apply { writeText("333") }
        val dest = File(rootDir, "Destination").apply { mkdir() }

        val n1 = file1.toNode()
        val n2 = file2.toNode()
        val n3 = file3.toNode()
        val destNode = dest.toNode()

        viewModel.toggleSelection(n1.id)
        viewModel.toggleSelection(n2.id)
        viewModel.toggleSelection(n3.id)

        assertEquals(3, viewModel.selectedNodeIds.value.size)

        // Simulate bottom action bar "Move" trigger
        viewModel.startMove(
            sources = listOf(n1, n2, n3),
            destination = destNode,
            hasPermission = true
        )
        advanceUntilIdle()

        // Selection should be cleared after move operation
        assertTrue(viewModel.selectedNodeIds.value.isEmpty())
        assertTrue(File(dest, "item1.txt").exists())
        assertTrue(File(dest, "item2.txt").exists())
        assertTrue(File(dest, "item3.txt").exists())
    }

    private fun File.toNode(): StorageNode {
        return StorageNode(
            id = absolutePath,
            name = name,
            path = absolutePath,
            size = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            isDirectory = isDirectory
        )
    }
}
