package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.credentials.SecureCredentialsStore
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkConnectionRepositoryTest {

    private lateinit var repository: NetworkConnectionRepositoryImpl
    private lateinit var credentialsStore: SecureCredentialsStore

    @Before
    fun setUp() {
        credentialsStore = SecureCredentialsStore(context = null)
        repository = NetworkConnectionRepositoryImpl(
            context = null,
            credentialsStore = credentialsStore,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun initialState_preSeedsSampleNasAndServerConnections() = runTest {
        val list = repository.getConnections()
        assertEquals("Must pre-seed 2 sample connections", 2, list.size)

        val nas = list.find { it.name == "NAS" }
        assertNotNull("Sample NAS connection must exist", nas)
        assertEquals("192.168.1.20", nas?.host)
        assertEquals(NetworkProtocol.SFTP, nas?.protocol)
        assertEquals(22, nas?.effectivePort)

        val server = list.find { it.name == "Server" }
        assertNotNull("Sample Server connection must exist", server)
        assertEquals("example.local", server?.host)
        assertEquals(NetworkProtocol.SFTP, server?.protocol)
        assertEquals(22, server?.effectivePort)
    }

    @Test
    fun saveConnection_encryptsSecretsAndExposesMetadata() = runTest {
        val newConnection = NetworkServerConfig(
            id = "custom-sftp-01",
            name = "Backup Storage",
            protocol = NetworkProtocol.SFTP,
            host = "backup.internal.lan",
            port = 2222,
            authType = NetworkAuthType.PASSWORD,
            username = "sysadmin",
            password = "EncryptedSecretPassword#99",
            basePath = "/data/backups"
        )

        repository.saveConnection(newConnection)

        val retrieved = repository.getConnection("custom-sftp-01")
        assertNotNull(retrieved)
        assertEquals("Backup Storage", retrieved?.name)
        assertEquals("backup.internal.lan", retrieved?.host)
        assertEquals(2222, retrieved?.effectivePort)
        // Saved metadata must NOT leak the plaintext password directly
        assertNull("Password must not be stored in unpopulated metadata", retrieved?.password)

        // Populating secrets decrypts password from SecureCredentialsStore
        val populated = repository.populateWithSecrets(retrieved!!)
        assertEquals("EncryptedSecretPassword#99", populated.password)
    }

    @Test
    fun deleteConnection_removesMetadataAndPurgesEncryptedSecrets() = runTest {
        val tempConnection = NetworkServerConfig(
            id = "temp-conn",
            name = "Temporary Server",
            protocol = NetworkProtocol.SFTP,
            host = "temp.local",
            authType = NetworkAuthType.PASSWORD,
            username = "tempuser",
            password = "tempPassword"
        )

        repository.saveConnection(tempConnection)
        assertNotNull(repository.getConnection("temp-conn"))

        repository.deleteConnection("temp-conn")
        assertNull(repository.getConnection("temp-conn"))

        // Secret should also be purged
        val secret = credentialsStore.getSecret("temp-conn", SecureCredentialsStore.SECRET_PASSWORD)
        assertNull("Secret must be purged upon connection deletion", secret)
    }
}
