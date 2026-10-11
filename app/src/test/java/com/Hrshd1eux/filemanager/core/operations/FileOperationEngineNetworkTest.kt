package com.Hrshd1eux.filemanager.core.operations

import com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageOperator
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.SmbNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.WebDavNetworkProvider
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

/**
 * Universal Network Copy/Move Verification Suite.
 *
 * Verifies that the exact same operations engine (FileOperationEngine) powers:
 * - Phone → NAS (SMB / SFTP)
 * - NAS → Phone
 * - Phone → Server (WebDAV)
 * - Server → Phone
 * - NAS → Server (Cross-remote transfers)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileOperationEngineNetworkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var phoneStorageDir: File

    private lateinit var sftpProvider: SftpNetworkProvider
    private lateinit var smbProvider: SmbNetworkProvider
    private lateinit var webdavProvider: WebDavNetworkProvider
    private lateinit var engine: FileOperationEngine

    private val sftpNasConfig = NetworkServerConfig(
        id = "cfg-nas-sftp",
        name = "Synology SFTP",
        protocol = NetworkProtocol.SFTP,
        host = "192.168.1.100",
        port = 22,
        authType = NetworkAuthType.PASSWORD,
        username = "admin",
        password = "pwd",
        basePath = "/volume1"
    )

    private val smbNasConfig = NetworkServerConfig(
        id = "cfg-nas-smb",
        name = "Windows SMB NAS",
        protocol = NetworkProtocol.SMB,
        host = "192.168.1.10",
        port = 445,
        authType = NetworkAuthType.PASSWORD,
        username = "nasuser",
        password = "naspwd",
        shareName = "Shared"
    )

    private val webdavServerConfig = NetworkServerConfig(
        id = "cfg-server-webdav",
        name = "Cloud WebDAV Server",
        protocol = NetworkProtocol.WEBDAV,
        host = "cloud.example.local",
        port = 443,
        isImplicitTls = true,
        authType = NetworkAuthType.PASSWORD,
        username = "alice",
        password = "token123",
        basePath = "/remote.php/dav/files/alice"
    )

    @Before
    fun setUp() = runTest(testDispatcher) {
        phoneStorageDir = tempFolder.newFolder("phone_storage")

        // 1. Initialize SFTP NAS
        sftpProvider = SftpNetworkProvider(sftpNasConfig, ioDispatcher = testDispatcher)
        sftpProvider.connect()
        sftpProvider.createDirectory("/volume1/shares")
        NetworkProviderRegistry.register(sftpProvider)

        // 2. Initialize SMB NAS (192.168.1.10 / Shared)
        smbProvider = SmbNetworkProvider(smbNasConfig, ioDispatcher = testDispatcher)
        smbProvider.connect()
        NetworkProviderRegistry.register(smbProvider)

        // 3. Initialize WebDAV Server
        webdavProvider = WebDavNetworkProvider(webdavServerConfig, ioDispatcher = testDispatcher)
        webdavProvider.connect()
        webdavProvider.createDirectory("/remote.php/dav/files/alice/Vault")
        NetworkProviderRegistry.register(webdavProvider)

        val operator = NetworkStorageOperator(
            providerResolver = { configId ->
                when (configId) {
                    sftpNasConfig.id -> sftpProvider
                    smbNasConfig.id -> smbProvider
                    webdavServerConfig.id -> webdavProvider
                    else -> null
                }
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
        config: NetworkServerConfig,
        path: String,
        name: String = path.substringAfterLast('/'),
        isDirectory: Boolean = false,
        size: Long = 0L
    ): StorageNode {
        return StorageNode(
            id = "${config.protocol.scheme}://${config.id}$path",
            name = name,
            path = path,
            uriString = "${config.protocol.scheme}://${config.id}$path",
            size = size,
            isDirectory = isDirectory
        )
    }

    // =========================================================================
    // 1. Phone → NAS (SMB: 192.168.1.10 / Shared)
    // =========================================================================

    @Test
    fun copy_phoneToSmbNas_streamsLocalFileToSharedFolder() = runTest(testDispatcher) {
        val phoneFile = File(phoneStorageDir, "phone_backup.tar").apply {
            writeBytes("PHONE_TAR_ARCHIVE_DATA".toByteArray())
        }
        val smbDestDir = remoteNode(smbNasConfig, "/Shared", isDirectory = true)

        var lastProgress: OperationProgress? = null
        val result = engine.copy(
            sources = listOf(phoneFile.toNode()),
            destinationDir = smbDestDir,
            onProgress = { lastProgress = it }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Copy to SMB share must succeed", result.isSuccess)
        val opResult = result.getOrThrow() as OperationResult.Success
        assertEquals(1, opResult.filesProcessed)
        assertEquals(phoneFile.length(), opResult.bytesProcessed)

        // Verify remote file on SMB NAS inside /Shared
        assertTrue("File must exist in SMB /Shared", smbProvider.exists("/Shared/phone_backup.tar"))
        val smbBytes = smbProvider.openInputStream("/Shared/phone_backup.tar").getOrThrow().use { it.readBytes() }
        assertEquals("PHONE_TAR_ARCHIVE_DATA", String(smbBytes))

        // Phone source must still exist
        assertTrue(phoneFile.exists())
    }

    @Test
    fun move_phoneToSmbNas_transfersAndDeletesPhoneSource() = runTest(testDispatcher) {
        val phoneFile = File(phoneStorageDir, "invoice.pdf").apply {
            writeText("INVOICE-SMB-TRANSFER-1001")
        }
        val smbDestDir = remoteNode(smbNasConfig, "/Shared", isDirectory = true)

        val result = engine.move(
            sources = listOf(phoneFile.toNode()),
            destinationDir = smbDestDir
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Move to SMB NAS must succeed", result.isSuccess)
        assertTrue(smbProvider.exists("/Shared/invoice.pdf"))
        val content = smbProvider.openInputStream("/Shared/invoice.pdf").getOrThrow().use { String(it.readBytes()) }
        assertEquals("INVOICE-SMB-TRANSFER-1001", content)

        // Local source file must be purged from phone
        assertFalse("Local phone file must be deleted on move", phoneFile.exists())
    }

    // =========================================================================
    // 2. NAS → Phone (SMB → Local Phone)
    // =========================================================================

    @Test
    fun copy_smbNasToPhone_streamsSharedFileToLocalPhone() = runTest(testDispatcher) {
        val smbFilePath = "/Shared/nas_dataset.json"
        val payload = "{\"status\":\"ok\",\"records\":42}"
        smbProvider.openOutputStream(smbFilePath).getOrThrow().use {
            it.write(payload.toByteArray())
        }

        val remoteSourceNode = remoteNode(smbNasConfig, smbFilePath, name = "nas_dataset.json", size = payload.length.toLong())
        val localPhoneDest = phoneStorageDir.toNode()

        val result = engine.copy(
            sources = listOf(remoteSourceNode),
            destinationDir = localPhoneDest
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Copy from SMB NAS to Phone must succeed", result.isSuccess)
        val localFile = File(phoneStorageDir, "nas_dataset.json")
        assertTrue("Downloaded file must exist on Phone", localFile.exists())
        assertEquals(payload, localFile.readText())

        // Remote NAS source preserved
        assertTrue(smbProvider.exists(smbFilePath))
    }

    @Test
    fun move_smbNasToPhone_transfersAndDeletesNasSource() = runTest(testDispatcher) {
        val smbFilePath = "/Shared/temp_transfer.log"
        val payload = "MOVE_FROM_SMB_NAS_TO_PHONE"
        smbProvider.openOutputStream(smbFilePath).getOrThrow().use {
            it.write(payload.toByteArray())
        }

        val remoteSourceNode = remoteNode(smbNasConfig, smbFilePath, name = "temp_transfer.log", size = payload.length.toLong())
        val localPhoneDest = phoneStorageDir.toNode()

        val result = engine.move(
            sources = listOf(remoteSourceNode),
            destinationDir = localPhoneDest
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Move from SMB NAS to Phone must succeed", result.isSuccess)
        val localFile = File(phoneStorageDir, "temp_transfer.log")
        assertTrue(localFile.exists())
        assertEquals(payload, localFile.readText())

        // Remote NAS source deleted
        assertFalse("Source on SMB NAS must be deleted after move", smbProvider.exists(smbFilePath))
    }

    // =========================================================================
    // 3. Phone → Server (WebDAV / Nextcloud Server)
    // =========================================================================

    @Test
    fun copy_phoneToWebDavServer_streamsToRemoteStorage() = runTest(testDispatcher) {
        val phoneFile = File(phoneStorageDir, "presentation.pptx").apply {
            writeBytes("PPTX_SLIDES_ENCRYPTED".toByteArray())
        }
        val serverDestDir = remoteNode(webdavServerConfig, "/remote.php/dav/files/alice/Vault", isDirectory = true)

        val result = engine.copy(
            sources = listOf(phoneFile.toNode()),
            destinationDir = serverDestDir
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Copy Phone to WebDAV Server must succeed", result.isSuccess)
        assertTrue(webdavProvider.exists("/remote.php/dav/files/alice/Vault/presentation.pptx"))
        val serverBytes = webdavProvider.openInputStream("/remote.php/dav/files/alice/Vault/presentation.pptx").getOrThrow().use { it.readBytes() }
        assertEquals("PPTX_SLIDES_ENCRYPTED", String(serverBytes))
        assertTrue(phoneFile.exists())
    }

    @Test
    fun move_phoneToWebDavServer_transfersAndDeletesPhoneSource() = runTest(testDispatcher) {
        val phoneFile = File(phoneStorageDir, "notes.md").apply {
            writeText("# Confidential Meeting Notes")
        }
        val serverDestDir = remoteNode(webdavServerConfig, "/remote.php/dav/files/alice/Vault", isDirectory = true)

        val result = engine.move(
            sources = listOf(phoneFile.toNode()),
            destinationDir = serverDestDir
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Move Phone to WebDAV Server must succeed", result.isSuccess)
        assertTrue(webdavProvider.exists("/remote.php/dav/files/alice/Vault/notes.md"))
        val content = webdavProvider.openInputStream("/remote.php/dav/files/alice/Vault/notes.md").getOrThrow().use { String(it.readBytes()) }
        assertEquals("# Confidential Meeting Notes", content)
        assertFalse("Phone source must be deleted", phoneFile.exists())
    }

    // =========================================================================
    // 4. Server → Phone (WebDAV Server → Local Phone)
    // =========================================================================

    @Test
    fun copy_webDavServerToPhone_streamsToLocalStorage() = runTest(testDispatcher) {
        val serverPath = "/remote.php/dav/files/alice/Vault/report_q3.pdf"
        val payload = "PDF_FINANCIAL_REPORT_Q3"
        webdavProvider.openOutputStream(serverPath).getOrThrow().use {
            it.write(payload.toByteArray())
        }

        val remoteSource = remoteNode(webdavServerConfig, serverPath, name = "report_q3.pdf", size = payload.length.toLong())
        val localDest = phoneStorageDir.toNode()

        val result = engine.copy(
            sources = listOf(remoteSource),
            destinationDir = localDest
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        val localFile = File(phoneStorageDir, "report_q3.pdf")
        assertTrue(localFile.exists())
        assertEquals(payload, localFile.readText())
        assertTrue(webdavProvider.exists(serverPath))
    }

    // =========================================================================
    // 5. NAS → Server (Cross-Remote: SMB NAS → WebDAV Server)
    // =========================================================================

    @Test
    fun copy_nasToServer_transfersAcrossDifferentRemoteProtocols() = runTest(testDispatcher) {
        // Prepare file on SMB NAS (192.168.1.10 / Shared)
        val smbSourcePath = "/Shared/database_dump.sql"
        val dumpData = "CREATE TABLE users (id INT, name VARCHAR);"
        smbProvider.openOutputStream(smbSourcePath).getOrThrow().use {
            it.write(dumpData.toByteArray())
        }

        val smbSourceNode = remoteNode(smbNasConfig, smbSourcePath, name = "database_dump.sql", size = dumpData.length.toLong())
        val serverDestDir = remoteNode(webdavServerConfig, "/remote.php/dav/files/alice/Vault", isDirectory = true)

        // Transfer directly from NAS to Server using the SAME operation engine!
        val result = engine.copy(
            sources = listOf(smbSourceNode),
            destinationDir = serverDestDir
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Cross-protocol remote transfer (NAS -> Server) must succeed", result.isSuccess)
        val opResult = result.getOrThrow() as OperationResult.Success
        assertEquals(1, opResult.filesProcessed)

        // Verify written to WebDAV Server
        assertTrue("Dump must exist on WebDAV Server", webdavProvider.exists("/remote.php/dav/files/alice/Vault/database_dump.sql"))
        val serverBytes = webdavProvider.openInputStream("/remote.php/dav/files/alice/Vault/database_dump.sql").getOrThrow().use { it.readBytes() }
        assertEquals(dumpData, String(serverBytes))

        // Source must remain on SMB NAS
        assertTrue("Dump must still exist on SMB NAS", smbProvider.exists(smbSourcePath))
    }

    // =========================================================================
    // 6. Conflict Resolution & Directory Hierarchy
    // =========================================================================

    @Test
    fun copy_phoneToNas_withConflictRenameAuto_generatesIndexedRemoteName() = runTest(testDispatcher) {
        smbProvider.openOutputStream("/Shared/backup.zip").getOrThrow().use {
            it.write("ORIGINAL_SMB_BACKUP".toByteArray())
        }

        val localBackup = File(phoneStorageDir, "backup.zip").apply {
            writeText("NEW_PHONE_BACKUP")
        }

        val result = engine.copy(
            sources = listOf(localBackup.toNode()),
            destinationDir = remoteNode(smbNasConfig, "/Shared", isDirectory = true),
            conflictResolution = ConflictResolution.RENAME_AUTO
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertTrue(smbProvider.exists("/Shared/backup.zip"))
        assertTrue(smbProvider.exists("/Shared/backup (1).zip"))

        val autoRenamedBytes = smbProvider.openInputStream("/Shared/backup (1).zip").getOrThrow().use {
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
            destinationDir = remoteNode(smbNasConfig, "/Shared", isDirectory = true)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertTrue(smbProvider.exists("/Shared/Project"))
        assertTrue(smbProvider.exists("/Shared/Project/README.md"))
        assertTrue(smbProvider.exists("/Shared/Project/src"))
        assertTrue(smbProvider.exists("/Shared/Project/src/main.kt"))

        val remoteSrcCode = smbProvider.openInputStream("/Shared/Project/src/main.kt").getOrThrow().use {
            String(it.readBytes())
        }
        assertEquals("fun main() = println()", remoteSrcCode)
    }
}
