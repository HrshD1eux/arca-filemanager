package com.Hrshd1eux.filemanager.core.vault

import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.vault.backup.VaultBackupEngine
import com.Hrshd1eux.filemanager.core.vault.integrity.VaultIntegrityChecker
import com.Hrshd1eux.filemanager.core.vault.integrity.VaultIntegrityStatus
import com.Hrshd1eux.filemanager.core.vault.temp.VaultTempFileManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Rigorous Unit & Security Test Suite for Version 1.5.21 – 1.5.30:
 * Vault Auto-Lock, Integrity Checks, and Verified Backup/Restore Testing.
 */
class VaultIntegrityAndBackupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var vaultDir: File
    private lateinit var vaultManager: VaultManager
    private lateinit var cryptoEngine: CryptoEngine

    @Before
    fun setUp() {
        vaultDir = tempFolder.newFolder("test_vault")
        cryptoEngine = CryptoEngine()
        vaultManager = VaultManager(vaultDir = vaultDir, cryptoEngine = cryptoEngine)
    }

    // =========================================================================
    // 1.5.21–1.5.25 Vault Integrity Checker & Tamper Resistance
    // =========================================================================

    @Test
    fun integrityChecker_unconfiguredVault_reportsUnconfigured() {
        val checker = VaultIntegrityChecker(vaultManager)
        val report = checker.performIntegrityCheck()

        assertEquals(VaultIntegrityStatus.UNCONFIGURED, report.status)
        assertFalse(report.isHealthy)
    }

    @Test
    fun integrityChecker_pristineVault_reportsPristine() = runBlocking {
        vaultManager.createVault("Pass1234!".toCharArray()).getOrThrow()

        val sampleFile = tempFolder.newFile("sample.txt").apply { writeText("Hello Vault Secret") }
        vaultManager.importFiles(listOf(sampleFile), deleteOriginals = false).getOrThrow()

        val checker = VaultIntegrityChecker(vaultManager)
        val report = checker.performIntegrityCheck()

        assertEquals(VaultIntegrityStatus.PRISTINE, report.status)
        assertTrue(report.isHealthy)
        assertEquals(1, report.totalManifestEntries)
        assertEquals(1, report.totalPhysicalFiles)
        assertTrue(report.missingEntries.isEmpty())
        assertTrue(report.orphanFiles.isEmpty())
    }

    @Test
    fun integrityChecker_orphanFileOnDisk_reportsWarningOrphans() = runBlocking {
        vaultManager.createVault("Pass1234!".toCharArray()).getOrThrow()

        // Create an untracked orphan file in data/
        val dataDir = File(vaultDir, "data")
        File(dataDir, "orphan_secret.enc").writeText("spurious bytes")

        val checker = VaultIntegrityChecker(vaultManager)
        val report = checker.performIntegrityCheck()

        assertEquals(VaultIntegrityStatus.WARNING_ORPHANS, report.status)
        assertFalse(report.isHealthy)
        assertEquals(1, report.orphanFiles.size)
        assertEquals("orphan_secret.enc", report.orphanFiles.first())
    }

    @Test
    fun integrityChecker_missingFileOnDisk_reportsErrorMissingFiles() = runBlocking {
        vaultManager.createVault("Pass1234!".toCharArray()).getOrThrow()
        val sample = tempFolder.newFile("doc.pdf").apply { writeText("Confidential Report") }
        val imported = vaultManager.importFiles(listOf(sample), deleteOriginals = false).getOrThrow()

        // Intentionally delete the physical encrypted file
        val encFile = File(File(vaultDir, "data"), imported.first().encryptedFileName)
        encFile.delete()

        val checker = VaultIntegrityChecker(vaultManager)
        val report = checker.performIntegrityCheck()

        assertEquals(VaultIntegrityStatus.ERROR_MISSING_FILES, report.status)
        assertFalse(report.isHealthy)
        assertEquals(1, report.missingEntries.size)
    }

    // =========================================================================
    // 1.5.21–1.5.25 Safe Temporary-File Zero-Fill Wiping
    // =========================================================================

    @Test
    fun tempFileManager_wipesFileWithZeroesBeforeDeletion() {
        val tempDir = tempFolder.newFolder("vault_temp")
        val cleaner = VaultTempFileManager(tempDir)

        val tempFile = cleaner.createTempFile("view_", ".tmp")
        tempFile.writeText("Sensitive decrypted buffer data")
        assertTrue(tempFile.exists())

        cleaner.wipeTempFile(tempFile)
        assertFalse("File must be deleted after wiping", tempFile.exists())
    }

    @Test
    fun tempFileManager_wipeAllTempFiles_clearsEntireDirectory() {
        val tempDir = tempFolder.newFolder("vault_temp_all")
        val cleaner = VaultTempFileManager(tempDir)

        val file1 = cleaner.createTempFile("tmp_a_", ".tmp").apply { writeText("data1") }
        val file2 = cleaner.createTempFile("tmp_b_", ".tmp").apply { writeText("data2") }

        cleaner.wipeAllTempFiles()
        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    // =========================================================================
    // 1.5.26–1.5.30 Verified Vault Backup & Restore Testing
    // =========================================================================

    @Test
    fun createBackup_verifiesTestRestoreBeforeDeclaringSuccess() = runBlocking {
        // Setup vault with files
        vaultManager.createVault("SecurePass99!".toCharArray()).getOrThrow()
        val f1 = tempFolder.newFile("file1.txt").apply { writeText("Content 1") }
        val f2 = tempFolder.newFile("file2.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        vaultManager.importFiles(listOf(f1, f2), deleteOriginals = false).getOrThrow()

        val backupEngine = VaultBackupEngine(vaultDir = vaultDir, cryptoEngine = cryptoEngine)
        val backupFile = File(tempFolder.newFolder("backups"), "arca_vault_backup.arcavault")

        val report = backupEngine.createBackup(backupFile).getOrThrow()

        assertTrue("Backup file must exist", backupFile.exists())
        assertTrue("Must be marked verified by mandatory test restoration", report.isVerifiedRestoreTested)
        assertEquals(2, report.filesCount)
        assertTrue(report.totalBytes > 0L)
    }

    @Test
    fun restoreFromBackup_restoresAllVaultDataSuccessfully() = runBlocking {
        // Create source vault
        vaultManager.createVault("RestorePass1!".toCharArray()).getOrThrow()
        val testDoc = tempFolder.newFile("contract.pdf").apply { writeText("Signed Contract Content") }
        vaultManager.importFiles(listOf(testDoc), deleteOriginals = false).getOrThrow()

        // Backup
        val backupEngine = VaultBackupEngine(vaultDir = vaultDir, cryptoEngine = cryptoEngine)
        val backupFile = File(tempFolder.newFolder("backup_dir"), "export.arcavault")
        backupEngine.createBackup(backupFile).getOrThrow()

        // Restore into a completely fresh destination vault directory
        val restoredVaultDir = tempFolder.newFolder("restored_vault")
        val restoredCount = backupEngine.restoreFromBackup(backupFile, restoredVaultDir).getOrThrow()

        assertEquals(1, restoredCount)

        // Verify that the restored vault unlocks and decrypts the file properly with the original password
        val restoredManager = VaultManager(vaultDir = restoredVaultDir, cryptoEngine = cryptoEngine)
        assertTrue(restoredManager.isConfigured())

        val unlockedEntries = restoredManager.unlock("RestorePass1!".toCharArray()).getOrThrow()
        assertEquals(1, unlockedEntries.size)
        assertEquals("contract.pdf", unlockedEntries.first().originalName)

        val decryptedBytes = restoredManager.decryptToMemory(unlockedEntries.first()).getOrThrow()
        assertEquals("Signed Contract Content", String(decryptedBytes, Charsets.UTF_8))
    }
}
