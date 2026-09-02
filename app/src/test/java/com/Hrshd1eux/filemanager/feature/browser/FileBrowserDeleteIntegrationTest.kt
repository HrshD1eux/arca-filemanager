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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserDeleteIntegrationTest {

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
    fun startDelete_deletesFileAndEmitsConfirmation() = runTest {
        // Arrange
        val fileToDelete = File(rootDir, "obsolete.txt").apply { writeText("Delete me") }
        val targetNode = StorageNode(
            id = fileToDelete.absolutePath,
            name = fileToDelete.name,
            path = fileToDelete.absolutePath,
            size = fileToDelete.length(),
            lastModified = fileToDelete.lastModified(),
            isDirectory = false
        )

        var receivedMessage: String? = null
        val messageJob = launch {
            receivedMessage = viewModel.userMessage.first()
        }

        // Act
        viewModel.startDelete(
            targets = listOf(targetNode),
            hasPermission = true
        )
        advanceUntilIdle()

        // Assert
        assertFalse(fileToDelete.exists())
        assertTrue(receivedMessage?.contains("Deleted 1 items") == true)

        messageJob.cancel()
    }

    @Test
    fun startDelete_recursivelyDeletesDirectory() = runTest {
        // Arrange
        val folderToDelete = File(rootDir, "DeleteFolder").apply { mkdir() }
        File(folderToDelete, "nested.txt").writeText("Nested file")

        val targetNode = StorageNode(
            id = folderToDelete.absolutePath,
            name = folderToDelete.name,
            path = folderToDelete.absolutePath,
            size = 0L,
            lastModified = folderToDelete.lastModified(),
            isDirectory = true
        )

        // Act
        viewModel.startDelete(
            targets = listOf(targetNode),
            hasPermission = true
        )
        advanceUntilIdle()

        // Assert
        assertFalse(folderToDelete.exists())
    }

    @Test
    fun startDelete_permissionDenied_emitsPermissionRequired() {
        val targetNode = StorageNode(id = "a", name = "a", path = "a")

        viewModel.startDelete(
            targets = listOf(targetNode),
            hasPermission = false
        )

        assertTrue(viewModel.uiState.value is BrowserUiState.PermissionRequired)
    }
}
