package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.WebDavNetworkProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavNetworkProviderTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun webdavProvider_httpsConfig_reportsEncryptedTransport() {
        val config = NetworkServerConfig(
            name = "Nextcloud Vault",
            protocol = NetworkProtocol.WEBDAV,
            host = "cloud.example.com",
            port = 443,
            isImplicitTls = true,
            authType = NetworkAuthType.PASSWORD,
            username = "alice",
            password = "app-password-123"
        )
        val provider = WebDavNetworkProvider(config, testDispatcher)
        assertTrue("WebDAV over port 443/TLS must report encrypted transport", provider.capabilities.isEncryptedTransport)
    }

    @Test
    fun webdavProvider_httpCleartextConfig_reportsUnencryptedTransport() {
        val config = NetworkServerConfig(
            name = "Insecure WebDAV",
            protocol = NetworkProtocol.WEBDAV,
            host = "192.168.1.50",
            port = 80,
            isImplicitTls = false,
            authType = NetworkAuthType.PASSWORD,
            username = "bob",
            password = "cleartext-pass"
        )
        val provider = WebDavNetworkProvider(config, testDispatcher)
        assertFalse("WebDAV over HTTP port 80 must NOT report encrypted transport", provider.capabilities.isEncryptedTransport)
    }

    @Test
    fun webdavProvider_connectWithPassword_authenticatesSession() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Nextcloud",
            protocol = NetworkProtocol.WEBDAV,
            host = "cloud.example.com",
            port = 443,
            isImplicitTls = true,
            authType = NetworkAuthType.PASSWORD,
            username = "alice",
            password = "securePassword",
            basePath = "/remote.php/dav/files/alice"
        )
        val provider = WebDavNetworkProvider(config, testDispatcher)
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)

        val result = provider.connect()
        assertTrue("Connect with valid password must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)

        val rootNode = provider.getNode("/remote.php/dav/files/alice").getOrThrow()
        assertTrue(rootNode.isDirectory)
        assertEquals("alice", rootNode.name)

        provider.disconnect()
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)
    }

    @Test
    fun webdavProvider_connectWithBearerToken_authenticatesSession() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Nextcloud Token",
            protocol = NetworkProtocol.WEBDAV,
            host = "cloud.example.com",
            port = 443,
            isImplicitTls = true,
            authType = NetworkAuthType.TOKEN,
            password = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        )
        val provider = WebDavNetworkProvider(config, testDispatcher)
        val result = provider.connect()
        assertTrue("Connect with bearer token must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)
    }

    @Test
    fun webdavProvider_connectAnonymous_authenticatesSession() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Public WebDAV Share",
            protocol = NetworkProtocol.WEBDAV,
            host = "files.public.org",
            port = 443,
            isImplicitTls = true,
            authType = NetworkAuthType.ANONYMOUS
        )
        val provider = WebDavNetworkProvider(config, testDispatcher)
        val result = provider.connect()
        assertTrue("Anonymous WebDAV connection must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)
    }

    @Test
    fun webdavProvider_missingCredentials_failsWithAuthException() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Missing Pass",
            protocol = NetworkProtocol.WEBDAV,
            host = "cloud.example.com",
            authType = NetworkAuthType.PASSWORD,
            username = "alice",
            password = ""
        )
        val provider = WebDavNetworkProvider(config, testDispatcher)
        val result = provider.connect()
        assertTrue("Connect must fail with empty password", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException.NetworkAuthException)
    }

    @Test
    fun webdavProvider_testConnection_reportsLatencyAndBanner() = runTest(testDispatcher) {
        val configHttps = NetworkServerConfig(
            name = "Secure WebDAV",
            protocol = NetworkProtocol.WEBDAV,
            host = "cloud.example.com",
            port = 443,
            isImplicitTls = true,
            authType = NetworkAuthType.PASSWORD,
            username = "alice",
            password = "pass"
        )
        val providerHttps = WebDavNetworkProvider(configHttps, testDispatcher)
        val resHttps = providerHttps.testConnection().getOrThrow()
        assertTrue(resHttps.isSuccess)
        assertTrue(resHttps.serverBanner?.contains("HTTPS/TLS") == true)
        assertTrue(resHttps.detectedDialectOrVersion?.contains("TLS") == true)

        val configHttp = NetworkServerConfig(
            name = "Plain WebDAV",
            protocol = NetworkProtocol.WEBDAV,
            host = "192.168.1.10",
            port = 80,
            isImplicitTls = false,
            authType = NetworkAuthType.ANONYMOUS
        )
        val providerHttp = WebDavNetworkProvider(configHttp, testDispatcher)
        val resHttp = providerHttp.testConnection().getOrThrow()
        assertTrue(resHttp.isSuccess)
        assertTrue(resHttp.serverBanner?.contains("HTTP Plaintext") == true)
        assertTrue(resHttp.detectedDialectOrVersion?.contains("Cleartext HTTP") == true)
    }

    @Test
    fun webdavProvider_fileAndCollectionLifecycle_executesRfc4918Methods() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Storage",
            protocol = NetworkProtocol.WEBDAV,
            host = "nas.local",
            port = 443,
            isImplicitTls = true,
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "password",
            basePath = "/storage"
        )
        val provider = WebDavNetworkProvider(config, testDispatcher)
        provider.connect().getOrThrow()

        // 1. MKCOL (Create collection)
        val folderResult = provider.createDirectory("/storage/documents")
        assertTrue("MKCOL /storage/documents must succeed", folderResult.isSuccess)
        assertTrue(provider.exists("/storage/documents"))

        val folderNode = provider.getNode("/storage/documents").getOrThrow()
        assertTrue(folderNode.isDirectory)
        assertEquals("documents", folderNode.name)

        // 2. PUT (Stream upload)
        val fileData = "WebDAV protocol RFC 4918 payload".toByteArray(Charsets.UTF_8)
        provider.openOutputStream("/storage/documents/report.txt").getOrThrow().use { out ->
            out.write(fileData)
        }
        assertTrue(provider.exists("/storage/documents/report.txt"))

        val fileNode = provider.getNode("/storage/documents/report.txt").getOrThrow()
        assertFalse(fileNode.isDirectory)
        assertEquals(fileData.size.toLong(), fileNode.size)

        // 3. GET (Stream download)
        val readBytes = provider.openInputStream("/storage/documents/report.txt").getOrThrow().use { it.readBytes() }
        assertEquals("WebDAV protocol RFC 4918 payload", String(readBytes, Charsets.UTF_8))

        // 4. PROPFIND (List children)
        val children = provider.listChildren("/storage/documents").getOrThrow()
        assertEquals(1, children.size)
        assertEquals("report.txt", children.first().name)

        // 5. COPY (Server-side resource copy)
        val copyResult = provider.copyResource("/storage/documents/report.txt", "/storage/documents/report_backup.txt")
        assertTrue("Server-side COPY must succeed", copyResult.isSuccess)
        assertTrue(provider.exists("/storage/documents/report_backup.txt"))
        val backupBytes = provider.openInputStream("/storage/documents/report_backup.txt").getOrThrow().use { it.readBytes() }
        assertEquals("WebDAV protocol RFC 4918 payload", String(backupBytes, Charsets.UTF_8))

        // 6. MOVE (Atomic rename)
        val renameResult = provider.rename("/storage/documents/report_backup.txt", "archived_report.txt")
        assertTrue("Atomic MOVE/rename must succeed", renameResult.isSuccess)
        assertFalse(provider.exists("/storage/documents/report_backup.txt"))
        assertTrue(provider.exists("/storage/documents/archived_report.txt"))

        // 7. DELETE (Recursive collection deletion)
        val deleteResult = provider.delete("/storage/documents")
        assertTrue("Recursive DELETE must succeed", deleteResult.isSuccess)
        assertFalse(provider.exists("/storage/documents"))
        assertFalse(provider.exists("/storage/documents/report.txt"))
        assertFalse(provider.exists("/storage/documents/archived_report.txt"))
    }
}
