package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkCapabilities
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.FtpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.FtpsNetworkProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests covering FTP (RFC 959) and FTPS (RFC 4217) network providers.
 * Validates connection lifecycles, authentication modes, explicit vs implicit TLS,
 * unencrypted warnings, and transfer encryption capabilities.
 */
class FtpFtpsProviderTest {

    // ==========================================
    // FTP (Cleartext RFC 959) Tests
    // ==========================================

    @Test
    fun ftpProvider_connectWithPassword_authenticatesSession() = runTest {
        val config = NetworkServerConfig(
            name = "Legacy Server",
            protocol = NetworkProtocol.FTP,
            host = "192.168.1.15",
            port = 21,
            authType = NetworkAuthType.PASSWORD,
            username = "ftpuser",
            password = "ftppassword",
            isPassiveMode = true
        )
        val provider = FtpNetworkProvider(config)
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)

        val result = provider.connect()
        assertTrue("Connect must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)

        val rootNode = provider.getNode("/").getOrThrow()
        assertTrue(rootNode.isDirectory)
        assertEquals(NetworkProtocol.FTP, rootNode.protocol)

        provider.disconnect()
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)
    }

    @Test
    fun ftpProvider_connectAnonymous_authenticatesSession() = runTest {
        val config = NetworkServerConfig(
            name = "Public FTP Archive",
            protocol = NetworkProtocol.FTP,
            host = "ftp.gnu.org",
            port = 21,
            authType = NetworkAuthType.ANONYMOUS,
            username = null,
            password = null,
            isPassiveMode = true
        )
        val provider = FtpNetworkProvider(config)
        val result = provider.connect()
        assertTrue("Anonymous connect must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)
    }

    @Test
    fun ftpProvider_missingPassword_failsWithAuthException() = runTest {
        val config = NetworkServerConfig(
            name = "Incomplete FTP",
            protocol = NetworkProtocol.FTP,
            host = "192.168.1.15",
            authType = NetworkAuthType.PASSWORD,
            username = "ftpuser",
            password = ""
        )
        val provider = FtpNetworkProvider(config)
        val result = provider.connect()
        assertTrue("Connect must fail with empty password", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException.NetworkAuthException)
    }

    @Test
    fun ftpProvider_testConnection_reportsPlaintextBannerAndLatency() = runTest {
        val config = NetworkServerConfig(
            name = "Test FTP",
            protocol = NetworkProtocol.FTP,
            host = "192.168.1.15"
        )
        val provider = FtpNetworkProvider(config)
        val testResult = provider.testConnection().getOrThrow()

        assertTrue(testResult.isSuccess)
        assertTrue(testResult.latencyMs >= 1L)
        assertNotNull(testResult.serverBanner)
        assertTrue(testResult.serverBanner?.contains("ARCA FTP Ready") == true)
        assertTrue(testResult.detectedDialectOrVersion?.contains("RFC 959 (Plaintext)") == true)
    }

    @Test
    fun ftpProvider_capabilities_strictlyDeclaresUnencryptedTransport() {
        val config = NetworkServerConfig(name = "FTP", protocol = NetworkProtocol.FTP, host = "10.0.0.1")
        val provider = FtpNetworkProvider(config)

        assertFalse("FTP MUST be declared unencrypted", provider.capabilities.isEncryptedTransport)
        assertTrue("FTP supports resume", provider.capabilities.supportsResume)
        assertFalse("FTP does not support random access seeking", provider.capabilities.supportsRandomAccess)
        assertTrue("FTP supports server-side rename", provider.capabilities.supportsServerSideMove)
    }

    @Test
    fun ftpProvider_fileAndDirectoryLifecycle() = runTest {
        val config = NetworkServerConfig(
            name = "FTP Host",
            protocol = NetworkProtocol.FTP,
            host = "192.168.1.15",
            authType = NetworkAuthType.ANONYMOUS
        )
        val provider = FtpNetworkProvider(config)
        provider.connect()

        // Create remote folder
        val mkdirResult = provider.createDirectory("/ftp_docs")
        assertTrue(mkdirResult.isSuccess)

        // Write remote file
        val outStreamResult = provider.openOutputStream("/ftp_docs/notes.txt")
        assertTrue(outStreamResult.isSuccess)
        outStreamResult.getOrThrow().use { it.write("Cleartext FTP test".toByteArray()) }

        // Verify existence and listing
        val children = provider.listChildren("/ftp_docs").getOrThrow()
        assertEquals(1, children.size)
        assertEquals("notes.txt", children[0].name)
        assertFalse(children[0].isDirectory)

        // Open input stream
        val inStreamResult = provider.openInputStream("/ftp_docs/notes.txt")
        assertTrue(inStreamResult.isSuccess)
        inStreamResult.getOrThrow().close()

        // Delete
        assertTrue(provider.delete("/ftp_docs/notes.txt").isSuccess)
        assertTrue(provider.delete("/ftp_docs").isSuccess)
    }

    // ==========================================
    // FTPS (TLS Encrypted RFC 4217) Tests
    // ==========================================

    @Test
    fun ftpsProvider_connectExplicitTls_authenticatesSession() = runTest {
        val config = NetworkServerConfig(
            name = "Explicit FTPS Server",
            protocol = NetworkProtocol.FTPS,
            host = "ftps.example.com",
            port = 21,
            isImplicitTls = false,
            allowSelfSignedCert = false,
            authType = NetworkAuthType.PASSWORD,
            username = "tlsuser",
            password = "tlspassword"
        )
        val provider = FtpsNetworkProvider(config)
        assertEquals(21, config.effectivePort)
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)

        val result = provider.connect()
        assertTrue("Connect must succeed", result.isSuccess)
        assertEquals(NetworkConnectionState.Authenticated, provider.connectionState.value)

        val testResult = provider.testConnection().getOrThrow()
        assertTrue(testResult.isSuccess)
        assertTrue(testResult.detectedDialectOrVersion?.contains("Explicit AUTH TLS") == true)
        assertTrue(testResult.serverBanner?.contains("TLS") == true)

        provider.disconnect()
        assertEquals(NetworkConnectionState.Disconnected, provider.connectionState.value)
    }

    @Test
    fun ftpsProvider_connectImplicitTls_defaultsToPort990() = runTest {
        val config = NetworkServerConfig(
            name = "Implicit FTPS Server",
            protocol = NetworkProtocol.FTPS,
            host = "ftps.example.com",
            port = 0, // Should resolve default port
            isImplicitTls = true,
            authType = NetworkAuthType.PASSWORD,
            username = "tlsuser",
            password = "tlspassword"
        )
        assertEquals(990, config.effectivePort)

        val provider = FtpsNetworkProvider(config)
        val result = provider.connect()
        assertTrue("Connect must succeed", result.isSuccess)

        val testResult = provider.testConnection().getOrThrow()
        assertTrue(testResult.detectedDialectOrVersion?.contains("Implicit TLS (Port 990)") == true)
    }

    @Test
    fun ftpsProvider_missingPassword_failsWithAuthException() = runTest {
        val config = NetworkServerConfig(
            name = "Incomplete FTPS",
            protocol = NetworkProtocol.FTPS,
            host = "ftps.example.com",
            authType = NetworkAuthType.PASSWORD,
            username = "tlsuser",
            password = ""
        )
        val provider = FtpsNetworkProvider(config)
        val result = provider.connect()
        assertTrue("Connect must fail with empty password", result.isFailure)
        assertTrue(result.exceptionOrNull() is NetworkException.NetworkAuthException)
    }

    @Test
    fun ftpsProvider_capabilities_strictlyDeclaresEncryptedTransport() {
        val config = NetworkServerConfig(
            name = "FTPS Host",
            protocol = NetworkProtocol.FTPS,
            host = "10.0.0.1",
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "pass"
        )
        val provider = FtpsNetworkProvider(config)

        assertTrue("FTPS MUST be declared encrypted", provider.capabilities.isEncryptedTransport)
        assertTrue("FTPS supports resume", provider.capabilities.supportsResume)
        assertFalse("FTPS does not support random access seeking", provider.capabilities.supportsRandomAccess)
    }

    @Test
    fun ftpsProvider_selfSignedCertConfig_isHonored() {
        val config = NetworkServerConfig(
            name = "Self-Signed FTPS",
            protocol = NetworkProtocol.FTPS,
            host = "192.168.1.100",
            allowSelfSignedCert = true,
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = "pass"
        )
        assertTrue("Self-signed cert flag must be preserved", config.allowSelfSignedCert)
        config.validate() // Must pass validation
    }

    @Test
    fun protocolSecurityComparison_ftpVsFtps_isHonestAndExplicit() {
        // Core protocol definition verification
        assertFalse("FTP is inherently unencrypted", NetworkProtocol.FTP.isEncryptedByDefault)
        assertTrue("FTPS is encrypted by default", NetworkProtocol.FTPS.isEncryptedByDefault)

        // Capabilities verification
        assertFalse(NetworkCapabilities.FTP.isEncryptedTransport)
        assertTrue(NetworkCapabilities.FTPS.isEncryptedTransport)
    }
}
