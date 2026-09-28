package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpProviderTest {

    @Test
    fun sftpProvider_connectWithPassword_authenticatesSession() = runTest {
        val config = NetworkServerConfig(
            name = "NAS",
            protocol = NetworkProtocol.SFTP,
            host = "192.168.1.20",
            port = 22,
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = "sftp-password"
        )
        val provider = SftpNetworkProvider(config)
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)

        val result = provider.connect()
        assertTrue("Connect must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)

        val rootNode = provider.getNode("/").getOrThrow()
        assertTrue(rootNode.isDirectory)
        assertEquals("rwxr-xr-x", rootNode.permissions)

        provider.disconnect()
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)
    }

    @Test
    fun sftpProvider_connectWithKey_authenticatesSession() = runTest {
        val config = NetworkServerConfig(
            name = "Server",
            protocol = NetworkProtocol.SFTP,
            host = "example.local",
            port = 22,
            authType = NetworkAuthType.KEY_PAIR,
            username = "user",
            privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\ntest\n-----END OPENSSH PRIVATE KEY-----"
        )
        val provider = SftpNetworkProvider(config)
        val result = provider.connect()
        assertTrue("Connect with SSH key must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)
    }

    @Test
    fun sftpProvider_missingCredentials_failsWithAuthException() = runTest {
        val config = NetworkServerConfig(
            name = "Incomplete Server",
            protocol = NetworkProtocol.SFTP,
            host = "example.local",
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = ""
        )
        val provider = SftpNetworkProvider(config)
        val result = provider.connect()
        assertTrue("Connect must fail with empty password", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException.NetworkAuthException)
    }

    @Test
    fun sftpProvider_testConnection_reportsLatencyAndBanner() = runTest {
        val config = NetworkServerConfig(
            name = "Test SSH",
            protocol = NetworkProtocol.SFTP,
            host = "192.168.1.20",
            authType = NetworkAuthType.PASSWORD,
            username = "testuser"
        )
        val provider = SftpNetworkProvider(config)
        val testResult = provider.testConnection().getOrThrow()

        assertTrue(testResult.isSuccess)
        assertTrue(testResult.latencyMs >= 1L)
        assertNotNull(testResult.serverBanner)
        assertTrue(testResult.serverBanner?.contains("SSH-2.0") == true)
        assertTrue(testResult.detectedDialectOrVersion?.contains("SFTP") == true)
    }

    @Test
    fun sftpProvider_fileAndDirectoryLifecycle() = runTest {
        val config = NetworkServerConfig(
            name = "NAS",
            protocol = NetworkProtocol.SFTP,
            host = "192.168.1.20",
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = "password"
        )
        val provider = SftpNetworkProvider(config)
        provider.connect()

        // Create directory
        provider.createDirectory("/backup").getOrThrow()
        assertTrue(provider.exists("/backup"))

        // Create remote file output stream
        val outStream = provider.openOutputStream("/backup/test.txt").getOrThrow()
        outStream.write("hello sftp".toByteArray())
        outStream.close()
        assertTrue(provider.exists("/backup/test.txt"))

        // List children
        val children = provider.listChildren("/backup").getOrThrow()
        assertEquals(1, children.size)
        assertEquals("test.txt", children[0].name)

        // Rename
        provider.rename("/backup/test.txt", "renamed.txt").getOrThrow()
        assertFalse(provider.exists("/backup/test.txt"))
        assertTrue(provider.exists("/backup/renamed.txt"))

        // Delete
        provider.delete("/backup/renamed.txt").getOrThrow()
        assertFalse(provider.exists("/backup/renamed.txt"))
    }
}
