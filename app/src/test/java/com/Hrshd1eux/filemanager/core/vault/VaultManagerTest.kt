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
        val importResult = vaultManager.importFile(originalFile, deleteOriginal = false)
        assertTrue(importResult.isSuccess)
        val entry = importResult.getOrNull()!!
        assertEquals("financials.pdf", entry.originalName)
        assertEquals(1, vaultManager.getFiles().size)

        // 2. Export file
        val exportResult = vaultManager.exportFile(entry, exportDir)
        assertTrue(exportResult.isSuccess)
        val exportedFile = exportResult.getOrNull()!!
        assertTrue(exportedFile.exists())
        assertEquals("financials.pdf", exportedFile.name)
        assertEquals(sampleContent, exportedFile.readText())

        // 3. Delete file
        val deleteResult = vaultManager.deleteFile(entry)
        assertTrue(deleteResult.isSuccess)
        assertEquals(0, vaultManager.getFiles().size)
    }
}
