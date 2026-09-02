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
class FileBrowserRenameAndMkdirIntegrationTest {

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
    fun rename_renamesFileSuccessfully() = runTest {
        // Arrange
        val srcFile = File(rootDir, "initial_doc.txt").apply { writeText("Rename test content") }
        val targetNode = StorageNode(
            id = srcFile.absolutePath,
            name = srcFile.name,
            path = srcFile.absolutePath,
            size = srcFile.length(),
            lastModified = srcFile.lastModified(),
            isDirectory = false
        )

        var receivedMessage: String? = null
        val messageJob = launch {
            receivedMessage = viewModel.userMessage.first()
        }

        // Act
        viewModel.rename(
            target = targetNode,
            newName = "renamed_doc.txt",
            hasPermission = true
        )
        advanceUntilIdle()

        // Assert
        assertFalse(srcFile.exists())
        val renamedFile = File(rootDir, "renamed_doc.txt")
        assertTrue(renamedFile.exists())
        assertEquals("Rename test content", renamedFile.readText())
        assertTrue(receivedMessage?.contains("Renamed to 'renamed_doc.txt'") == true)

        messageJob.cancel()
    }

    @Test
    fun createDirectory_createsFolderSuccessfully() = runTest {
        var receivedMessage: String? = null
        val messageJob = launch {
            receivedMessage = viewModel.userMessage.first()
        }

        // Act
        viewModel.createDirectory(
            parentPath = rootDir.absolutePath,
            dirName = "Work Documents",
            hasPermission = true
        )
        advanceUntilIdle()

        // Assert
        val createdDir = File(rootDir, "Work Documents")
        assertTrue(createdDir.exists())
        assertTrue(createdDir.isDirectory)
        assertTrue(receivedMessage?.contains("Created folder 'Work Documents'") == true)

        messageJob.cancel()
    }

    @Test
    fun rename_permissionDenied_emitsPermissionRequired() {
        val targetNode = StorageNode(id = "a", name = "a", path = "a")

        viewModel.rename(
            target = targetNode,
            newName = "new.txt",
            hasPermission = false
        )

        assertTrue(viewModel.uiState.value is BrowserUiState.PermissionRequired)
    }

    @Test
    fun createDirectory_permissionDenied_emitsPermissionRequired() {
        viewModel.createDirectory(
            parentPath = rootDir.absolutePath,
            dirName = "Secret",
            hasPermission = false
        )

        assertTrue(viewModel.uiState.value is BrowserUiState.PermissionRequired)
    }
}
