package com.Hrshd1eux.filemanager.feature.power

import com.Hrshd1eux.filemanager.core.backup.engine.BackupEngine
import com.Hrshd1eux.filemanager.core.backup.model.BackupMode
import com.Hrshd1eux.filemanager.core.backup.model.BackupProfile
import com.Hrshd1eux.filemanager.core.backup.model.BackupStatus
import com.Hrshd1eux.filemanager.core.backup.repository.BackupProfileRepository
import com.Hrshd1eux.filemanager.core.checksum.ChecksumAlgorithm
import com.Hrshd1eux.filemanager.core.comparison.FileComparisonEngine
import com.Hrshd1eux.filemanager.core.recovery.OperationRecoveryManager
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.preferences.DestinationHistoryRepository
import com.Hrshd1eux.filemanager.core.storage.util.ComparisonStatus
import com.Hrshd1eux.filemanager.core.storage.util.FolderComparator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class PowerFeaturesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var dirA: File
    private lateinit var dirB: File

    @Before
    fun setUp() {
        dirA = tempFolder.newFolder("dirA")
        dirB = tempFolder.newFolder("dirB")
    }

    // =========================================================================
    // 1.4.34: Destination History Tests
    // =========================================================================

    @Test
    fun destinationHistory_recordsLruOrderAndCapsAtMax() {
        val repo = DestinationHistoryRepository(context = null)
        repo.clear()

        val folders = (1..12).map { i ->
            val f = tempFolder.newFolder("folder_$i")
            StorageNode(id = f.path, name = f.name, path = f.path, isDirectory = true)
        }

        for (folder in folders) {
            repo.recordDestination(folder)
        }

        val recents = repo.recentDestinations.value
        assertEquals(DestinationHistoryRepository.MAX_RECENT_DESTINATIONS, recents.size)
        // Most recent should be folder_12
        assertEquals("folder_12", recents.first().name)
        assertEquals("folder_12", repo.lastMoveDestination.value?.name)

        // Re-recording folder_5 should move it to the front
        val folder5 = folders[4]
        repo.recordDestination(folder5)
        assertEquals("folder_5", repo.recentDestinations.value.first().name)
        assertEquals(DestinationHistoryRepository.MAX_RECENT_DESTINATIONS, repo.recentDestinations.value.size)
    }

    @Test
    fun destinationHistory_removesDestination() {
        val repo = DestinationHistoryRepository(context = null)
        val f1 = tempFolder.newFolder("target_1")
        val f2 = tempFolder.newFolder("target_2")

        val node1 = StorageNode(id = f1.path, name = f1.name, path = f1.path, isDirectory = true)
        val node2 = StorageNode(id = f2.path, name = f2.name, path = f2.path, isDirectory = true)

        repo.recordDestination(node1)
        repo.recordDestination(node2)

        assertEquals(2, repo.recentDestinations.value.size)
        repo.removeDestination(f2.path)

        assertEquals(1, repo.recentDestinations.value.size)
        assertEquals(f1.path, repo.recentDestinations.value.first().path)
        assertEquals(f1.path, repo.lastMoveDestination.value?.path)
    }

    // =========================================================================
    // 1.4.35: Folder Comparison Tests
    // =========================================================================

    @Test
    fun folderComparison_recursiveDirectoryComparison_detectsDifferences() {
        // Prepare dirA:
        // - doc.txt (identical)
        // - sub/nestedA.txt (unique to A)
        // - modified.txt (different size)
        val docA = File(dirA, "doc.txt").apply { writeText("Hello World") }
        val subA = File(dirA, "sub").apply { mkdirs() }
        File(subA, "nestedA.txt").apply { writeText("Nested in A") }
        File(dirA, "modified.txt").apply { writeText("Small content") }

        // Prepare dirB:
        // - doc.txt (identical)
        // - modified.txt (different size)
        // - b_only.txt (unique to B)
        val docB = File(dirB, "doc.txt").apply { writeText("Hello World") }
        File(dirB, "modified.txt").apply { writeText("Significantly larger content here") }
        File(dirB, "b_only.txt").apply { writeText("Only B") }

        docB.setLastModified(docA.lastModified())

        val report = FolderComparator.compareDirectories(dirA, dirB, recursive = true)
        assertFalse(report.isIdentical)
        assertTrue(report.isRecursive)

        // doc.txt should be identical
        val docItem = report.items.find { it.name == "doc.txt" }
        assertNotNull(docItem)
        assertEquals(ComparisonStatus.IDENTICAL, docItem?.status)

        // modified.txt should be MODIFIED
        val modItem = report.items.find { it.name == "modified.txt" }
        assertNotNull(modItem)
        assertEquals(ComparisonStatus.MODIFIED, modItem?.status)

        // sub/nestedA.txt should be UNIQUE_LEFT
        val nestedItem = report.items.find { it.name == "nestedA.txt" }
        assertNotNull(nestedItem)
        assertEquals(ComparisonStatus.UNIQUE_LEFT, nestedItem?.status)

        // b_only.txt should be UNIQUE_RIGHT
        val bOnlyItem = report.items.find { it.name == "b_only.txt" }
        assertNotNull(bOnlyItem)
        assertEquals(ComparisonStatus.UNIQUE_RIGHT, bOnlyItem?.status)
    }

    // =========================================================================
    // 1.4.36: File Comparison Tests
    // =========================================================================

    @Test
    fun fileComparison_identicalFiles_verifiesChecksumMatch() = runTest(testDispatcher) {
        val engine = FileComparisonEngine(ioDispatcher = testDispatcher)
        val file1 = File(dirA, "test.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        val file2 = File(dirB, "test.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        file2.setLastModified(file1.lastModified())

        val result = engine.compareFiles(file1, file2, calculateHash = true, algorithm = ChecksumAlgorithm.SHA_256)
        assertTrue(result.isSuccess)

        val comparison = result.getOrThrow()
        assertTrue(comparison.isIdentical)
        assertTrue(comparison.isSizeMatch)
        assertEquals(true, comparison.isHashMatch)
        assertNotNull(comparison.hash1)
        assertEquals(comparison.hash1, comparison.hash2)
        assertEquals(ChecksumAlgorithm.SHA_256, comparison.hashAlgorithm)
    }

    @Test
    fun fileComparison_differentFiles_detectsMismatch() = runTest(testDispatcher) {
        val engine = FileComparisonEngine(ioDispatcher = testDispatcher)
        val file1 = File(dirA, "sample.txt").apply { writeText("Alpha string") }
        val file2 = File(dirB, "sample.txt").apply { writeText("Beta string!") }

        val result = engine.compareFiles(file1, file2, calculateHash = true)
        assertTrue(result.isSuccess)

        val comparison = result.getOrThrow()
        assertFalse(comparison.isIdentical)
        assertEquals(false, comparison.isHashMatch)
    }

    // =========================================================================
    // 1.4.37: Backup Profile Repository Tests
    // =========================================================================

    @Test
    fun backupProfileRepository_crudAndRunResult() {
        val repo = BackupProfileRepository(context = null)
        val profile = BackupProfile(
            id = "custom_backup_1",
            name = "Project Backup",
            sourcePaths = listOf(dirA.path),
            destinationPath = dirB.path,
            mode = BackupMode.INCREMENTAL
        )

        repo.saveProfile(profile)
        val loaded = repo.getProfileById("custom_backup_1")
        assertNotNull(loaded)
        assertEquals("Project Backup", loaded?.name)

        repo.updateProfileRunResult("custom_backup_1", 12345678L, BackupStatus.SUCCESS, "All good")
        val updated = repo.getProfileById("custom_backup_1")
        assertEquals(12345678L, updated?.lastRunTimestamp)
        assertEquals(BackupStatus.SUCCESS, updated?.lastRunStatus)
        assertEquals("All good", updated?.lastRunSummary)

        repo.deleteProfile("custom_backup_1")
        assertNull(repo.getProfileById("custom_backup_1"))
    }

    // =========================================================================
    // 1.4.38: Backup Engine Tests
    // =========================================================================

    @Test
    fun backupEngine_incrementalBackup_copiesNewAndSkipsUnchanged() = runTest(testDispatcher) {
        val engine = BackupEngine(ioDispatcher = testDispatcher)

        // Setup source files
        val srcFile1 = File(dirA, "photo.jpg").apply { writeBytes(ByteArray(1024) { 1 }) }
        val srcFile2 = File(dirA, "notes.txt").apply { writeText("Original note") }
        val tmpFile = File(dirA, "ignore.tmp").apply { writeText("Temporary garbage") }

        val profile = BackupProfile(
            id = "engine_test_1",
            name = "Test Incremental",
            sourcePaths = listOf(dirA.path),
            destinationPath = dirB.path,
            mode = BackupMode.INCREMENTAL,
            exclusionPatterns = listOf("*.tmp")
        )

        // Run 1: First backup (should copy photo.jpg and notes.txt, exclude ignore.tmp)
        val report1 = engine.executeBackup(profile)
        assertEquals(BackupStatus.SUCCESS, report1.status)
        assertEquals(2, report1.filesCopied)
        assertEquals(0, report1.filesSkipped)
        assertTrue(File(dirB, "photo.jpg").exists())
        assertTrue(File(dirB, "notes.txt").exists())
        assertFalse(File(dirB, "ignore.tmp").exists())

        // Run 2: Without modifying anything (should skip both files)
        val report2 = engine.executeBackup(profile)
        assertEquals(BackupStatus.SUCCESS, report2.status)
        assertEquals(0, report2.filesCopied)
        assertEquals(2, report2.filesSkipped)

        // Modify notes.txt in source
        Thread.sleep(10)
        srcFile2.writeText("Updated note with new content!")

        // Run 3: Should copy 1 modified file and skip 1 unchanged file
        val report3 = engine.executeBackup(profile)
        assertEquals(BackupStatus.SUCCESS, report3.status)
        assertEquals(1, report3.filesCopied)
        assertEquals(1, report3.filesSkipped)
    }

    // =========================================================================
    // 1.4.39: Operation Recovery Tests
    // =========================================================================

    @Test
    fun operationRecovery_scansAndCleansArtifacts() {
        val recovery = OperationRecoveryManager(ioDispatcher = testDispatcher)

        // Create artifacts older than 1000ms
        val oldTimestamp = System.currentTimeMillis() - 5000L
        val tmp1 = File(dirA, "doc.tmp").apply {
            writeText("Temp")
            setLastModified(oldTimestamp)
        }
        val part1 = File(dirA, "archive.zip.part").apply {
            writeText("Partial download")
            setLastModified(oldTimestamp)
        }
        val backupTmp = File(dirA, ".secret.txt.arca_backup_tmp").apply {
            writeText("Backup artifact")
            setLastModified(oldTimestamp)
        }
        val regularFile = File(dirA, "keep_me.txt").apply {
            writeText("Valid regular file")
            setLastModified(oldTimestamp)
        }

        val artifacts = recovery.scanInterruptedArtifacts(dirA, recursive = false)
        assertEquals(3, artifacts.size)

        val deletedCount = recovery.cleanAll(artifacts)
        assertEquals(3, deletedCount)

        assertFalse(tmp1.exists())
        assertFalse(part1.exists())
        assertFalse(backupTmp.exists())
        assertTrue(regularFile.exists())
    }

    @Test
    fun operationRecovery_canResumeAndResumesPartialCopy() = runTest(testDispatcher) {
        val recovery = OperationRecoveryManager(ioDispatcher = testDispatcher)

        val fullSourceData = ByteArray(100 * 1024) { (it % 256).toByte() }
        val sourceFile = File(dirA, "large_source.bin").apply { writeBytes(fullSourceData) }

        // Create partially copied destination (first 40 KB)
        val partialDest = File(dirB, "large_source.bin").apply {
            writeBytes(fullSourceData.copyOfRange(0, 40 * 1024))
        }

        assertTrue(recovery.canResume(sourceFile, partialDest))

        var lastProgressProcessed = 0L
        val resumeResult = recovery.resumeCopy(sourceFile, partialDest) { current, total ->
            lastProgressProcessed = current
        }

        assertTrue(resumeResult.isSuccess)
        assertEquals(fullSourceData.size.toLong(), partialDest.length())
        assertEquals(fullSourceData.size.toLong(), lastProgressProcessed)
        assertTrue(fullSourceData.contentEquals(partialDest.readBytes()))
    }
}
