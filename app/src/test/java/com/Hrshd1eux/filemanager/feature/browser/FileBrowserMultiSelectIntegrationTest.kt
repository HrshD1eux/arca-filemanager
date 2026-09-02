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
class FileBrowserMultiSelectIntegrationTest {

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
        rootDir = tempFolder.newFolder("arca_root")
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
    fun selectionState_toggleAndSelectAll_worksCorrectly() {
        val node1 = StorageNode(id = "1", name = "1", path = "1")
        val node2 = StorageNode(id = "2", name = "2", path = "2")
        val node3 = StorageNode(id = "3", name = "3", path = "3")
        val allNodes = listOf(node1, node2, node3)

        // Initial state
        assertTrue(viewModel.selectedNodeIds.value.isEmpty())

        // Toggle 1
        viewModel.toggleSelection(node1.id)
        assertEquals(setOf("1"), viewModel.selectedNodeIds.value)

        // Toggle 2
        viewModel.toggleSelection(node2.id)
        assertEquals(setOf("1", "2"), viewModel.selectedNodeIds.value)

        // Untoggle 1
        viewModel.toggleSelection(node1.id)
        assertEquals(setOf("2"), viewModel.selectedNodeIds.value)

        // Select All
        viewModel.selectAll(allNodes)
        assertEquals(setOf("1", "2", "3"), viewModel.selectedNodeIds.value)

        // Select All again (toggles off)
        viewModel.selectAll(allNodes)
        assertTrue(viewModel.selectedNodeIds.value.isEmpty())
    }

    @Test
    fun bulkCopy_copiesAllSelectedItems() = runTest {
        val f1 = File(rootDir, "f1.txt").apply { writeText("File 1") }
        val f2 = File(rootDir, "f2.txt").apply { writeText("File 2") }
        val destDir = File(rootDir, "Dest").apply { mkdir() }

        val node1 = f1.toNode()
        val node2 = f2.toNode()
        val destNode = destDir.toNode()

        viewModel.toggleSelection(node1.id)
        viewModel.toggleSelection(node2.id)

        viewModel.startCopy(
            sources = listOf(node1, node2),
            destination = destNode,
            hasPermission = true
        )
        advanceUntilIdle()

        // Assert selection is cleared and files copied
        assertTrue(viewModel.selectedNodeIds.value.isEmpty())
        assertTrue(File(destDir, "f1.txt").exists())
        assertTrue(File(destDir, "f2.txt").exists())
    }

    @Test
    fun bulkMove_movesAllSelectedItems() = runTest {
        val f1 = File(rootDir, "m1.txt").apply { writeText("M1") }
        val f2 = File(rootDir, "m2.txt").apply { writeText("M2") }
        val destDir = File(rootDir, "MoveDest").apply { mkdir() }

        val node1 = f1.toNode()
        val node2 = f2.toNode()
        val destNode = destDir.toNode()

        viewModel.startMove(
            sources = listOf(node1, node2),
            destination = destNode,
            hasPermission = true
        )
        advanceUntilIdle()

        assertFalse(f1.exists())
        assertFalse(f2.exists())
        assertTrue(File(destDir, "m1.txt").exists())
        assertTrue(File(destDir, "m2.txt").exists())
    }

    @Test
    fun bulkDelete_deletesAllSelectedItems() = runTest {
        val f1 = File(rootDir, "d1.txt").apply { writeText("D1") }
        val f2 = File(rootDir, "d2.txt").apply { writeText("D2") }

        val node1 = f1.toNode()
        val node2 = f2.toNode()

        viewModel.startDelete(
            targets = listOf(node1, node2),
            hasPermission = true
        )
        advanceUntilIdle()

        assertFalse(f1.exists())
        assertFalse(f2.exists())
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
