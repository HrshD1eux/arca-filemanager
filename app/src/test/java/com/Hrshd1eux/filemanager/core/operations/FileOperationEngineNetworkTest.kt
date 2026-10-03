package com.Hrshd1eux.filemanager.core.operations

import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.model.OperationResult
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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
class FileOperationEngineNetworkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var phoneStorageDir: File
    private lateinit var sftpProvider: SftpNetworkProvider
    private lateinit var engine: FileOperationEngine

    private val nasConfig = NetworkServerConfig(
        id = "cfg-nas",
        name = "Synology NAS",
        protocol = NetworkProtocol.SFTP,
        host = "192.168.1.100",
        port = 22,
        authType = NetworkAuthType.PASSWORD,
        username = "admin",
        password = "pwd",
        basePath = "/volume1"
    )

    @Before
    fun setUp() = runTest(testDispatcher) {
        phoneStorageDir = tempFolder.newFolder("phone_storage")
        sftpProvider = SftpNetworkProvider(nasConfig, ioDispatcher = testDispatcher)
        sftpProvider.connect()
        NetworkProviderRegistry.register(sftpProvider)

        // Pre-create remote base directory
        sftpProvider.createDirectory("/volume1/shares")

        val operator = com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageOperator(
            providerResolver = { configId ->
                if (configId == nasConfig.id) sftpProvider else null
            }
        )

        engine = FileOperationEngine(
            ioDispatcher = testDispatcher,
            remoteOperator = operator
        )
    }

    @After
    fun tearDown() = runTest(testDispatcher) {
        NetworkProviderRegistry.disconnectAll()
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

    private fun remoteNode(
        path: String,
        name: String = path.substringAfterLast('/'),
        isDirectory: Boolean = false,
        size: Long = 0L
    ): StorageNode {
        return StorageNode(
            id = "sftp://${nasConfig.id}$path",
            name = name,
            path = path,
            uriString = "sftp://${nasConfig.id}$path",
            size = size,
            isDirectory = isDirectory
        )
    }

    @Test
    fun copy_phoneToNas_streamsLocalFileToRemoteServer() = runTest(testDispatcher) {
        val localFile = File(phoneStorageDir, "phone_photo.jpg").apply {
            writeBytes("JPEG_IMAGE_DATA_12345".toByteArray())
        }
        val remoteDestDir = remoteNode("/volume1/shares", isDirectory = true)

        var lastProgress: OperationProgress? = null
        val result = engine.copy(
            sources = listOf(localFile.toNode()),
            destinationDir = remoteDestDir,
            onProgress = { lastProgress = it }
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Copy operation must succeed", result.isSuccess)
        val opResult = result.getOrThrow() as OperationResult.Success
        assertEquals(1, opResult.filesProcessed)
        assertEquals(localFile.length(), opResult.bytesProcessed)

        // Verify remote file was written on NAS
        assertTrue("Remote file must exist on NAS", sftpProvider.exists("/volume1/shares/phone_photo.jpg"))
        val remoteInStream = sftpProvider.openInputStream("/volume1/shares/phone_photo.jpg").getOrThrow()
        val remoteBytes = remoteInStream.use { it.readBytes() }
        assertEquals("JPEG_IMAGE_DATA_12345", String(remoteBytes))

        // Local source file must still exist
        assertTrue("Local source must remain intact after copy", localFile.exists())
        assertEquals(localFile.length(), lastProgress?.bytesProcessed)
    }

    @Test
    fun move_phoneToNas_transfersAndDeletesLocalSource() = runTest(testDispatcher) {
        val localFile = File(phoneStorageDir, "document_to_move.pdf").apply {
            writeText("PDF-CONTENT-MOVE-TEST")
        }
        val remoteDestDir = remoteNode("/volume1/shares", isDirectory = true)

        val result = engine.move(
            sources = listOf(localFile.toNode()),
            destinationDir = remoteDestDir
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow() as OperationResult.Success
        assertEquals(1, opResult.filesProcessed)

        // Remote file must exist on NAS
        assertTrue(sftpProvider.exists("/volume1/shares/document_to_move.pdf"))
        val remoteContent = sftpProvider.openInputStream("/volume1/shares/document_to_move.pdf").getOrThrow().use {
            String(it.readBytes())
        }
        assertEquals("PDF-CONTENT-MOVE-TEST", remoteContent)

        // Local source file must be deleted after move
        assertFalse("Local source file must be deleted on move", localFile.exists())
    }

    @Test
    fun copy_nasToPhone_streamsRemoteFileToLocalPhone() = runTest(testDispatcher) {
        // Write file onto NAS
        val nasFilePath = "/volume1/shares/nas_report.csv"
        val nasData = "id,name,role\n1,Alice,Engineer\n2,Bob,Architect"
        sftpProvider.openOutputStream(nasFilePath).getOrThrow().use {
            it.write(nasData.toByteArray())
        }

        val remoteSourceNode = remoteNode(nasFilePath, name = "nas_report.csv", size = nasData.length.toLong())
        val localDestDir = phoneStorageDir.toNode()

        val result = engine.copy(
            sources = listOf(remoteSourceNode),
            destinationDir = localDestDir
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow() as OperationResult.Success
        assertEquals(1, opResult.filesProcessed)

        // Local file on Phone must exist
        val localDownloadedFile = File(phoneStorageDir, "nas_report.csv")
        assertTrue("Downloaded file must exist on Phone", localDownloadedFile.exists())
        assertEquals(nasData, localDownloadedFile.readText())

        // Remote source must still exist on NAS
        assertTrue("Remote file must still exist after copy", sftpProvider.exists(nasFilePath))
    }

    @Test
    fun move_nasToPhone_transfersAndDeletesRemoteSource() = runTest(testDispatcher) {
        val nasFilePath = "/volume1/shares/archive_log.txt"
        val nasData = "Log entry 1\nLog entry 2"
        sftpProvider.openOutputStream(nasFilePath).getOrThrow().use {
            it.write(nasData.toByteArray())
        }

        val remoteSourceNode = remoteNode(nasFilePath, name = "archive_log.txt", size = nasData.length.toLong())
        val localDestDir = phoneStorageDir.toNode()

        val result = engine.move(
            sources = listOf(remoteSourceNode),
            destinationDir = localDestDir
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        val localDownloadedFile = File(phoneStorageDir, "archive_log.txt")
        assertTrue(localDownloadedFile.exists())
        assertEquals(nasData, localDownloadedFile.readText())

        // Remote source must be deleted from NAS
        assertFalse("Remote file must be deleted from NAS after move", sftpProvider.exists(nasFilePath))
    }

    @Test
    fun copy_phoneToNas_withConflictRenameAuto_generatesIndexedRemoteName() = runTest(testDispatcher) {
        // Pre-create existing file on NAS
        sftpProvider.openOutputStream("/volume1/shares/backup.zip").getOrThrow().use {
            it.write("ORIGINAL_NAS_BACKUP".toByteArray())
        }

        val localBackup = File(phoneStorageDir, "backup.zip").apply {
            writeText("NEW_PHONE_BACKUP")
        }

        val result = engine.copy(
            sources = listOf(localBackup.toNode()),
            destinationDir = remoteNode("/volume1/shares", isDirectory = true),
            conflictResolution = ConflictResolution.RENAME_AUTO
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        // Original NAS backup preserved
        assertTrue(sftpProvider.exists("/volume1/shares/backup.zip"))
        // Renamed auto candidate created on NAS
        assertTrue(sftpProvider.exists("/volume1/shares/backup (1).zip"))

        val autoRenamedBytes = sftpProvider.openInputStream("/volume1/shares/backup (1).zip").getOrThrow().use {
            String(it.readBytes())
        }
        assertEquals("NEW_PHONE_BACKUP", autoRenamedBytes)
    }

    @Test
    fun copy_phoneToNas_recursiveDirectory_createsRemoteFoldersAndFiles() = runTest(testDispatcher) {
        val localFolder = File(phoneStorageDir, "Project").apply { mkdir() }
        val subFolder = File(localFolder, "src").apply { mkdir() }
        File(localFolder, "README.md").writeText("# Project Docs")
        File(subFolder, "main.kt").writeText("fun main() = println()")

        val result = engine.copy(
            sources = listOf(localFolder.toNode()),
            destinationDir = remoteNode("/volume1/shares", isDirectory = true)
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertTrue(sftpProvider.exists("/volume1/shares/Project"))
        assertTrue(sftpProvider.exists("/volume1/shares/Project/README.md"))
        assertTrue(sftpProvider.exists("/volume1/shares/Project/src"))
        assertTrue(sftpProvider.exists("/volume1/shares/Project/src/main.kt"))

        val remoteSrcCode = sftpProvider.openInputStream("/volume1/shares/Project/src/main.kt").getOrThrow().use {
            String(it.readBytes())
        }
        assertEquals("fun main() = println()", remoteSrcCode)
    }

    @Test
    fun copy_nasToPhone_recursiveDirectory_createsLocalFoldersAndFiles() = runTest(testDispatcher) {
        // Create remote folder hierarchy on NAS
        sftpProvider.createDirectory("/volume1/shares/RemoteMedia")
        sftpProvider.createDirectory("/volume1/shares/RemoteMedia/Photos")
        sftpProvider.openOutputStream("/volume1/shares/RemoteMedia/Photos/pic1.raw").getOrThrow().use {
            it.write("RAW_IMAGE_PIXELS".toByteArray())
        }
        sftpProvider.openOutputStream("/volume1/shares/RemoteMedia/manifest.txt").getOrThrow().use {
            it.write("MEDIA_MANIFEST_2026".toByteArray())
        }

        val remoteFolderNode = remoteNode("/volume1/shares/RemoteMedia", name = "RemoteMedia", isDirectory = true)
        val localDest = phoneStorageDir.toNode()

        val result = engine.copy(
            sources = listOf(remoteFolderNode),
            destinationDir = localDest
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        val localDownloadedFolder = File(phoneStorageDir, "RemoteMedia")
        assertTrue(localDownloadedFolder.exists())
        assertTrue(File(localDownloadedFolder, "Photos").exists())
        assertTrue(File(localDownloadedFolder, "Photos/pic1.raw").exists())
        assertEquals("RAW_IMAGE_PIXELS", File(localDownloadedFolder, "Photos/pic1.raw").readText())
        assertEquals("MEDIA_MANIFEST_2026", File(localDownloadedFolder, "manifest.txt").readText())
    }
}
