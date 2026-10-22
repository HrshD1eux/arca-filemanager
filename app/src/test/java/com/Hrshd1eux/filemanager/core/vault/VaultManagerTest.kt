package com.Hrshd1eux.filemanager.core.vault

import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
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

class VaultManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var vaultManager: VaultManager
    private lateinit var vaultDir: File
    private lateinit var cryptoEngine: CryptoEngine

    @Before
    fun setUp() {
        vaultDir = tempFolder.newFolder("test_vault")
        cryptoEngine = CryptoEngine(ioDispatcher = Dispatchers.Unconfined)
        vaultManager = VaultManager(
            vaultDir = vaultDir,
            cryptoEngine = cryptoEngine,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun vaultLifecycle_configurationAndUnlock() = runTest {
        assertFalse(vaultManager.isConfigured())
        assertFalse(vaultManager.isUnlocked())

        val password = "StrongVaultPassword123!".toCharArray()

        // 1. Create Vault
        val createResult = vaultManager.createVault(password)
        assertTrue(createResult.isSuccess)
        assertTrue(vaultManager.isConfigured())
        assertTrue(vaultManager.isUnlocked())

        // 2. Lock Vault
        vaultManager.lock()
        assertFalse(vaultManager.isUnlocked())

        // 3. Unlock with correct password
        val unlockResult = vaultManager.unlock("StrongVaultPassword123!".toCharArray())
        assertTrue(unlockResult.isSuccess)
        assertTrue(vaultManager.isUnlocked())

        // 4. Lock & attempt unlock with wrong password
        vaultManager.lock()
        val wrongUnlockResult = vaultManager.unlock("WrongPassword999!".toCharArray())
        assertFalse(wrongUnlockResult.isSuccess)
        assertFalse(vaultManager.isUnlocked())
    }

    @Test
    fun vaultFileOperations_importExportDelete() = runTest {
        val password = "MasterVaultPassword!".toCharArray()
        vaultManager.createVault(password)

        val sampleContent = "Confidential Banking & Identity Records — 100% Offline Vault"
        val originalFile = tempFolder.newFile("financials.pdf").apply {
            writeText(sampleContent)
        }
        val exportDir = tempFolder.newFolder("exported_files")

        // 1. Import file
        val importResult = vaultManager.importFiles(listOf(originalFile), targetVirtualPath = "", deleteOriginals = false)
        assertTrue(importResult.isSuccess)
        val imported = importResult.getOrNull()!!
        assertEquals(1, imported.size)
        val entry = imported.first()
        assertEquals("financials.pdf", entry.originalName)
        assertEquals(1, vaultManager.getAllFiles().size)

        // 2. In-Memory Preview Decryption
        val previewResult = vaultManager.decryptToMemory(entry)
        assertTrue(previewResult.isSuccess)
        val previewBytes = previewResult.getOrNull()!!
        assertEquals(sampleContent, String(previewBytes, Charsets.UTF_8))

        // 3. Export file
        val exportResult = vaultManager.exportEntries(setOf(entry.id), exportDir)
        assertTrue(exportResult.isSuccess)
        assertEquals(1, exportResult.getOrNull()!!)
        val exportedFile = File(exportDir, "financials.pdf")
        assertTrue(exportedFile.exists())
        assertEquals(sampleContent, exportedFile.readText())

        // 4. Delete file
        val deleteResult = vaultManager.deleteEntries(setOf(entry.id))
        assertTrue(deleteResult.isSuccess)
        assertEquals(0, vaultManager.getAllFiles().size)
    }

    @Test
    fun vaultOrganization_foldersMoveAndRename() = runTest {
        val password = "SecureOrganizationPassword!".toCharArray()
        vaultManager.createVault(password)

        // 1. Create Folders
        val folderResult = vaultManager.createFolder("Work", parentVirtualPath = "")
        assertTrue(folderResult.isSuccess)
        val workFolder = folderResult.getOrNull()!!
        assertTrue(workFolder.isDirectory)

        val subFolderResult = vaultManager.createFolder("Contracts", parentVirtualPath = "Work")
        assertTrue(subFolderResult.isSuccess)
        val contractsFolder = subFolderResult.getOrNull()!!
        assertEquals("Work", contractsFolder.parentVirtualPath)

        // 2. Import file into root
        val file1 = tempFolder.newFile("agreement.txt").apply { writeText("Agreement terms") }
        val importResult = vaultManager.importFiles(listOf(file1), targetVirtualPath = "")
        assertTrue(importResult.isSuccess)
        val agreementEntry = importResult.getOrNull()!!.first()

        // 3. Move file into Work/Contracts
        val moveResult = vaultManager.moveEntries(setOf(agreementEntry.id), targetVirtualPath = "Work/Contracts")
        assertTrue(moveResult.isSuccess)

        val entriesInContracts = vaultManager.getEntriesInPath("Work/Contracts")
        assertEquals(1, entriesInContracts.size)
        assertEquals("agreement.txt", entriesInContracts.first().originalName)

        // 4. Rename file
        val renameResult = vaultManager.renameEntry(agreementEntry.id, "final_agreement.txt")
        assertTrue(renameResult.isSuccess)
        val renamed = renameResult.getOrNull()!!
        assertEquals("final_agreement.txt", renamed.originalName)

        // 5. Recursive Delete of folder "Work"
        val deleteFolderResult = vaultManager.deleteEntries(setOf(workFolder.id))
        assertTrue(deleteFolderResult.isSuccess)
        assertEquals(0, vaultManager.getAllFiles().size)
    }

    @Test
    fun vaultDelete_wipesAllDataAndResetsToUnconfigured() = runTest {
        val password = "MasterVaultPassword!".toCharArray()
        vaultManager.createVault(password)
        assertTrue(vaultManager.isConfigured())
        assertTrue(vaultManager.isUnlocked())

        val sampleFile = tempFolder.newFile("secret.txt").apply { writeText("super confidential") }
        vaultManager.importFiles(listOf(sampleFile))
        assertEquals(1, vaultManager.getAllFiles().size)

        // Delete Vault
        val deleteResult = vaultManager.deleteVault()
        assertTrue(deleteResult.isSuccess)
        assertFalse(vaultManager.isConfigured())
        assertFalse(vaultManager.isUnlocked())
        assertEquals(0, vaultManager.getAllFiles().size)
    }

    @Test
    fun vaultChangePassword_reEncryptsDataAndAllowsNewUnlock() = runTest {
        val oldPassword = "OldPassword123!".toCharArray()
        val newPassword = "NewPassword456@!".toCharArray()

        vaultManager.createVault(oldPassword)

        val sampleContent = "Encrypted Financial Records 2026"
        val sampleFile = tempFolder.newFile("records.txt").apply { writeText(sampleContent) }
        val importRes = vaultManager.importFiles(listOf(sampleFile))
        val entry = importRes.getOrNull()!!.first()

        // Change password
        val changeRes = vaultManager.changePassword(
            oldPassword = "OldPassword123!".toCharArray(),
            newPassword = "NewPassword456@!".toCharArray()
        )
        assertTrue(changeRes.isSuccess)
        assertTrue(vaultManager.isUnlocked())

        // Lock and unlock with old password -> should fail
        vaultManager.lock()
        val oldUnlock = vaultManager.unlock("OldPassword123!".toCharArray())
        assertFalse(oldUnlock.isSuccess)

        // Unlock with new password -> should succeed
        val newUnlock = vaultManager.unlock("NewPassword456@!".toCharArray())
        assertTrue(newUnlock.isSuccess)
        assertEquals(1, vaultManager.getAllFiles().size)

        // Verify data decrypts accurately with new password
        val preview = vaultManager.decryptToMemory(vaultManager.getAllFiles().first())
        assertTrue(preview.isSuccess)
        assertEquals(sampleContent, String(preview.getOrNull()!!, Charsets.UTF_8))
    }
}
