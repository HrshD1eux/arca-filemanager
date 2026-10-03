package com.Hrshd1eux.filemanager.feature.network.browser

import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.network.credentials.SecureCredentialsStore
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepositoryImpl
import com.Hrshd1eux.filemanager.core.operations.clipboard.OperationClipboard
import com.Hrshd1eux.filemanager.core.operations.clipboard.StagedTransferType
import com.Hrshd1eux.filemanager.core.operations.queue.OperationManager
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkFileBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var credentialsStore: SecureCredentialsStore
    private lateinit var repository: NetworkConnectionRepositoryImpl

    private val defaultSftpConfig = NetworkServerConfig(
        id = "cfg-nas",
        name = "Home NAS",
        protocol = NetworkProtocol.SFTP,
        host = "192.168.1.20",
        port = 22,
        authType = NetworkAuthType.PASSWORD,
        username = "admin",
        password = "secret",
        basePath = "/data"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        OperationManager.resetInstance()
        OperationClipboard.clear()
        com.Hrshd1eux.filemanager.core.operations.engine.RemoteStorageOperatorRegistry.register(
            com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageOperator()
        )
        credentialsStore = SecureCredentialsStore(context = null)
        repository = NetworkConnectionRepositoryImpl(
            context = null,
            credentialsStore = credentialsStore,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() = runTest(testDispatcher) {
        OperationClipboard.clear()
        OperationManager.resetInstance()
        com.Hrshd1eux.filemanager.core.operations.engine.RemoteStorageOperatorRegistry.unregister()
        NetworkProviderRegistry.disconnectAll()
        Dispatchers.resetMain()
    }

    private suspend fun NetworkFileBrowserViewModel.awaitSuccess(): NetworkBrowserUiState.Success {
        testDispatcher.scheduler.advanceUntilIdle()
        return uiState.filterIsInstance<NetworkBrowserUiState.Success>().first()
    }

    @Test
    fun initialization_connectsAndLoadsRootDirectory() = runTest(testDispatcher) {
        repository.saveConnection(defaultSftpConfig)

        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )

        val success = viewModel.awaitSuccess()

        assertEquals("/data", success.currentPath)
        assertEquals("Home NAS", success.serverConfig.name)
        assertTrue("SFTP transport must be encrypted", success.isEncryptedTransport)
        assertTrue("Breadcrumbs must not be empty", success.breadcrumbs.isNotEmpty())
        assertEquals("Home NAS", success.breadcrumbs.first().name)
    }

    @Test
    fun navigationIntoDirectory_updatesPathAndBreadcrumbs() = runTest(testDispatcher) {
        val rootSftpConfig = defaultSftpConfig.copy(basePath = "/")
        repository.saveConnection(rootSftpConfig)

        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )

        viewModel.awaitSuccess()

        // Create remote subdirectory
        viewModel.createDirectory("documents")
        testDispatcher.scheduler.advanceUntilIdle()

        val subFolder = StorageNode(
            id = "/documents",
            name = "documents",
            path = "/documents",
            isDirectory = true
        )

        viewModel.navigateInto(subFolder)
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as NetworkBrowserUiState.Success

        assertEquals("/documents", state.currentPath)
        assertEquals("documents", state.currentTitle)

        // Breadcrumbs: [Home NAS (/)] > [documents (/documents)]
        assertEquals(2, state.breadcrumbs.size)
        assertEquals("Home NAS", state.breadcrumbs[0].name)
        assertEquals("documents", state.breadcrumbs[1].name)
        assertTrue(state.breadcrumbs[1].isCurrent)
    }

    @Test
    fun navigateUp_traversesToParentUntilRoot() = runTest(testDispatcher) {
        val rootSftpConfig = defaultSftpConfig.copy(basePath = "/")
        repository.saveConnection(rootSftpConfig)

        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )

        viewModel.awaitSuccess()
        viewModel.loadDirectory("/var/www/html")
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value as NetworkBrowserUiState.Success
        assertEquals("/var/www/html", state.currentPath)

        // Navigate up -> /var/www
        assertTrue(viewModel.navigateUp())
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value as NetworkBrowserUiState.Success
        assertEquals("/var/www", state.currentPath)

        // Navigate up -> /var
        assertTrue(viewModel.navigateUp())
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value as NetworkBrowserUiState.Success
        assertEquals("/var", state.currentPath)

        // Navigate up -> /
        assertTrue(viewModel.navigateUp())
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value as NetworkBrowserUiState.Success
        assertEquals("/", state.currentPath)

        // At root -> navigateUp returns false
        assertFalse(viewModel.navigateUp())
    }

    @Test
    fun crudOperations_createRenameAndDelete() = runTest(testDispatcher) {
        val rootSftpConfig = defaultSftpConfig.copy(basePath = "/")
        repository.saveConnection(rootSftpConfig)

        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )

        viewModel.awaitSuccess()

        // 1. Create directory
        viewModel.createDirectory("work_projects")
        testDispatcher.scheduler.advanceUntilIdle()
        var state = viewModel.uiState.value as NetworkBrowserUiState.Success
        val createdNode = state.items.find { it.name == "work_projects" }
        assertNotNull("Created directory must exist in items", createdNode)

        // 2. Rename directory
        viewModel.rename(createdNode!!, "archived_projects")
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value as NetworkBrowserUiState.Success
        val renamedNode = state.items.find { it.name == "archived_projects" }
        assertNotNull("Renamed directory must exist in items", renamedNode)

        // 3. Delete directory
        viewModel.delete(listOf(renamedNode!!))
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value as NetworkBrowserUiState.Success
        val deletedNode = state.items.find { it.name == "archived_projects" }
        assertEquals(null, deletedNode)
    }

    @Test
    fun downloadFile_transfersRemoteBytesToLocalTarget() = runTest(testDispatcher) {
        val rootSftpConfig = defaultSftpConfig.copy(basePath = "/")
        repository.saveConnection(rootSftpConfig)

        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )

        viewModel.awaitSuccess()

        // Create remote file via provider
        val provider = NetworkProviderRegistry.get(rootSftpConfig.id)!!
        val outStreamResult = provider.openOutputStream("/file.txt")
        outStreamResult.getOrThrow().use { out ->
            out.write("Remote file content".toByteArray())
        }

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as NetworkBrowserUiState.Success
        val remoteFile = state.items.find { it.name == "file.txt" }
        assertNotNull("Remote file must exist in items", remoteFile)

        val tempLocalDir = Files.createTempDirectory("local_downloads").toFile()
        try {
            viewModel.downloadFile(remoteFile!!, tempLocalDir)
            testDispatcher.scheduler.advanceUntilIdle()

            val localDownloaded = File(tempLocalDir, "file.txt")
            assertTrue("Downloaded file must exist locally", localDownloaded.exists())
            assertEquals("Remote file content", localDownloaded.readText())
        } finally {
            tempLocalDir.deleteRecursively()
        }
    }

    @Test
    fun searchFilter_filtersItemsCorrectly() = runTest(testDispatcher) {
        val rootSftpConfig = defaultSftpConfig.copy(basePath = "/")
        repository.saveConnection(rootSftpConfig)

        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )

        viewModel.awaitSuccess()
        viewModel.createDirectory("alpha")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.createDirectory("beta")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.createDirectory("alphabet")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSearchQuery("alph")
        var state = viewModel.uiState.value as NetworkBrowserUiState.Success
        assertEquals(2, state.items.size)
        assertTrue(state.items.all { it.name.contains("alph") })

        viewModel.setSearchQuery("")
        state = viewModel.uiState.value as NetworkBrowserUiState.Success
        assertEquals(3, state.items.size)
    }

    @Test
    fun selection_toggleAndSelectAll() = runTest(testDispatcher) {
        val rootSftpConfig = defaultSftpConfig.copy(basePath = "/")
        repository.saveConnection(rootSftpConfig)

        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )

        viewModel.awaitSuccess()
        viewModel.createDirectory("folder1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.createDirectory("folder2")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as NetworkBrowserUiState.Success

        val id1 = state.items[0].id
        val id2 = state.items[1].id

        assertEquals(0, viewModel.selectedNodeIds.value.size)

        viewModel.toggleSelection(id1)
        assertEquals(setOf(id1), viewModel.selectedNodeIds.value)

        viewModel.selectAll()
        assertEquals(setOf(id1, id2), viewModel.selectedNodeIds.value)

        viewModel.clearSelection()
        assertTrue(viewModel.selectedNodeIds.value.isEmpty())
    }

    @Test
    fun unencryptedFtp_honestlyReportsInsecureTransport() = runTest(testDispatcher) {
        val ftpConfig = NetworkServerConfig(
            id = "cfg-ftp",
            name = "Cleartext FTP",
            protocol = NetworkProtocol.FTP,
            host = "192.168.1.15",
            port = 21,
            authType = NetworkAuthType.ANONYMOUS,
            basePath = "/"
        )
        repository.saveConnection(ftpConfig)

        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-ftp",
            repository = repository,
            ioDispatcher = testDispatcher
        )

        val state = viewModel.awaitSuccess()
        assertFalse("FTP MUST be flagged as unencrypted transport", state.isEncryptedTransport)
        assertEquals("Cleartext FTP", state.serverConfig.name)
    }

    @Test
    fun stageNodesForCopy_and_stageNodesForMove_updateOperationClipboard() = runTest(testDispatcher) {
        repository.saveConnection(defaultSftpConfig)
        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )
        viewModel.awaitSuccess()

        val sampleNode = StorageNode(id = "sftp://cfg-nas/data/file1.txt", name = "file1.txt", path = "/data/file1.txt")

        viewModel.stageNodesForCopy(listOf(sampleNode))
        assertEquals(StagedTransferType.COPY, OperationClipboard.stagedTransfer.value?.type)
        assertEquals("Home NAS", OperationClipboard.stagedTransfer.value?.sourceLocationName)
        assertEquals(1, OperationClipboard.stagedTransfer.value?.sources?.size)

        viewModel.stageNodesForMove(listOf(sampleNode))
        assertEquals(StagedTransferType.MOVE, OperationClipboard.stagedTransfer.value?.type)
    }

    @Test
    fun commitStagedTransfer_fromPhoneToRemote_executesCopySuccessfully() = runTest(testDispatcher) {
        repository.saveConnection(defaultSftpConfig)
        val viewModel = NetworkFileBrowserViewModel(
            connectionId = "cfg-nas",
            repository = repository,
            ioDispatcher = testDispatcher
        )
        viewModel.awaitSuccess()

        val localTempFile = File.createTempFile("phone_upload", ".txt").apply {
            writeText("UPLOADED_FROM_PHONE_TO_NAS")
            deleteOnExit()
        }
        val localNode = StorageNode(
            id = localTempFile.absolutePath,
            name = localTempFile.name,
            path = localTempFile.absolutePath,
            size = localTempFile.length()
        )

        OperationClipboard.stage(listOf(localNode), StagedTransferType.COPY, "Phone")

        viewModel.commitStagedTransfer()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(OperationClipboard.hasStagedItems())

        val successState = viewModel.awaitSuccess()
        val found = successState.items.find { it.name == localTempFile.name }
        assertNotNull("Uploaded file must appear in remote file listing", found)
    }
}
