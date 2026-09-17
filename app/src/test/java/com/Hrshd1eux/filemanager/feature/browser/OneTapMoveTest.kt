package com.Hrshd1eux.filemanager.feature.browser

import com.Hrshd1eux.filemanager.core.dragdrop.DragDropPayload
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.preferences.LastDestinationRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OneTapMoveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var rootDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var provider: LocalFileSystemProvider
    private lateinit var engine: FileOperationEngine
    private lateinit var lastDestRepo: LastDestinationRepository
    private lateinit var viewModel: FileBrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("one_tap_root")
        sourceDir = File(rootDir, "Source").apply { mkdir() }
        targetDir = File(rootDir, "Target").apply { mkdir() }

        provider = LocalFileSystemProvider(
            rootDirectory = rootDir,
            displayName = "One-Tap Storage",
            ioDispatcher = testDispatcher
        )
        engine = FileOperationEngine(ioDispatcher = testDispatcher)
        lastDestRepo = LastDestinationRepository(context = null)
        viewModel = FileBrowserViewModel(
            storageProvider = provider,
            lastDestinationRepository = lastDestRepo,
            operationEngine = engine,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun lastDestinationRepository_recordAndRetrieve() {
        assertNull("Initial last destination must be null", lastDestRepo.lastMoveDestination.value)

        val targetNode = StorageNode(
            id = targetDir.absolutePath,
            name = "Target",
            path = targetDir.absolutePath,
            isDirectory = true
        )

        lastDestRepo.recordMoveDestination(targetNode)

        val recorded = lastDestRepo.lastMoveDestination.value
        assertNotNull("Recorded destination must not be null", recorded)
        assertEquals(targetDir.absolutePath, recorded?.path)
        assertEquals("Target", recorded?.name)
        assertTrue(recorded?.isDirectory == true)

        lastDestRepo.clear()
        assertNull("After clear, destination must be null", lastDestRepo.lastMoveDestination.value)
    }

    @Test
    fun lastDestinationRepository_ignoresNonExistentDirectory() {
        val phantomPath = File(rootDir, "GhostFolder").absolutePath
        val ghostNode = StorageNode(
            id = phantomPath,
            name = "GhostFolder",
            path = phantomPath,
            isDirectory = true
        )

        lastDestRepo.recordMoveDestination(ghostNode)
        assertNull("Non-existent folder must not be stored as valid destination", lastDestRepo.lastMoveDestination.value)
    }

    @Test
    fun stageNodesForMove_updatesStateAndClearsSelection() = runTest {
        val file1 = File(sourceDir, "doc1.txt").apply { writeText("Hello 1") }
        val file2 = File(sourceDir, "doc2.txt").apply { writeText("Hello 2") }

        viewModel.loadDirectory(sourceDir.absolutePath, hasPermission = true)
        advanceUntilIdle()

        val node1 = StorageNode(id = file1.absolutePath, name = "doc1.txt", path = file1.absolutePath, isDirectory = false)
        val node2 = StorageNode(id = file2.absolutePath, name = "doc2.txt", path = file2.absolutePath, isDirectory = false)

        viewModel.toggleSelection(node1.id)
        viewModel.toggleSelection(node2.id)
        assertEquals(2, viewModel.selectedNodeIds.value.size)

        viewModel.stageNodesForMove(listOf(node1, node2))

        val staged = viewModel.stagedNodesToMove.value
        assertNotNull("Staged nodes must not be null", staged)
        assertEquals(2, staged?.size)
        assertEquals("doc1.txt", staged?.get(0)?.name)
        assertEquals("doc2.txt", staged?.get(1)?.name)

        assertTrue("Selection must be cleared after staging for move", viewModel.selectedNodeIds.value.isEmpty())

        viewModel.clearStagedMove()
        assertNull("Staged nodes must be null after cancel", viewModel.stagedNodesToMove.value)
    }

    @Test
    fun commitMoveHere_executesMoveAndRecordsLastDestination() = runTest {
        val file = File(sourceDir, "staged_file.txt").apply { writeText("Move me via One-Tap") }
        val fileNode = StorageNode(
            id = file.absolutePath,
            name = "staged_file.txt",
            path = file.absolutePath,
            isDirectory = false
        )
        val targetNode = StorageNode(
            id = targetDir.absolutePath,
            name = "Target",
            path = targetDir.absolutePath,
            isDirectory = true
        )

        viewModel.stageNodesForMove(listOf(fileNode))
        assertEquals(1, viewModel.stagedNodesToMove.value?.size)

        viewModel.commitMoveHere(destination = targetNode, hasPermission = true)
        advanceUntilIdle()

        // Verify staging is cleared
        assertNull("Staged nodes must be cleared after committing move", viewModel.stagedNodesToMove.value)

        // Verify file was moved on disk
        val movedFile = File(targetDir, "staged_file.txt")
        assertTrue("Moved file must exist in target directory", movedFile.exists())
        assertEquals("Move me via One-Tap", movedFile.readText())

        // Verify last destination was recorded
        val recordedDest = lastDestRepo.lastMoveDestination.value
        assertNotNull(recordedDest)
        assertEquals(targetDir.absolutePath, recordedDest?.path)
    }

    @Test
    fun startMove_automaticallyRecordsLastDestination() = runTest {
        val file = File(sourceDir, "direct_move.txt").apply { writeText("Direct One-Tap Move") }
        val fileNode = StorageNode(
            id = file.absolutePath,
            name = "direct_move.txt",
            path = file.absolutePath,
            isDirectory = false
        )
        val targetNode = StorageNode(
            id = targetDir.absolutePath,
            name = "Target",
            path = targetDir.absolutePath,
            isDirectory = true
        )

        viewModel.startMove(
            sources = listOf(fileNode),
            destination = targetNode,
            hasPermission = true
        )
        advanceUntilIdle()

        val movedFile = File(targetDir, "direct_move.txt")
        assertTrue("Moved file must exist in target directory", movedFile.exists())

        val lastDest = lastDestRepo.lastMoveDestination.value
        assertNotNull("Direct move must record last destination", lastDest)
        assertEquals(targetDir.absolutePath, lastDest?.path)
    }

    @Test
    fun handleDroppedPayload_movesFileWhenValidDropOccurs() = runTest {
        val file = File(sourceDir, "dragged_item.txt").apply { writeText("Dragged & Dropped") }
        val targetNode = StorageNode(
            id = targetDir.absolutePath,
            name = "Target",
            path = targetDir.absolutePath,
            isDirectory = true
        )

        val payload = DragDropPayload.StorageNodes(listOf(file.absolutePath))
        viewModel.handleDroppedPayload(
            payload = payload,
            destinationFolder = targetNode,
            hasPermission = true
        )
        advanceUntilIdle()

        val movedFile = File(targetDir, "dragged_item.txt")
        assertTrue("Dragged file must be moved into target folder", movedFile.exists())
        assertEquals("Dragged & Dropped", movedFile.readText())
    }
}
