package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.SmbNetworkProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbNetworkProviderTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun smbProvider_connectToHost_exposesWindowsNasShares() = runTest(testDispatcher) {
        // Network -> SMB -> 192.168.1.10 -> Shared
        val config = NetworkServerConfig(
            name = "Office NAS",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            port = 445,
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "smbPassword",
            shareName = "Shared"
        )
        val provider = SmbNetworkProvider(config, testDispatcher)
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)

        val result = provider.connect()
        assertTrue("SMB connection must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)

        // 1. Verify Host root exists
        assertTrue(provider.exists("/"))
        val rootNode = provider.getNode("/").getOrThrow()
        assertTrue(rootNode.isDirectory)
        assertEquals("192.168.1.10", rootNode.name)

        // 2. Verify Windows/NAS shares enumerated under root
        val shares = provider.listChildren("/").getOrThrow()
        assertTrue("Shares must be listed under host", shares.isNotEmpty())
        assertTrue("Shared folder must exist", shares.any { it.name == "Shared" })

        // 3. Verify accessing /Shared
        val sharedNode = provider.getNode("/Shared").getOrThrow()
        assertTrue(sharedNode.isDirectory)
        assertEquals("Shared", sharedNode.name)

        provider.disconnect()
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)
    }

    @Test
    fun smbProvider_listShares_returnsRegisteredShares() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Home Server",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            authType = NetworkAuthType.ANONYMOUS
        )
        val provider = SmbNetworkProvider(config, testDispatcher)
        provider.connect().getOrThrow()

        val shares = provider.listShares()
        assertTrue(shares.size >= 2)
        assertTrue(shares.any { it.name == "Shared" })
        assertTrue(shares.any { it.name == "Public" })

        // Register dynamic share
        val backupShare = provider.registerShare("Backups")
        assertEquals("/Backups", backupShare.path)
        assertEquals("Backups", backupShare.name)
        assertTrue(provider.exists("/Backups"))
    }

    @Test
    fun smbProvider_fileAndDirectoryLifecycle_insideShare() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "NAS",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            port = 445,
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = "password",
            shareName = "Shared"
        )
        val provider = SmbNetworkProvider(config, testDispatcher)
        provider.connect().getOrThrow()

        // 1. Create subfolder inside share: /Shared/Documents
        val createDirResult = provider.createDirectory("/Shared/Documents")
        assertTrue("Directory creation in share must succeed", createDirResult.isSuccess)
        assertTrue(provider.exists("/Shared/Documents"))

        val dirNode = provider.getNode("/Shared/Documents").getOrThrow()
        assertTrue(dirNode.isDirectory)
        assertEquals("Documents", dirNode.name)

        // 2. Stream upload file into share: /Shared/Documents/contract.pdf
        val fileBytes = "Samba SMB 3.1.1 binary file payload".toByteArray(Charsets.UTF_8)
        provider.openOutputStream("/Shared/Documents/contract.pdf").getOrThrow().use { out ->
            out.write(fileBytes)
        }
        assertTrue(provider.exists("/Shared/Documents/contract.pdf"))

        val fileNode = provider.getNode("/Shared/Documents/contract.pdf").getOrThrow()
        assertFalse(fileNode.isDirectory)
        assertEquals(fileBytes.size.toLong(), fileNode.size)

        // 3. Stream download file from share
        val readBytes = provider.openInputStream("/Shared/Documents/contract.pdf").getOrThrow().use { it.readBytes() }
        assertEquals("Samba SMB 3.1.1 binary file payload", String(readBytes, Charsets.UTF_8))

        // 4. List children of share folder
        val children = provider.listChildren("/Shared/Documents").getOrThrow()
        assertEquals(1, children.size)
        assertEquals("contract.pdf", children.first().name)

        // 5. Server-side duplicate / copy
        val copyResult = provider.copyResource("/Shared/Documents/contract.pdf", "/Shared/Documents/contract_copy.pdf")
        assertTrue("Server-side copy must succeed", copyResult.isSuccess)
        assertTrue(provider.exists("/Shared/Documents/contract_copy.pdf"))
        val copyBytes = provider.openInputStream("/Shared/Documents/contract_copy.pdf").getOrThrow().use { it.readBytes() }
        assertEquals("Samba SMB 3.1.1 binary file payload", String(copyBytes, Charsets.UTF_8))

        // 6. Rename file
        val renameResult = provider.rename("/Shared/Documents/contract_copy.pdf", "signed_contract.pdf")
        assertTrue("Rename must succeed", renameResult.isSuccess)
        assertFalse(provider.exists("/Shared/Documents/contract_copy.pdf"))
        assertTrue(provider.exists("/Shared/Documents/signed_contract.pdf"))

        // 7. Recursive deletion
        val deleteResult = provider.delete("/Shared/Documents")
        assertTrue("Recursive deletion of directory in share must succeed", deleteResult.isSuccess)
        assertFalse(provider.exists("/Shared/Documents"))
        assertFalse(provider.exists("/Shared/Documents/contract.pdf"))
        assertFalse(provider.exists("/Shared/Documents/signed_contract.pdf"))
    }

    @Test
    fun smbProvider_insecureSmb1Cifs_isStrictlyRejected() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Legacy Server",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            basePath = "/smb1/insecure"
        )
        val provider = SmbNetworkProvider(config, testDispatcher)
        val result = provider.connect()
        assertTrue("Connecting with SMB1 path must fail", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException.InsecureProtocolException)
    }

    @Test
    fun smbProvider_anonymousGuestSession_authenticatesSuccessfully() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Guest Share",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            authType = NetworkAuthType.ANONYMOUS
        )
        val provider = SmbNetworkProvider(config, testDispatcher)
        val result = provider.connect()
        assertTrue("Guest SMB connection must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)
    }

    @Test
    fun smbProvider_missingPassword_failsWithAuthException() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "Incomplete Auth",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10",
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = ""
        )
        val provider = SmbNetworkProvider(config, testDispatcher)
        val result = provider.connect()
        assertTrue("Password auth without password must fail", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException.NetworkAuthException)
    }

    @Test
    fun smbProvider_testConnection_reportsDialectAndBanner() = runTest(testDispatcher) {
        val config = NetworkServerConfig(
            name = "NAS",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.10"
        )
        val provider = SmbNetworkProvider(config, testDispatcher)
        val testResult = provider.testConnection().getOrThrow()

        assertTrue(testResult.isSuccess)
        assertTrue(testResult.latencyMs >= 1L)
        assertNotNull(testResult.serverBanner)
        assertTrue(testResult.serverBanner?.contains("SMB Server") == true)
        assertEquals("SMB 3.1.1 (AES-128-GCM)", testResult.detectedDialectOrVersion)
    }
}
