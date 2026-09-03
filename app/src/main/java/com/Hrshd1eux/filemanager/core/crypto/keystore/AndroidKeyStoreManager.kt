package com.Hrshd1eux.filemanager.core.crypto.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.Hrshd1eux.filemanager.core.crypto.aead.AeadCipherEngine
import com.Hrshd1eux.filemanager.core.crypto.aead.EncryptedPayload
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AndroidKeyStoreManager(
    private val keyStoreProvider: String = ANDROID_KEYSTORE_PROVIDER,
    private val aeadEngine: AeadCipherEngine = AeadCipherEngine()
) {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(keyStoreProvider).apply { load(null) }
    }

    /**
     * Retrieves an existing hardware-backed SecretKey from Android Keystore, or creates a new 256-bit AES key.
     */
    fun getOrCreateMasterKey(alias: String): SecretKey {
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreProvider)
        val keyGenSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext bytes using the master key from Android Keystore.
     */
    fun encryptWithMasterKey(
        alias: String,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): EncryptedPayload {
        val masterKey = getOrCreateMasterKey(alias)
        return aeadEngine.encryptPayload(plaintext, masterKey, associatedData)
    }

    /**
     * Decrypts an EncryptedPayload using the master key from Android Keystore.
     */
    fun decryptWithMasterKey(
        alias: String,
        payload: EncryptedPayload,
        associatedData: ByteArray? = null
    ): ByteArray {
        val masterKey = getOrCreateMasterKey(alias)
        return aeadEngine.decryptPayload(payload, masterKey, associatedData)
    }

    /**
     * Deletes a key from the Android Keystore.
     */
    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * Checks if a key exists in the Android Keystore.
     */
    fun hasKey(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    companion object {
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
    }
}
