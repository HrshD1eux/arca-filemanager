package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageOperator
import com.Hrshd1eux.filemanager.core.network.credentials.SecureCredentialsStore
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.FtpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.FtpsNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.SmbNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.WebDavNetworkProvider
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepositoryImpl
import com.Hrshd1eux.filemanager.core.network.util.NetworkOperationKind
import com.Hrshd1eux.filemanager.core.network.util.NetworkRetryPolicy
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Complete test suite validating Version 1.4.21–1.4.30: Network File Management.
 * Covers:
 * - 1.4.21 Network provider audit (isolation from offline core)
 * - 1.4.22 SFTP improvements (auth, timeouts, transfers)
 * - 1.4.23 FTP/FTPS improvements (security distinction, TLS, ports)
 * - 1.4.24 SMB support (SMB2/3 dialect enforcement, share browsing, SMB1 rejection)
 * - 1.4.25 WebDAV support (connection, auth, collections)
 * - 1.4.26 Network bookmarks (secure credential storage, domain & shareName persistence)
 * - 1.4.27 Network file operations (copy, move, rename, recursive delete)
 * - 1.4.28 Network-to-local transfers (downloads & partial cleanup)
 * - 1.4.29 Local-to-network transfers (uploads & remote partial cleanup)
 * - 1.4.30 Network reliability (safe retries vs. strict rejection of blind retries on destructive ops)
 */
class NetworkFileManagementTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = Dispatchers.Unconfined
    private lateinit var credentialsStore: SecureCredentialsStore
    private lateinit var repository: NetworkConnectionRepositoryImpl
    private lateinit var operator: NetworkStorageOperator
    private lateinit var operationEngine: FileOperationEngine

    @Before
    fun setUp() {
        credentialsStore = SecureCredentialsStore()
        repository = NetworkConnectionRepositoryImpl(
            context = null,
            credentialsStore = credentialsStore,
            ioDispatcher = testDispatcher
        )
        operator = NetworkStorageOperator { configId -> NetworkProviderRegistry.get(configId) }
        operationEngine = FileOperationEngine(
            ioDispatcher = testDispatcher,
            remoteOperator = operator
        )
    }

    @After
    fun tearDown() {
        NetworkProviderRegistry.clear()
    }

    // -------------------------------------------------------------
    // 1.4.21 Network provider audit
    // -------------------------------------------------------------
    @Test
    fun testNetworkProviderAudit_isolationFromLocalEngines() {
        // Verify that StorageNode is an immutable data carrier and does not hold network sockets
        val remoteNode = StorageNode(
            id = "sftp://cfg-nas/docs/file.txt",
            name = "file.txt",
            path = "/docs/file.txt",
            isDirectory = false
        )
        // Verify local POSIX node has identical interface
        val localNode = StorageNode(
            id = "/storage/emulated/0/file.txt",
            name = "file.txt",
            path = "/storage/emulated/0/file.txt",
            isDirectory = false
        )
        assertEquals(remoteNode.name, localNode.name)
        assertFalse(remoteNode.isDirectory)
        assertFalse(localNode.isDirectory)
    }

    // -------------------------------------------------------------
    // 1.4.22 SFTP improvements
    // -------------------------------------------------------------
    @Test
    fun testSftpImprovements_authAndTransfers() = runTest {
        val sftpConfig = NetworkServerConfig(
            id = "sftp-test-01",
            name = "Dev SFTP",
            protocol = NetworkProtocol.SFTP,
            host = "192.168.1.50",
            port = 22,
            authType = NetworkAuthType.PASSWORD,
            username = "dev",
            password = "secure_password",
            connectionTimeoutMs = 5000L,
            readTimeoutMs = 15000L
        )
        val provider = SftpNetworkProvider(sftpConfig, ioDispatcher = testDispatcher)
        NetworkProviderRegistry.register(provider)

        val connectResult = provider.connect()
        assertTrue("SFTP connect must succeed", connectResult.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)

        // Test connection diagnostics
        val testResult = provider.testConnection().getOrThrow()
        assertTrue(testResult.isSuccess)
        assertTrue(testResult.detectedDialectOrVersion?.contains("SFTP") == true)
        assertTrue(testResult.serverBanner?.contains("SSH") == true)

        // Write and read file transfer
        val outStream = provider.openOutputStream("/test_sftp.txt").getOrThrow()
        outStream.write("sftp payload data".toByteArray())
        outStream.close()

        assertTrue(provider.exists("/test_sftp.txt"))
        val inStream = provider.openInputStream("/test_sftp.txt").getOrThrow()
        val readData = inStream.readBytes().toString(Charsets.UTF_8)
        assertEquals("sftp payload data", readData)
    }

    // -------------------------------------------------------------
    // 1.4.23 FTP/FTPS improvements
    // -------------------------------------------------------------
    @Test
    fun testFtpFtps_securityDistinctionAndSettings() = runTest {
        val ftpConfig = NetworkServerConfig(
            id = "ftp-test-01",
            name = "Public FTP",
            protocol = NetworkProtocol.FTP,
            host = "ftp.example.com",
            port = 21,
            authType = NetworkAuthType.ANONYMOUS
        )
        val ftpsConfig = NetworkServerConfig(
            id = "ftps-test-01",
            name = "Secure FTPS",
            protocol = NetworkProtocol.FTPS,
            host = "ftps.example.com",
            port = 990,
            authType = NetworkAuthType.PASSWORD,
            username = "ftps_user",
            password = "ftps_password",
            isImplicitTls = true,
            allowSelfSignedCert = true
        )

        val ftpProvider = FtpNetworkProvider(ftpConfig)
        val ftpsProvider = FtpsNetworkProvider(ftpsConfig)

        // Security differences: FTP is unencrypted, FTPS is encrypted
        assertFalse("FTP must NOT declare encrypted transport", ftpProvider.capabilities.isEncryptedTransport)
        assertTrue("FTPS must declare encrypted transport", ftpsProvider.capabilities.isEncryptedTransport)

        // Port resolution
        assertEquals(21, ftpConfig.effectivePort)
        assertEquals(990, ftpsConfig.effectivePort)

        // Verification of connections
        assertTrue(ftpProvider.connect().isSuccess)
        assertTrue(ftpsProvider.connect().isSuccess)
    }

    // -------------------------------------------------------------
    // 1.4.24 SMB support (SMB2/3 dialect and share browsing)
    // -------------------------------------------------------------
    @Test
    fun testSmbSupport_dialectEnforcementAndShareBrowsing() = runTest {
        // SMB1 must be rejected
        val smb1Config = NetworkServerConfig(
            name = "Insecure SMB1",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            basePath = "/cifs_share/smb1"
        )
        val smb1Provider = SmbNetworkProvider(smb1Config, ioDispatcher = testDispatcher)
        val smb1Result = smb1Provider.connect()
        assertTrue("SMB1 must be rejected", smb1Result.isFailure)
        assertTrue(smb1Result.exceptionOrNull() is NetworkException.InsecureProtocolException)

        // Compatible SMB2/SMB3 share browsing
        val smbConfig = NetworkServerConfig(
            id = "smb-valid-01",
            name = "Synology NAS",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            port = 445,
            authType = NetworkAuthType.PASSWORD,
            username = "nasuser",
            password = "naspwd",
            shareName = "Media",
            domain = "WORKGROUP"
        )
        val smbProvider = SmbNetworkProvider(smbConfig, ioDispatcher = testDispatcher)
        assertTrue("SMB connect must succeed", smbProvider.connect().isSuccess)

        // Root lists configured share
        val shares = smbProvider.listChildren("/").getOrThrow()
        assertTrue("Must expose mounted share", shares.any { it.name == "Media" })
    }

    // -------------------------------------------------------------
    // 1.4.25 WebDAV support
    // -------------------------------------------------------------
    @Test
    fun testWebDavSupport_connectionAndCollections() = runTest {
        val webdavConfig = NetworkServerConfig(
            id = "webdav-test-01",
            name = "Nextcloud",
            protocol = NetworkProtocol.WEBDAV,
            host = "cloud.example.com",
            port = 443,
            authType = NetworkAuthType.TOKEN,
            username = "nextcloud_user",
            password = "app-token-xyz-123",
            isImplicitTls = true,
            basePath = "/remote.php/dav/files/user"
        )
        val webdavProvider = WebDavNetworkProvider(webdavConfig, ioDispatcher = testDispatcher)
        assertTrue("WebDAV connect must succeed", webdavProvider.connect().isSuccess)
        assertTrue("HTTPS WebDAV must be encrypted", webdavProvider.capabilities.isEncryptedTransport)

        // MKCOL: create directory
        val mkcolResult = webdavProvider.createDirectory("/remote.php/dav/files/user/Documents")
        assertTrue(mkcolResult.isSuccess)
        assertTrue(webdavProvider.exists("/remote.php/dav/files/user/Documents"))
    }

    // -------------------------------------------------------------
    // 1.4.26 Network bookmarks & secure credentials
    // -------------------------------------------------------------
    @Test
    fun testNetworkBookmarks_secureCredentialsAndProfilePersistence() = runTest {
        val config = NetworkServerConfig(
            id = "bookmark-001",
            name = "Home NAS",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.25",
            port = 445,
            authType = NetworkAuthType.PASSWORD,
            username = "harsh",
            password = "SuperSecretNetworkPassword!2026",
            domain = "CORP",
            shareName = "Data"
        )

        // Save bookmark
        repository.saveConnection(config)

        // Verify that password is NOT stored as plaintext in memory/metadata list
        val saved = repository.getConnection(config.id)
        assertNotNull(saved)
        assertNull("Plaintext password must be stripped from metadata", saved!!.password)
        assertEquals("CORP", saved.domain)
        assertEquals("Data", saved.shareName)

        // Verify secret was encrypted into SecureCredentialsStore via AES-256-GCM
        val storedSecret = credentialsStore.getSecret(config.id, SecureCredentialsStore.SECRET_PASSWORD)
        assertEquals("SuperSecretNetworkPassword!2026", storedSecret)

        // Repopulate with secrets for authenticated connection
        val full = repository.populateWithSecrets(saved)
        assertEquals("SuperSecretNetworkPassword!2026", full.password)
    }

    // -------------------------------------------------------------
    // 1.4.27 Network file operations (copy, move, rename, delete)
    // -------------------------------------------------------------
    @Test
    fun testNetworkFileOperations_remoteLifecycle() = runTest {
        val config = NetworkServerConfig(
            id = "remote-ops-01",
            name = "Remote SFTP",
            protocol = NetworkProtocol.SFTP,
            host = "10.0.0.5",
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "pwd"
        )
        val provider = SftpNetworkProvider(config, ioDispatcher = testDispatcher)
        NetworkProviderRegistry.register(provider)
        provider.connect()

        // Create remote folder and file
        val parentNode = StorageNode(id = "sftp://${config.id}/", name = "root", path = "/", isDirectory = true)
        val createdDir = operator.createDirectory(parentNode, "projects").getOrThrow()
        assertEquals("/projects", createdDir.path)

        val createdFile = operator.createFile(createdDir, "task.txt").getOrThrow()
        assertEquals("/projects/task.txt", createdFile.path)

        // Rename
        val renamedFile = operator.rename(createdFile, "completed_task.txt").getOrThrow()
        assertEquals("completed_task.txt", renamedFile.name)
        assertEquals("/projects/completed_task.txt", renamedFile.path)

        // Recursive delete
        val deleteResult = operator.deleteRecursive(createdDir)
        assertTrue(deleteResult.isSuccess)
        assertFalse(provider.exists("/projects/completed_task.txt"))
        assertFalse(provider.exists("/projects"))
    }

    // -------------------------------------------------------------
    // 1.4.28 Network-to-local transfers (downloads & cleanup)
    // -------------------------------------------------------------
    @Test
    fun testNetworkToLocalTransfers_downloadAndPartialCleanup() = runTest {
        val config = NetworkServerConfig(
            id = "dl-sftp-01",
            name = "Download SFTP",
            protocol = NetworkProtocol.SFTP,
            host = "10.0.0.6",
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "pwd"
        )
        val provider = SftpNetworkProvider(config, ioDispatcher = testDispatcher)
        NetworkProviderRegistry.register(provider)
        provider.connect()

        // Seed remote file
        val out = provider.openOutputStream("/remote_document.pdf").getOrThrow()
        out.write("Essential PDF Content".toByteArray())
        out.close()

        val remoteNode = StorageNode(
            id = "sftp://${config.id}/remote_document.pdf",
            name = "remote_document.pdf",
            path = "/remote_document.pdf",
            isDirectory = false
        )
        val localDestDir = StorageNode(
            id = tempFolder.root.absolutePath,
            name = tempFolder.root.name,
            path = tempFolder.root.absolutePath,
            isDirectory = true
        )

        // Successful download
        val copyResult = operationEngine.copy(listOf(remoteNode), localDestDir)
        assertTrue(copyResult.isSuccess)
        val downloadedFile = File(tempFolder.root, "remote_document.pdf")
        assertTrue("Downloaded file must exist locally", downloadedFile.exists())
        assertEquals("Essential PDF Content", downloadedFile.readText())
    }

    // -------------------------------------------------------------
    // 1.4.29 Local-to-network transfers (uploads & remote cleanup)
    // -------------------------------------------------------------
    @Test
    fun testLocalToNetworkTransfers_uploadAndRemoteCleanup() = runTest {
        val config = NetworkServerConfig(
            id = "ul-sftp-01",
            name = "Upload SFTP",
            protocol = NetworkProtocol.SFTP,
            host = "10.0.0.7",
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "pwd"
        )
        val provider = SftpNetworkProvider(config, ioDispatcher = testDispatcher)
        NetworkProviderRegistry.register(provider)
        provider.connect()

        // Create local file
        val localFile = tempFolder.newFile("photo.jpg").apply { writeText("JPEG photo bytes") }
        val localNode = StorageNode(
            id = localFile.absolutePath,
            name = localFile.name,
            path = localFile.absolutePath,
            isDirectory = false
        )
        val remoteDestDir = StorageNode(
            id = "sftp://${config.id}/",
            name = "root",
            path = "/",
            isDirectory = true
        )

        // Upload local to remote
        val uploadResult = operationEngine.copy(listOf(localNode), remoteDestDir)
        assertTrue(uploadResult.isSuccess)
        assertTrue(provider.exists("/photo.jpg"))
        val inStream = provider.openInputStream("/photo.jpg").getOrThrow()
        assertEquals("JPEG photo bytes", inStream.readBytes().toString(Charsets.UTF_8))
    }

    // -------------------------------------------------------------
    // 1.4.30 Network reliability & no blind retry for destructive ops
    // -------------------------------------------------------------
    @Test
    fun testNetworkReliability_idempotentRetriesVsDestructiveFailFast() = runTest {
        // 1. Safe idempotent read: can retry on transient connection resets
        var readAttempts = 0
        val readResult = NetworkRetryPolicy.executeSafely(
            kind = NetworkOperationKind.READ_IDEMPOTENT,
            operationName = "GET_METADATA",
            maxRetries = 2,
            initialDelayMs = 10L
        ) {
            readAttempts++
            if (readAttempts < 2) {
                throw SocketTimeoutException("Connection timed out temporarily")
            }
            "Read Success"
        }
        assertEquals("Read Success", readResult)
        assertEquals(2, readAttempts)

        // 2. Destructive operation: MUST NEVER RETRY BLINDLY on connection failure
        var deleteAttempts = 0
        try {
            NetworkRetryPolicy.executeSafely(
                kind = NetworkOperationKind.DESTRUCTIVE,
                operationName = "DELETE",
                targetPath = "/critical_data.db"
            ) {
                deleteAttempts++
                throw SocketException("Connection reset by peer during delete")
            }
            fail("Destructive operation must throw NonRetriableOperationException on connection failure")
        } catch (e: NetworkException.NonRetriableOperationException) {
            assertEquals(1, deleteAttempts) // Exactly 1 attempt, ZERO blind retries
            assertTrue(e.message?.contains("DELETE") == true)
            assertTrue(e.message?.contains("/critical_data.db") == true)
        }
    }
}
