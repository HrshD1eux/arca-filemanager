package com.Hrshd1eux.filemanager.core.vault

import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.crypto.aead.EncryptedPayload
import com.Hrshd1eux.filemanager.core.crypto.random.SecureRandomGenerator
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.vault.model.EncryptedPayloadDto
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry
import com.Hrshd1eux.filemanager.core.vault.model.VaultManifest
import com.Hrshd1eux.filemanager.core.vault.model.VaultMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.SecretKey

class VaultManager(
    val vaultDir: File,
    private val cryptoEngine: CryptoEngine = CryptoEngine(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val metaFile = File(vaultDir, META_FILE_NAME)
    private val manifestFile = File(vaultDir, MANIFEST_FILE_NAME)
    private val dataDir = File(vaultDir, DATA_DIR_NAME)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Volatile
    private var activeKey: SecretKey? = null

    @Volatile
    private var currentManifest: VaultManifest? = null

    fun isConfigured(): Boolean = metaFile.exists() && metaFile.length() > 0

    fun isUnlocked(): Boolean = activeKey != null

    fun getFiles(): List<VaultFileEntry> = currentManifest?.files ?: emptyList()

    /**
     * Initializes a new Private Vault with a user password.
     */
    suspend fun createVault(password: CharArray): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(password.isNotEmpty()) { "Vault password cannot be empty" }
            vaultDir.mkdirs()
            dataDir.mkdirs()

            val salt = SecureRandomGenerator.generateSalt(32)
            val masterKey = cryptoEngine.deriveKey(password, salt)

            try {
                // 1. Generate verification token
                val verifyPayload = cryptoEngine.encryptPayload(VERIFICATION_TOKEN_BYTES, masterKey)
                val metadata = VaultMetadata(
                    saltHex = salt.toHexString(),
                    verificationPayload = EncryptedPayloadDto(
                        ivHex = verifyPayload.iv.toHexString(),
                        ciphertextHex = verifyPayload.ciphertextWithTag.toHexString()
                    ),
                    createdAt = System.currentTimeMillis()
                )
                metaFile.writeText(json.encodeToString(VaultMetadata.serializer(), metadata))

                // 2. Initialize empty manifest
                val initialManifest = VaultManifest(emptyList())
                val manifestBytes = json.encodeToString(VaultManifest.serializer(), initialManifest).toByteArray(Charsets.UTF_8)
                val encryptedManifest = cryptoEngine.encryptPayload(manifestBytes, masterKey)
                manifestFile.writeBytes(encryptedManifest.iv + encryptedManifest.ciphertextWithTag)

                // 3. Set unlocked session
                activeKey = masterKey
                currentManifest = initialManifest
            } finally {
                cryptoEngine.wipeSensitiveData(password)
            }
        }
    }

    /**
     * Unlocks the Private Vault using the user password.
     */
    suspend fun unlock(password: CharArray): Result<List<VaultFileEntry>> = withContext(ioDispatcher) {
        runCatching {
            if (!isConfigured()) {
                throw IllegalStateException("Vault is not configured yet")
            }

            val metaJson = metaFile.readText()
            val metadata = json.decodeFromString(VaultMetadata.serializer(), metaJson)
            val salt = hexToBytes(metadata.saltHex)
            val masterKey = cryptoEngine.deriveKey(password, salt)

            try {
                // 1. Verify password via verification token
                val tokenPayload = EncryptedPayload(
                    iv = hexToBytes(metadata.verificationPayload.ivHex),
                    ciphertextWithTag = hexToBytes(metadata.verificationPayload.ciphertextHex)
                )
                val decryptedToken = cryptoEngine.decryptPayload(tokenPayload, masterKey)
                if (!decryptedToken.contentEquals(VERIFICATION_TOKEN_BYTES)) {
                    throw GeneralSecurityException("Incorrect vault password")
                }

                // 2. Decrypt manifest
                val manifestRaw = manifestFile.readBytes()
                val iv = manifestRaw.copyOfRange(0, 12)
                val ciphertext = manifestRaw.copyOfRange(12, manifestRaw.size)
                val decryptedManifestBytes = cryptoEngine.decryptPayload(EncryptedPayload(iv, ciphertext), masterKey)
                val manifest = json.decodeFromString(VaultManifest.serializer(), String(decryptedManifestBytes, Charsets.UTF_8))

                activeKey = masterKey
                currentManifest = manifest
                manifest.files
            } finally {
                cryptoEngine.wipeSensitiveData(password)
            }
        }
    }

    /**
     * Locks the vault immediately and clears active cryptographic keys from memory.
     */
    fun lock() {
        activeKey = null
        currentManifest = null
    }

    /**
     * Imports an external file into the encrypted vault.
     */
    suspend fun importFile(
        sourceFile: File,
        deleteOriginal: Boolean = false,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ): Result<VaultFileEntry> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            if (!sourceFile.exists() || !sourceFile.isFile) {
                throw IllegalArgumentException("Source file does not exist: ${sourceFile.path}")
            }

            dataDir.mkdirs()
            val fileId = UUID.randomUUID().toString()
            val encFileName = "$fileId.enc"
            val encFile = File(dataDir, encFileName)

            // Encrypt streaming
            cryptoEngine.encryptFile(
                sourceFile = sourceFile,
                destinationFile = encFile,
                key = key,
                onProgress = onProgress
            )

            val entry = VaultFileEntry(
                id = fileId,
                originalName = sourceFile.name,
                encryptedFileName = encFileName,
                size = sourceFile.length(),
                mimeType = MimeTypeResolver.getMimeType(sourceFile.extension) ?: "*/*",
                addedDate = System.currentTimeMillis()
            )

            // Update Manifest
            val updatedList = (currentManifest?.files ?: emptyList()) + entry
            val updatedManifest = VaultManifest(updatedList)
            saveManifest(updatedManifest, key)
            currentManifest = updatedManifest

            if (deleteOriginal) {
                sourceFile.delete()
            }

            entry
        }
    }

    /**
     * Exports a file from the vault to a destination directory.
     */
    suspend fun exportFile(
        entry: VaultFileEntry,
        destinationDir: File,
        onProgress: ((bytesWritten: Long) -> Unit)? = null
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            val encFile = File(dataDir, entry.encryptedFileName)
            if (!encFile.exists()) {
                throw IllegalStateException("Encrypted file payload missing: ${encFile.path}")
            }

            destinationDir.mkdirs()
            var destFile = File(destinationDir, entry.originalName)
            if (destFile.exists()) {
                val nameWithoutExt = destFile.nameWithoutExtension
                val ext = destFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
                var counter = 1
                while (destFile.exists()) {
                    destFile = File(destinationDir, "$nameWithoutExt ($counter)$ext")
                    counter++
                }
            }

            cryptoEngine.decryptFile(
                sourceFile = encFile,
                destinationFile = destFile,
                key = key,
                onProgress = onProgress
            )

            destFile
        }
    }

    /**
     * Deletes a file permanently from the encrypted vault.
     */
    suspend fun deleteFile(entry: VaultFileEntry): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            val encFile = File(dataDir, entry.encryptedFileName)
            if (encFile.exists()) {
                encFile.delete()
            }

            val updatedList = (currentManifest?.files ?: emptyList()).filterNot { it.id == entry.id }
            val updatedManifest = VaultManifest(updatedList)
            saveManifest(updatedManifest, key)
            currentManifest = updatedManifest
        }
    }

    private fun saveManifest(manifest: VaultManifest, key: SecretKey) {
        val bytes = json.encodeToString(VaultManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
        val encrypted = cryptoEngine.encryptPayload(bytes, key)
        manifestFile.writeBytes(encrypted.iv + encrypted.ciphertextWithTag)
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            result[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }

    companion object {
        private const val META_FILE_NAME = "vault.meta"
        private const val MANIFEST_FILE_NAME = "manifest.enc"
        private const val DATA_DIR_NAME = "data"
        private val VERIFICATION_TOKEN_BYTES = "ARCA_VAULT_OK_v1".toByteArray(Charsets.UTF_8)
    }
}
