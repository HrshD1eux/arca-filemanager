package com.Hrshd1eux.filemanager.core.network

import com.Hrshd1eux.filemanager.core.crypto.aead.AeadCipherEngine
import com.Hrshd1eux.filemanager.core.network.credentials.SecureCredentialsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecureCredentialsStoreTest {

    private lateinit var store: SecureCredentialsStore

    @Before
    fun setUp() {
        store = SecureCredentialsStore(
            context = null,
            aeadEngine = AeadCipherEngine()
        )
    }

    @Test
    fun saveAndRetrievePassword_encryptedAndRestoredAccurately() {
        val connectionId = "conn-sftp-01"
        val password = "SuperSecret#SftpPassword_2026!"

        store.saveSecret(connectionId, SecureCredentialsStore.SECRET_PASSWORD, password)

        val retrieved = store.getSecret(connectionId, SecureCredentialsStore.SECRET_PASSWORD)
        assertEquals(password, retrieved)
    }

    @Test
    fun saveAndRetrieveSshPrivateKey_restoredAccurately() {
        val connectionId = "conn-sftp-ssh-key"
        val mockOpenSshKey = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABlwAAAAdzc2gtcn
            NhAAAAAwEAAQAAAYEAm+mYt29Q4Rz5G8wQ1Z9X7h6e2...
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
        val passphrase = "my-key-passphrase-xyz"

        store.saveSecret(connectionId, SecureCredentialsStore.SECRET_PRIVATE_KEY, mockOpenSshKey)
        store.saveSecret(connectionId, SecureCredentialsStore.SECRET_PASSPHRASE, passphrase)

        val retrievedKey = store.getSecret(connectionId, SecureCredentialsStore.SECRET_PRIVATE_KEY)
        val retrievedPassphrase = store.getSecret(connectionId, SecureCredentialsStore.SECRET_PASSPHRASE)

        assertEquals(mockOpenSshKey, retrievedKey)
        assertEquals(passphrase, retrievedPassphrase)
    }

    @Test
    fun removeSecret_purgesSecretFromStorage() {
        val connectionId = "conn-to-remove"
        store.saveSecret(connectionId, SecureCredentialsStore.SECRET_PASSWORD, "password123")
        assertNotNull(store.getSecret(connectionId, SecureCredentialsStore.SECRET_PASSWORD))

        store.removeSecret(connectionId, SecureCredentialsStore.SECRET_PASSWORD)
        assertNull(store.getSecret(connectionId, SecureCredentialsStore.SECRET_PASSWORD))
    }

    @Test
    fun purgeConnectionSecrets_removesAllAssociatedKeys() {
        val connectionId = "conn-multi-secret"
        store.saveSecret(connectionId, SecureCredentialsStore.SECRET_PASSWORD, "pass")
        store.saveSecret(connectionId, SecureCredentialsStore.SECRET_PRIVATE_KEY, "key")
        store.saveSecret(connectionId, SecureCredentialsStore.SECRET_PASSPHRASE, "phrase")

        store.purgeConnectionSecrets(connectionId)

        assertNull(store.getSecret(connectionId, SecureCredentialsStore.SECRET_PASSWORD))
        assertNull(store.getSecret(connectionId, SecureCredentialsStore.SECRET_PRIVATE_KEY))
        assertNull(store.getSecret(connectionId, SecureCredentialsStore.SECRET_PASSPHRASE))
    }

    @Test
    fun nonExistentSecret_returnsNull() {
        assertNull(store.getSecret("nonexistent", SecureCredentialsStore.SECRET_PASSWORD))
    }
}
