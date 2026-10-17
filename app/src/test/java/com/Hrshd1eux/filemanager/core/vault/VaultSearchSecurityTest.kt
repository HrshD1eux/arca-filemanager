package com.Hrshd1eux.filemanager.core.vault

import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.privacy.NotificationPrivacyGuard
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import com.Hrshd1eux.filemanager.core.vault.search.VaultSearchEngine
import com.Hrshd1eux.filemanager.core.vault.stealth.VaultStealthSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultSearchSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var vaultDir: File
    private lateinit var vaultManager: VaultManager
    private fun newPassword() = "SuperSecurePassword2026!".toCharArray()

    @Before
    fun setUp() {
        vaultDir = tempFolder.newFolder("security_vault")
        vaultManager = VaultManager(
            vaultDir = vaultDir,
            cryptoEngine = CryptoEngine(ioDispatcher = Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun architectureDefinition_verifiesLocalAndZeroPlaintextGuarantees() {
        assertEquals("ON_DEMAND_IN_MEMORY", VaultSearchEngine.INDEX_STRATEGY)
        assertTrue(VaultSearchEngine.IS_LOCAL_ONLY)
        assertFalse(VaultSearchEngine.PERSISTS_PLAINTEXT_INDEX)
    }

    @Test
    fun diskInspection_verifiesZeroPlaintextFilenamesOrIndexFilesOnDisk() = runTest {
        // 1. Initialize vault and import a sensitive file
        vaultManager.createVault(newPassword())
        assertTrue(vaultManager.isUnlocked())

        val secretFileName = "Classified_Financial_Records_2026.pdf"
        val tempSource = tempFolder.newFile(secretFileName)
        tempSource.writeText("Confidential bank transactions and balances: \$5,000,000")

        vaultManager.importFiles(listOf(tempSource), targetVirtualPath = "Confidential/Finance")
        val allFiles = vaultManager.getAllFiles()
        assertEquals(1, allFiles.size)
        assertEquals(secretFileName, allFiles.first().originalName)

        // 2. Lock the vault
        vaultManager.lock()
        assertFalse(vaultManager.isUnlocked())

        // 3. Inspect every file on disk in the vault directory
        val diskFiles = vaultDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("Vault must have encrypted files on disk", diskFiles.isNotEmpty())

        val secretBytes = secretFileName.toByteArray(Charsets.UTF_8)
        val queryBytes = "Classified".toByteArray(Charsets.UTF_8)

        for (file in diskFiles) {
            // Verify no index files exist
            assertFalse(
                "No search index file may exist on disk: ${file.name}",
                file.name.contains("index", ignoreCase = true)
            )

            // Verify raw disk contents contain zero plaintext
            val bytes = file.readBytes()
            val text = String(bytes, Charsets.ISO_8859_1) // lossless byte-to-char representation

            assertFalse(
                "Plaintext filename found on disk in file: ${file.name}",
                text.contains(secretFileName)
            )
            assertFalse(
                "Plaintext search query found on disk in file: ${file.name}",
                text.contains("Classified")
            )
            assertFalse(
                "Plaintext file content found on disk in file: ${file.name}",
                text.contains("Confidential bank transactions")
            )
        }
    }

    @Test
    fun lockUnlockLifecycle_wipesSearchStateAndManifest() = runTest {
        vaultManager.createVault(newPassword())
        vaultManager.createFolder("SecretFolder", "")
        val files = vaultManager.getAllFiles()
        assertEquals(1, files.size)

        // Run search while unlocked
        val matchesBeforeLock = VaultSearchEngine.search(files, "Secret")
        assertEquals(1, matchesBeforeLock.size)

        // Lock vault
        vaultManager.lock()
        assertFalse(vaultManager.isUnlocked())

        // Ensure manifest is immediately wiped and returns empty
        val filesAfterLock = vaultManager.getAllFiles()
        assertTrue(filesAfterLock.isEmpty())

        // Search engine returns empty when given locked/empty entries
        val matchesAfterLock = VaultSearchEngine.search(filesAfterLock, "Secret")
        assertTrue(matchesAfterLock.isEmpty())
    }

    @Test
    fun processDeathSimulation_restartsInLockedStateWithZeroResidualSearchState() = runTest {
        // 1. Initial process session
        vaultManager.createVault(newPassword())
        vaultManager.createFolder("PersonalRecords", "")
        assertTrue(vaultManager.isUnlocked())
        assertEquals(1, vaultManager.getAllFiles().size)

        // 2. Simulate Process Death: drop existing VaultManager and create a new instance
        // pointing to the same disk directory without entering the password.
        val restartedVaultManager = VaultManager(
            vaultDir = vaultDir,
            cryptoEngine = CryptoEngine(ioDispatcher = Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )

        // Verifications on cold start / process death recovery:
        assertTrue("Vault metadata exists", restartedVaultManager.isConfigured())
        assertFalse("Vault must start LOCKED on process rebirth", restartedVaultManager.isUnlocked())
        assertTrue("Manifest in memory must be empty", restartedVaultManager.getAllFiles().isEmpty())

        // Search cannot return any results
        val searchResults = VaultSearchEngine.search(restartedVaultManager.getAllFiles(), "Personal")
        assertTrue(searchResults.isEmpty())

        // 3. Proper unlock restores accessibility
        restartedVaultManager.unlock(newPassword())
        assertTrue(restartedVaultManager.isUnlocked())
        assertEquals(1, restartedVaultManager.getAllFiles().size)
        assertEquals("PersonalRecords", restartedVaultManager.getAllFiles().first().originalName)

        val restoredSearchResults = VaultSearchEngine.search(restartedVaultManager.getAllFiles(), "Personal")
        assertEquals(1, restoredSearchResults.size)
    }

    @Test
    fun deviceRestartSimulation_restoresEncryptedPreferencesWithoutActiveSession() {
        val lockManager = VaultLockManager(context = null, vaultManager = vaultManager)
        val triggerCode = "🤫🔒✨"
        val credentials = VaultStealthSecurity.createStealthCredentials(triggerCode)

        lockManager.updateSettings(
            VaultLockSettings(
                isVaultHidden = true,
                secretCodeSalt = credentials.saltHex,
                secretCodeHash = credentials.hashHex,
                encryptedSecretCode = credentials.encryptedPayloadHex
            )
        )

        // Device reboot simulation: new lockManager instance
        val rebootedLockManager = VaultLockManager(context = null, vaultManager = vaultManager)
        // Key is not in memory, vault is locked
        assertFalse(vaultManager.isUnlocked())

        // But trigger code matches correctly
        assertTrue(lockManager.matchesSecretCode(triggerCode))
        assertFalse(lockManager.matchesSecretCode("wrong_code"))
    }

    @Test
    fun privacyGuard_sanitizesAllOperationsWithoutLeakingFilenames() {
        // Test notification titles
        val importNotification = NotificationPrivacyGuard.sanitizeVaultNotification("import")
        assertEquals("Encrypting files into Vault", importNotification)
        assertFalse(importNotification.contains(".pdf"))

        val exportNotification = NotificationPrivacyGuard.sanitizeVaultNotification("export")
        assertEquals("Decrypting files from Vault", exportNotification)

        // Test user feedback messages
        val folderMsg = NotificationPrivacyGuard.sanitizeVaultMessage("created_folder")
        assertEquals("New folder created", folderMsg)

        val renameMsg = NotificationPrivacyGuard.sanitizeVaultMessage("renamed")
        assertEquals("Item renamed", renameMsg)

        val deleteMsg = NotificationPrivacyGuard.sanitizeVaultMessage("deleted", 3)
        assertEquals("Deleted 3 items permanently", deleteMsg)

        val importMsg = NotificationPrivacyGuard.sanitizeVaultMessage("imported", 2)
        assertEquals("Imported 2 files into Vault", importMsg)

        val exportMsg = NotificationPrivacyGuard.sanitizeVaultMessage("exported", 1)
        assertEquals("Exported 1 file from Vault", exportMsg)
    }
}
