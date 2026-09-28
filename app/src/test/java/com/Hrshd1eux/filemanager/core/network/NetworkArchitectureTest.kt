package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageBridge
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkCapabilities
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkNode
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.FtpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.FtpsNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.SmbNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.WebDavNetworkProvider
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NetworkArchitectureTest {

    @Test
    fun networkProtocol_attributesAndDefaultPorts_areCorrect() {
        assertEquals("ftp", NetworkProtocol.FTP.scheme)
        assertEquals(21, NetworkProtocol.FTP.defaultPort)
        assertFalse(NetworkProtocol.FTP.isEncryptedByDefault)

        assertEquals("ftps", NetworkProtocol.FTPS.scheme)
        assertEquals(21, NetworkProtocol.FTPS.defaultPort)
        assertTrue(NetworkProtocol.FTPS.isEncryptedByDefault)

        assertEquals("sftp", NetworkProtocol.SFTP.scheme)
        assertEquals(22, NetworkProtocol.SFTP.defaultPort)
        assertTrue(NetworkProtocol.SFTP.isEncryptedByDefault)

        assertEquals("smb", NetworkProtocol.SMB.scheme)
        assertEquals(445, NetworkProtocol.SMB.defaultPort)
        assertFalse(NetworkProtocol.SMB.isEncryptedByDefault)

        assertEquals("webdav", NetworkProtocol.WEBDAV.scheme)
        assertEquals(80, NetworkProtocol.WEBDAV.defaultPort)
        assertFalse(NetworkProtocol.WEBDAV.isEncryptedByDefault)
    }

    @Test
    fun networkServerConfig_validation_succeedsForValidConfigs() {
        val config = NetworkServerConfig(
            name = "Home NAS",
            protocol = NetworkProtocol.SFTP,
            host = "192.168.1.100",
            port = 22,
            authType = NetworkAuthType.PASSWORD,
            username = "user",
            password = "secretPassword"
        )
        config.validate()
        assertEquals(22, config.effectivePort)
        assertEquals("sftp://user@192.168.1.100/", config.toSanitizedUriString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun networkServerConfig_blankHost_throwsException() {
        val config = NetworkServerConfig(
            name = "Invalid",
            protocol = NetworkProtocol.FTP,
            host = ""
        )
        config.validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun networkServerConfig_invalidPort_throwsException() {
        val config = NetworkServerConfig(
            name = "Invalid Port",
            protocol = NetworkProtocol.FTP,
            host = "192.168.1.1",
            port = 70000
        )
        config.validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun networkServerConfig_unsupportedAuthType_throwsException() {
        // FTP does not support SSH Key Pair authentication
        val config = NetworkServerConfig(
            name = "Mismatched Auth",
            protocol = NetworkProtocol.FTP,
            host = "192.168.1.1",
            authType = NetworkAuthType.KEY_PAIR,
            privateKey = "my-key"
        )
        config.validate()
    }

    @Test
    fun networkProviderRegistry_createsCorrectProviderForEveryProtocol() {
        val ftpConfig = NetworkServerConfig(name = "FTP", protocol = NetworkProtocol.FTP, host = "10.0.0.1")
        val ftpsConfig = NetworkServerConfig(name = "FTPS", protocol = NetworkProtocol.FTPS, host = "10.0.0.1", authType = NetworkAuthType.PASSWORD, username = "u", password = "p")
        val sftpConfig = NetworkServerConfig(name = "SFTP", protocol = NetworkProtocol.SFTP, host = "10.0.0.1", authType = NetworkAuthType.PASSWORD, username = "u", password = "p")
        val smbConfig = NetworkServerConfig(name = "SMB", protocol = NetworkProtocol.SMB, host = "10.0.0.1", shareName = "shared")
        val webdavConfig = NetworkServerConfig(name = "WebDAV", protocol = NetworkProtocol.WEBDAV, host = "10.0.0.1")

        val ftp = NetworkProviderRegistry.createProvider(ftpConfig)
        val ftps = NetworkProviderRegistry.createProvider(ftpsConfig)
        val sftp = NetworkProviderRegistry.createProvider(sftpConfig)
        val smb = NetworkProviderRegistry.createProvider(smbConfig)
        val webdav = NetworkProviderRegistry.createProvider(webdavConfig)

        assertTrue("Must instantiate FtpNetworkProvider", ftp is FtpNetworkProvider)
        assertTrue("Must instantiate FtpsNetworkProvider", ftps is FtpsNetworkProvider)
        assertTrue("Must instantiate SftpNetworkProvider", sftp is SftpNetworkProvider)
        assertTrue("Must instantiate SmbNetworkProvider", smb is SmbNetworkProvider)
        assertTrue("Must instantiate WebDavNetworkProvider", webdav is WebDavNetworkProvider)
    }

    @Test
    fun smbProvider_insecureSmb1Dialect_isStrictlyRejected() = runTest {
        val insecureConfig = NetworkServerConfig(
            name = "Insecure SMB1 Server",
            protocol = NetworkProtocol.SMB,
            host = "192.168.1.50",
            basePath = "/smb1/legacy",
            shareName = "public"
        )
        val smb = SmbNetworkProvider(insecureConfig)
        val result = smb.connect()

        assertTrue("Connection must fail when SMB1 is requested", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(
            "Must throw InsecureProtocolException",
            ex is NetworkException.InsecureProtocolException
        )
        assertTrue(
            "Error must mention SMB1 rejection",
            ex?.message?.contains("SMB1") == true
        )
    }

    @Test
    fun networkStorageBridge_adaptsNetworkProviderToGenericStorageProvider() = runTest {
        val config = NetworkServerConfig(
            name = "Cloud SFTP",
            protocol = NetworkProtocol.SFTP,
            host = "storage.example.com",
            port = 22,
            authType = NetworkAuthType.PASSWORD,
            username = "admin",
            password = "secure"
        )
        val provider = SftpNetworkProvider(config)
        val bridge = NetworkStorageBridge(provider)

        assertEquals("Cloud SFTP (SFTP (SSH File Transfer))", bridge.displayName)
        assertTrue(bridge.providerId.startsWith("network_sftp_"))

        // Before connect: hasPermission is false
        assertFalse(bridge.hasPermission("/"))

        val connectResult = provider.connect()
        assertTrue("Connect must succeed", connectResult.isSuccess)
        assertTrue(bridge.hasPermission("/"))

        // Root node inspection
        val root = bridge.getRootNode()
        assertEquals("/", root.path)
        assertTrue(root.isDirectory)

        // Remote directory creation via provider
        val mkdirResult = provider.createDirectory("/documents")
        assertTrue(mkdirResult.isSuccess)

        // List children through StorageProvider contract
        val children = bridge.listChildren("/").getOrThrow()
        assertEquals(1, children.size)
        assertEquals("documents", children[0].name)
        assertTrue(children[0].isDirectory)
        assertTrue(children[0].id.startsWith("sftp://"))

        // Exists check through bridge
        assertTrue(bridge.exists("/documents"))
        assertFalse(bridge.exists("/nonexistent"))

        // Disconnect
        provider.disconnect()
        assertFalse(bridge.hasPermission("/"))
    }

    @Test
    fun networkNode_toStorageNode_mapsFieldsTruthfully() {
        val node = NetworkNode(
            path = "/media/video.mp4",
            name = "video.mp4",
            isDirectory = false,
            size = 10485760L,
            lastModified = 1700000000000L,
            permissions = "rw-r--r--",
            mimeType = "video/mp4",
            protocol = NetworkProtocol.SMB,
            serverConfigId = "cfg-123"
        )

        val storageNode = node.toStorageNode()
        assertEquals("smb://cfg-123/media/video.mp4", storageNode.id)
        assertEquals("video.mp4", storageNode.name)
        assertEquals("/media/video.mp4", storageNode.path)
        assertEquals(10485760L, storageNode.size)
        assertEquals(1700000000000L, storageNode.lastModified)
        assertFalse(storageNode.isDirectory)
        assertFalse(storageNode.isHidden)
        assertEquals("video/mp4", storageNode.mimeType)
    }

    @Test
    fun networkCapabilities_truthfulness() {
        assertFalse("FTP transport is not encrypted", NetworkCapabilities.FTP.isEncryptedTransport)
        assertTrue("FTPS transport is encrypted", NetworkCapabilities.FTPS.isEncryptedTransport)
        assertTrue("SFTP transport is encrypted", NetworkCapabilities.SFTP.isEncryptedTransport)
        assertTrue("SMB requires share root", NetworkCapabilities.SMB.requiresShareRoot)
        assertTrue("SFTP supports random access seeking", NetworkCapabilities.SFTP.supportsRandomAccess)
    }
}
