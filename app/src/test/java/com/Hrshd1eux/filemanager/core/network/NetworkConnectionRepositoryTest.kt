package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.network.credentials.SecureCredentialsStore
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.repository.NetworkConnectionRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun initialState_startsEmptyWithoutDummyProfiles() = runTest {
        val list = repository.getConnections()
        assertTrue("Must start with empty connections list without dummy profiles", list.isEmpty())
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

    @Test
    fun exportProfilesWithoutSecrets_strictlyOmitsPasswordsAndKeys() = runTest {
        val secretConfig = NetworkServerConfig(
            id = "classified-01",
            name = "Secret Server",
            protocol = NetworkProtocol.SFTP,
            host = "classified.vault.internal",
            authType = NetworkAuthType.PASSWORD,
            username = "chief_officer",
            password = "SuperSecretPassword123!",
            privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----...",
            passphrase = "SecretPassphrase"
        )
        repository.saveConnection(secretConfig)

        val exportedJson = repository.exportProfilesWithoutSecrets()
        assertTrue("Exported JSON must contain server name", exportedJson.contains("Secret Server"))
        assertTrue("Exported JSON must contain host", exportedJson.contains("classified.vault.internal"))

        // Strictly verify NO secrets exist in export
        assertFalse("Export must NOT leak password", exportedJson.contains("SuperSecretPassword123!"))
        assertFalse("Export must NOT leak private key", exportedJson.contains("BEGIN OPENSSH PRIVATE KEY"))
        assertFalse("Export must NOT leak passphrase", exportedJson.contains("SecretPassphrase"))
    }

    @Test
    fun importProfiles_safelyImportsProfilesWithoutSecrets() = runTest {
        val jsonPayload = """
            [
                {
                    "id": "imported-server-01",
                    "name": "Imported Remote Box",
                    "protocol": "SFTP",
                    "host": "10.200.1.5",
                    "port": 2222,
                    "authType": "PASSWORD",
                    "username": "imported_user",
                    "basePath": "/var/files",
                    "password": "HackerInjectedPlaintextPassword"
                }
            ]
        """.trimIndent()

        val result = repository.importProfiles(jsonPayload)
        assertTrue("Import must succeed", result.isSuccess)
        assertEquals(1, result.getOrNull())

        val imported = repository.getConnection("imported-server-01")
        assertNotNull(imported)
        assertEquals("Imported Remote Box", imported?.name)
        assertEquals("10.200.1.5", imported?.host)
        assertNull("Password from import JSON must be stripped and not stored", imported?.password)

        val populated = repository.populateWithSecrets(imported!!)
        assertNull("No secret must be persisted from untrusted JSON import", populated.password)
    }
}
