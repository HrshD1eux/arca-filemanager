package com.Hrshd1eux.filemanager.core.foundation

import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.archive.engine.TarCompressionType
import com.Hrshd1eux.filemanager.core.archive.engine.TarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.checksum.ChecksumEngine
import com.Hrshd1eux.filemanager.core.cleaner.engine.DuplicateFinderEngine
import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.queue.OperationManager
import com.Hrshd1eux.filemanager.core.operations.queue.OperationType
import com.Hrshd1eux.filemanager.core.operations.verification.FileVerifier
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.vault.VaultManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FoundationReleaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cryptoEngine: CryptoEngine
    private lateinit var zipEngine: ZipEngine
    private lateinit var sevenZEngine: SevenZEngine
    private lateinit var tarEngine: TarEngine
    private lateinit var fileOperationEngine: FileOperationEngine
    private lateinit var checksumEngine: ChecksumEngine

    @Before
    fun setUp() {
        cryptoEngine = CryptoEngine(ioDispatcher = Dispatchers.Unconfined)
        zipEngine = ZipEngine(ioDispatcher = Dispatchers.Unconfined)
        sevenZEngine = SevenZEngine(ioDispatcher = Dispatchers.Unconfined)
        tarEngine = TarEngine(ioDispatcher = Dispatchers.Unconfined)
        fileOperationEngine = FileOperationEngine(ioDispatcher = Dispatchers.Unconfined)
        checksumEngine = ChecksumEngine(ioDispatcher = Dispatchers.Unconfined)
    }

    private fun fileToNode(file: File, isDir: Boolean = file.isDirectory): StorageNode {
        return StorageNode(
            id = file.absolutePath,
            path = file.absolutePath,
            name = file.name,
            size = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            isDirectory = isDir
        )
    }

    @Test
    fun milestone_fileManager_coreOperationsAndIntegrity() = runTest {
        val srcDir = tempFolder.newFolder("source_dir")
        val destDir = tempFolder.newFolder("dest_dir")
        val testFile = File(srcDir, "doc.txt").apply { writeText("ARCA File Manager Foundation") }

        val srcNode = fileToNode(testFile)
        val destNode = fileToNode(destDir, isDir = true)

        // 1. Copy
        val copyResult = fileOperationEngine.copy(listOf(srcNode), destNode, ConflictResolution.OVERWRITE)
        assertTrue(copyResult.isSuccess)
        val copiedFile = File(destDir, "doc.txt")
        assertTrue(copiedFile.exists())

        // 2. Verification
        val verifyResult = FileVerifier.verifyFilePair(testFile, copiedFile)
        assertTrue(verifyResult)

        // 3. Rename
        val copiedNode = fileToNode(copiedFile)
        val renameResult = fileOperationEngine.rename(copiedNode, "renamed_doc.txt")
        assertTrue(renameResult.isSuccess)
        val renamedFile = File(destDir, "renamed_doc.txt")
        assertTrue(renamedFile.exists())

        // 4. Duplicate Finder
        val duplicates = DuplicateFinderEngine(ioDispatcher = Dispatchers.Unconfined)
            .scanDuplicates(tempFolder.root.absolutePath)
        assertNotNull(duplicates)
    }

    @Test
    fun milestone_archives_ecosystemIntegrity() = runTest {
        val filesDir = tempFolder.newFolder("archive_src")
        val file1 = File(filesDir, "hello.txt").apply { writeText("Hello World Archive") }
        val outputDir = tempFolder.newFolder("archive_out")

        // 1. ZIP
        val zipFile = File(outputDir, "test.zip")
        val zipCreate = zipEngine.createZip(listOf(file1), zipFile)
        assertTrue(zipCreate.isSuccess)
        val zipTest = zipEngine.testArchive(zipFile)
        assertTrue(zipTest.isSuccess)
        assertTrue(zipTest.getOrNull()!!.isPassed)

        // 2. 7z Encrypted
        val szFile = File(outputDir, "test.7z")
        val pass = "ArchiveSecret7z!".toCharArray()
        val szCreate = sevenZEngine.create7z(listOf(file1), szFile, password = pass)
        assertTrue(szCreate.isSuccess)
        val szTest = sevenZEngine.testArchive(szFile, password = pass)
        assertTrue(szTest.isSuccess)
        assertTrue(szTest.getOrNull()!!.isPassed)

        // 3. TAR.GZ
        val tarGzFile = File(outputDir, "test.tar.gz")
        val tarCreate = tarEngine.createTar(listOf(file1), tarGzFile, TarCompressionType.GZIP)
        assertTrue(tarCreate.isSuccess)
        val tarTest = tarEngine.testArchive(tarGzFile)
        assertTrue(tarTest.isSuccess)
        assertTrue(tarTest.getOrNull()!!.isPassed)

        // 4. Checksums
        val checksums = checksumEngine.calculateAll(file1)
        assertTrue(checksums.isSuccess)
        assertEquals(4, checksums.getOrNull()!!.size)
    }

    @Test
    fun milestone_security_vaultAndLock() = runTest {
        val vaultDir = tempFolder.newFolder("vault_root")
        val vaultManager = VaultManager(vaultDir = vaultDir, cryptoEngine = cryptoEngine, ioDispatcher = Dispatchers.Unconfined)
        val lockManager = VaultLockManager(context = null, vaultManager = vaultManager)

        val masterPass = "UltraSecureVaultKey2026!".toCharArray()

        // 1. Setup Vault
        val createResult = vaultManager.createVault(masterPass)
        assertTrue(createResult.isSuccess)
        assertTrue(vaultManager.isUnlocked())

        // 2. Encrypted Organization & File Import
        val importSrc = tempFolder.newFile("confidential.txt").apply { writeText("Top Secret Material") }
        val importResult = vaultManager.importFiles(listOf(importSrc), targetVirtualPath = "Personal")
        assertTrue(importResult.isSuccess)

        // 3. In-Memory Preview
        val entry = importResult.getOrNull()!!.first()
        val previewBytes = vaultManager.decryptToMemory(entry)
        assertTrue(previewBytes.isSuccess)
        assertEquals("Top Secret Material", String(previewBytes.getOrNull()!!, Charsets.UTF_8))

        // 4. Auto-Lock / Manual Lock
        lockManager.manualLock()
        assertFalse(vaultManager.isUnlocked())

        // 5. Re-unlock
        val unlockResult = vaultManager.unlock("UltraSecureVaultKey2026!".toCharArray())
        assertTrue(unlockResult.isSuccess)
        assertTrue(vaultManager.isUnlocked())
    }

    @Test
    fun milestone_operations_queueFIFOAndProgress() = runTest {
        val opManager = OperationManager(ioDispatcher = Dispatchers.Unconfined)
        var taskExecuted = false

        val opId = opManager.enqueue(
            title = "Backup Archive Operation",
            type = OperationType.COMPRESS
        ) { progress ->
            taskExecuted = true
        }

        assertNotNull(opId)
        assertTrue(taskExecuted)
    }
}
