package com.Hrshd1eux.filemanager.feature.browser

import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class FileBrowserMoveIntegrationTest {

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
    fun startMove_movesFileAndRemovesOriginal() = runTest {
        // Arrange
        val srcFile = File(rootDir, "move_me.txt").apply { writeText("Move Content") }
        val targetDir = File(rootDir, "TargetFolder").apply { mkdir() }

        val srcNode = StorageNode(
            id = srcFile.absolutePath,
            name = srcFile.name,
            path = srcFile.absolutePath,
            size = srcFile.length(),
            lastModified = srcFile.lastModified(),
            isDirectory = false
        )
        val destNode = StorageNode(
            id = targetDir.absolutePath,
            name = targetDir.name,
            path = targetDir.absolutePath,
            size = 0L,
            lastModified = targetDir.lastModified(),
            isDirectory = true
        )

        var receivedMessage: String? = null
        val messageJob = launch {
            receivedMessage = viewModel.userMessage.first()
        }

        // Act
        viewModel.startMove(
            sources = listOf(srcNode),
            destination = destNode,
            hasPermission = true
        )
        advanceUntilIdle()

        // Assert
        assertFalse(srcFile.exists())
        val movedFile = File(targetDir, "move_me.txt")
        assertTrue(movedFile.exists())
        assertEquals("Move Content", movedFile.readText())
        assertTrue(receivedMessage?.contains("Moved 1 items") == true)

        messageJob.cancel()
    }

    @Test
    fun startMove_permissionDenied_emitsPermissionRequired() {
        val srcNode = StorageNode(id = "a", name = "a", path = "a")
        val destNode = StorageNode(id = "b", name = "b", path = "b")

        viewModel.startMove(
            sources = listOf(srcNode),
            destination = destNode,
            hasPermission = false
        )

        assertTrue(viewModel.uiState.value is BrowserUiState.PermissionRequired)
    }
}
