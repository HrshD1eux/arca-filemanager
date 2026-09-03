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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

    fun getAllFiles(): List<VaultFileEntry> = currentManifest?.files ?: emptyList()

    /**
     * Lists entries inside a specific virtual path inside the Vault.
     */
    fun getEntriesInPath(virtualPath: String): List<VaultFileEntry> {
        val cleanPath = virtualPath.trim('/').replace('\\', '/')
        return (currentManifest?.files ?: emptyList())
            .filter { it.parentVirtualPath == cleanPath }
            .sortedWith(compareBy<VaultFileEntry> { !it.isDirectory }.thenBy { it.originalName.lowercase() })
    }

    /**
     * Returns all virtual directories in the Vault for folder picking.
     */
    fun getAllFolders(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        list.add("" to "Vault Root")
        val folders = (currentManifest?.files ?: emptyList()).filter { it.isDirectory }
        folders.sortedBy { it.parentVirtualPath + "/" + it.originalName }.forEach { folder ->
            val fullPath = if (folder.parentVirtualPath.isEmpty()) folder.originalName else "${folder.parentVirtualPath}/${folder.originalName}"
            list.add(fullPath to folder.originalName)
        }
        return list
    }

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
     * Creates a new virtual folder inside the vault.
     */
    suspend fun createFolder(
        folderName: String,
        parentVirtualPath: String = ""
    ): Result<VaultFileEntry> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            val cleanName = folderName.trim().replace('/', '_').replace('\\', '_')
            require(cleanName.isNotEmpty()) { "Folder name cannot be empty" }

            val cleanParent = parentVirtualPath.trim('/').replace('\\', '/')
            val existing = (currentManifest?.files ?: emptyList()).any {
                it.parentVirtualPath == cleanParent && it.originalName.equals(cleanName, ignoreCase = true)
            }
            if (existing) {
                throw IllegalArgumentException("A folder or file named '$cleanName' already exists here")
            }

            val entry = VaultFileEntry(
                id = UUID.randomUUID().toString(),
                originalName = cleanName,
                encryptedFileName = "",
                parentVirtualPath = cleanParent,
                isDirectory = true,
                size = 0L,
                mimeType = "inode/directory",
                addedDate = System.currentTimeMillis()
            )

            val updatedList = (currentManifest?.files ?: emptyList()) + entry
            val updatedManifest = VaultManifest(updatedList)
            saveManifest(updatedManifest, key)
            currentManifest = updatedManifest

            entry
        }
    }

    /**
     * Renames a vault file or virtual folder.
     */
    suspend fun renameEntry(
        entryId: String,
        newName: String
    ): Result<VaultFileEntry> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            val cleanName = newName.trim().replace('/', '_').replace('\\', '_')
            require(cleanName.isNotEmpty()) { "Name cannot be empty" }

            val all = currentManifest?.files ?: emptyList()
            val target = all.find { it.id == entryId } ?: throw IllegalArgumentException("Entry not found: $entryId")

            val conflict = all.any {
                it.id != entryId && it.parentVirtualPath == target.parentVirtualPath && it.originalName.equals(cleanName, ignoreCase = true)
            }
            if (conflict) {
                throw IllegalArgumentException("An item named '$cleanName' already exists here")
            }

            val oldVirtualPath = if (target.parentVirtualPath.isEmpty()) target.originalName else "${target.parentVirtualPath}/${target.originalName}"
            val newVirtualPath = if (target.parentVirtualPath.isEmpty()) cleanName else "${target.parentVirtualPath}/$cleanName"

            val updatedList = all.map { entry ->
                when {
                    entry.id == entryId -> entry.copy(
                        originalName = cleanName,
                        lastModified = System.currentTimeMillis()
                    )
                    entry.parentVirtualPath == oldVirtualPath -> entry.copy(
                        parentVirtualPath = newVirtualPath
                    )
                    entry.parentVirtualPath.startsWith("$oldVirtualPath/") -> entry.copy(
                        parentVirtualPath = newVirtualPath + entry.parentVirtualPath.removePrefix(oldVirtualPath)
                    )
                    else -> entry
                }
            }

            val updatedManifest = VaultManifest(updatedList)
            saveManifest(updatedManifest, key)
            currentManifest = updatedManifest

            updatedList.first { it.id == entryId }
        }
    }

    /**
     * Moves vault files or folders to a new target virtual directory.
     */
    suspend fun moveEntries(
        entryIds: Set<String>,
        targetVirtualPath: String
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            val cleanTarget = targetVirtualPath.trim('/').replace('\\', '/')

            val all = currentManifest?.files ?: emptyList()
            val updatedList = all.map { entry ->
                if (entryIds.contains(entry.id)) {
                    entry.copy(
                        parentVirtualPath = cleanTarget,
                        lastModified = System.currentTimeMillis()
                    )
                } else {
                    entry
                }
            }

            val updatedManifest = VaultManifest(updatedList)
            saveManifest(updatedManifest, key)
            currentManifest = updatedManifest
        }
    }

    /**
     * Imports files into the encrypted vault under a target virtual directory.
     */
    suspend fun importFiles(
        sources: List<File>,
        targetVirtualPath: String = "",
        deleteOriginals: Boolean = false,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ): Result<List<VaultFileEntry>> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            dataDir.mkdirs()

            val cleanParent = targetVirtualPath.trim('/').replace('\\', '/')
            val importedEntries = mutableListOf<VaultFileEntry>()
            var totalBytesRead = 0L

            sources.forEach { sourceFile ->
                if (sourceFile.exists() && sourceFile.isFile) {
                    val fileId = UUID.randomUUID().toString()
                    val encFileName = "$fileId.enc"
                    val encFile = File(dataDir, encFileName)

                    cryptoEngine.encryptFile(
                        sourceFile = sourceFile,
                        destinationFile = encFile,
                        key = key,
                        onProgress = { bytes ->
                            totalBytesRead += bytes
                            onProgress?.invoke(totalBytesRead)
                        }
                    )

                    val entry = VaultFileEntry(
                        id = fileId,
                        originalName = sourceFile.name,
                        encryptedFileName = encFileName,
                        parentVirtualPath = cleanParent,
                        isDirectory = false,
                        size = sourceFile.length(),
                        mimeType = MimeTypeResolver.getMimeType(sourceFile.extension) ?: "*/*",
                        addedDate = System.currentTimeMillis()
                    )
                    importedEntries.add(entry)

                    if (deleteOriginals) {
                        sourceFile.delete()
                    }
                }
            }

            val updatedList = (currentManifest?.files ?: emptyList()) + importedEntries
            val updatedManifest = VaultManifest(updatedList)
            saveManifest(updatedManifest, key)
            currentManifest = updatedManifest

            importedEntries
        }
    }

    /**
     * Decrypts an encrypted vault entry directly into RAM for secure previewing without writing to disk.
     */
    suspend fun decryptToMemory(entry: VaultFileEntry): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            val encFile = File(dataDir, entry.encryptedFileName)
            if (!encFile.exists()) {
                throw IllegalStateException("Encrypted payload missing: ${encFile.path}")
            }

            val byteOut = ByteArrayOutputStream()
            FileInputStream(encFile).use { fis ->
                cryptoEngine.aead.decryptStream(fis, byteOut, key, ioDispatcher)
            }
            byteOut.toByteArray()
        }
    }

    /**
     * Exports selected entries (files and recursively folders) to an external destination directory.
     */
    suspend fun exportEntries(
        entryIds: Set<String>,
        destinationDir: File,
        onProgress: ((bytesWritten: Long) -> Unit)? = null
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            destinationDir.mkdirs()

            val all = currentManifest?.files ?: emptyList()
            val entriesToExport = all.filter { entryIds.contains(it.id) }
            var exportedCount = 0

            suspend fun exportItem(entry: VaultFileEntry, targetFolder: File) {
                if (entry.isDirectory) {
                    val subFolder = File(targetFolder, entry.originalName).apply { mkdirs() }
                    val fullVirtualPath = if (entry.parentVirtualPath.isEmpty()) entry.originalName else "${entry.parentVirtualPath}/${entry.originalName}"
                    val children = all.filter { it.parentVirtualPath == fullVirtualPath }
                    children.forEach { child ->
                        exportItem(child, subFolder)
                    }
                    exportedCount++
                } else {
                    val encFile = File(dataDir, entry.encryptedFileName)
                    if (encFile.exists()) {
                        var destFile = File(targetFolder, entry.originalName)
                        if (destFile.exists()) {
                            val nameWithoutExt = destFile.nameWithoutExtension
                            val ext = destFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }
                            var counter = 1
                            while (destFile.exists()) {
                                destFile = File(targetFolder, "$nameWithoutExt ($counter)$ext")
                                counter++
                            }
                        }

                        cryptoEngine.decryptFile(
                            sourceFile = encFile,
                            destinationFile = destFile,
                            key = key,
                            onProgress = onProgress
                        )
                        exportedCount++
                    }
                }
            }

            entriesToExport.forEach { entry ->
                exportItem(entry, destinationDir)
            }

            exportedCount
        }
    }

    /**
     * Permanently deletes selected entries and all recursive sub-entries from the encrypted vault.
     */
    suspend fun deleteEntries(entryIds: Set<String>): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val key = activeKey ?: throw IllegalStateException("Vault is locked")
            val all = currentManifest?.files ?: emptyList()

            val idsToDelete = mutableSetOf<String>()

            fun collectDeletions(entry: VaultFileEntry) {
                idsToDelete.add(entry.id)
                if (entry.isDirectory) {
                    val fullVirtualPath = if (entry.parentVirtualPath.isEmpty()) entry.originalName else "${entry.parentVirtualPath}/${entry.originalName}"
                    val children = all.filter { it.parentVirtualPath == fullVirtualPath || it.parentVirtualPath.startsWith("$fullVirtualPath/") }
                    children.forEach { collectDeletions(it) }
                }
            }

            all.filter { entryIds.contains(it.id) }.forEach { collectDeletions(it) }

            // Delete encrypted files on disk
            all.filter { idsToDelete.contains(it.id) && !it.isDirectory }.forEach { entry ->
                val encFile = File(dataDir, entry.encryptedFileName)
                if (encFile.exists()) {
                    encFile.delete()
                }
            }

            val updatedList = all.filterNot { idsToDelete.contains(it.id) }
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
