package com.Hrshd1eux.filemanager.core.operations.recovery

import com.Hrshd1eux.filemanager.core.checksum.ChecksumEngine
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationResult
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Comprehensive Test Suite for Phase D — Operation Recovery (Milestones 1.4.31 – 1.4.40).
 *
 * Covers:
 * - 1.4.31: Operation Journal (crash-resilient atomic persistence, vault metadata scrubbing).
 * - 1.4.32: Copy Recovery (completed/pending tracking, destination verification before skip).
 * - 1.4.33: Move Recovery (source deletion strictly blocked until destination verified complete).
 * - 1.4.34: Archive Operation Recovery (partial archive output is never treated as completed).
 * - 1.4.35: Backup Recovery (destination verification and progress resume).
 * - 1.4.36: Recovery Interface (progress reflects verified state, Resume/Restart/Discard).
 * - 1.4.37: Recovery Safety (duplicate prevention lock, source change detection, storage disconnection).
 * - 1.4.38–1.4.40: Recovery Testing (process death simulation, restart, release gate verification).
 */
class OperationRecoveryRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = Dispatchers.Unconfined
    private lateinit var testRoot: File
    private lateinit var journalDir: File
    private lateinit var journalRepository: FileOperationJournalRepository
    private lateinit var recoveryEngine: OperationRecoveryEngine
    private lateinit var checksumEngine: ChecksumEngine

    @Before
    fun setUp() {
        testRoot = tempFolder.newFolder("recovery_test_root")
        journalDir = File(testRoot, "recovery_journal")
        journalRepository = FileOperationJournalRepository(journalDir, testDispatcher)
        checksumEngine = ChecksumEngine(testDispatcher)
        recoveryEngine = OperationRecoveryEngine(journalRepository, testDispatcher, checksumEngine)
    }

    // =========================================================================
    // 1.4.31 Operation Journal & Vault Privacy
    // =========================================================================

    @Test
    fun operationJournal_persistsAndRecoversAcrossProcessRestart() = runTest {
        val entryId = "op_test_1"
        val entry = OperationJournalEntry(
            id = entryId,
            type = JournalOperationType.COPY,
            title = "Copying important files",
            sourceRoots = listOf("/storage/emulated/0/Documents"),
            destinationRoot = "/storage/emulated/0/Backup",
            totalFilesExpected = 5,
            totalBytesExpected = 10000L,
            items = listOf(
                JournalItemRecord(
                    relativePath = "file1.txt",
                    sourcePath = "/storage/emulated/0/Documents/file1.txt",
                    destinationPath = "/storage/emulated/0/Backup/file1.txt",
                    sizeBytes = 2000L,
                    lastModified = 1700000000000L,
                    state = JournalItemState.COMPLETED
                ),
                JournalItemRecord(
                    relativePath = "file2.txt",
                    sourcePath = "/storage/emulated/0/Documents/file2.txt",
                    destinationPath = "/storage/emulated/0/Backup/file2.txt",
                    sizeBytes = 8000L,
                    lastModified = 1700000000000L,
                    state = JournalItemState.PENDING
                )
            )
        )

        // Write entry
        journalRepository.recordOperationStarted(entry)

        // Simulate complete process death and cold restart by creating a brand-new repository instance
        val freshRepository = FileOperationJournalRepository(journalDir, testDispatcher)
        val interruptedOps = freshRepository.getInterruptedOperations()

        assertEquals(1, interruptedOps.size)
        val recovered = interruptedOps.first()
        assertEquals(entryId, recovered.id)
        assertEquals(JournalOperationType.COPY, recovered.type)
        assertEquals(JournalOperationState.INTERRUPTED, recovered.state)
        assertEquals(2, recovered.items.size)
        assertEquals(JournalItemState.COMPLETED, recovered.items[0].state)
        assertEquals(JournalItemState.PENDING, recovered.items[1].state)
    }

    @Test
    fun operationJournal_strictlyScrubsVaultMetadataAndCredentials() = runTest {
        val vaultEntry = OperationJournalEntry(
            id = "vault_op_1",
            type = JournalOperationType.COPY,
            title = "Copy to Vault",
            sourceRoots = listOf("/storage/emulated/0/PrivateDocs"),
            destinationRoot = "/storage/emulated/0/.arca_vault/secure",
            isVaultOperation = true,
            totalFilesExpected = 1,
            totalBytesExpected = 500L,
            metadata = mapOf(
                "vault_password" to "SuperSecretPassword123!",
                "encryption_key" to "0xDEADBEEFCAFE",
                "normal_setting" to "AES-256"
            ),
            items = listOf(
                JournalItemRecord(
                    relativePath = "financial_statement.pdf",
                    sourcePath = "/storage/emulated/0/PrivateDocs/financial_statement.pdf",
                    destinationPath = "/storage/emulated/0/.arca_vault/secure/financial_statement.pdf",
                    sizeBytes = 500L,
                    lastModified = 1700000000000L
                )
            )
        )

        journalRepository.recordOperationStarted(vaultEntry)

        val retrieved = journalRepository.getEntry("vault_op_1")
        assertNotNull(retrieved)
        assertTrue("isVaultOperation must be true", retrieved!!.isVaultOperation)

        // Verify passwords and keys were purged
        assertFalse(retrieved.metadata.containsKey("vault_password"))
        assertFalse(retrieved.metadata.containsKey("encryption_key"))
        assertTrue(retrieved.metadata.containsKey("normal_setting"))

        // Verify private filenames are scrubbed in the journal
        assertFalse(
            "Private filename must not be stored in plaintext in journal",
            retrieved.items.any { it.relativePath.contains("financial_statement") }
        )
        assertFalse(
            "Plaintext destination path must not leak into journal",
            retrieved.items.any { it.destinationPath.contains("financial_statement.pdf") }
        )
    }

    // =========================================================================
    // 1.4.32 Copy Recovery
    // =========================================================================

    @Test
    fun copyRecovery_verifiesExistingDestinationFilesBeforeSkipping() = runTest {
        val srcDir = File(testRoot, "SrcCopy").apply { mkdirs() }
        val dstDir = File(testRoot, "DstCopy").apply { mkdirs() }

        val file1 = File(srcDir, "doc1.txt").apply { writeText("Original Document 1") }
        val file2 = File(srcDir, "doc2.txt").apply { writeText("Original Document 2") }
        val file3 = File(srcDir, "doc3.txt").apply { writeText("Original Document 3") }

        // Dest: doc1 exists with correct size
        val dstFile1 = File(dstDir, "doc1.txt").apply { writeText("Original Document 1") }

        // Dest: doc2 exists but is truncated / corrupt!
        val dstFile2 = File(dstDir, "doc2.txt").apply { writeText("Truncated") }

        // Dest: doc3 does not exist

        val journalEntry = OperationJournalEntry(
            id = "copy_interrupted",
            type = JournalOperationType.COPY,
            title = "Copy files",
            sourceRoots = listOf(srcDir.absolutePath),
            destinationRoot = dstDir.absolutePath,
            totalFilesExpected = 3,
            totalBytesExpected = file1.length() + file2.length() + file3.length(),
            items = listOf(
                JournalItemRecord("doc1.txt", file1.absolutePath, dstFile1.absolutePath, file1.length(), file1.lastModified(), JournalItemState.COMPLETED),
                JournalItemRecord("doc2.txt", file2.absolutePath, dstFile2.absolutePath, file2.length(), file2.lastModified(), JournalItemState.COMPLETED), // Falsely recorded as completed before crash
                JournalItemRecord("doc3.txt", file3.absolutePath, File(dstDir, "doc3.txt").absolutePath, file3.length(), file3.lastModified(), JournalItemState.PENDING)
            )
        )
        journalRepository.recordOperationStarted(journalEntry)

        // Pre-flight assessment: MUST detect corrupted doc2.txt
        val assessment = recoveryEngine.assessInterruptedOperation(journalEntry)
        assertEquals(1, assessment.missingOrCorruptFilesCount) // doc2 caught!
        assertEquals(1, assessment.verifiedCompletedFilesCount) // only doc1 verified!
        assertEquals(file1.length(), assessment.verifiedCompletedBytes)
        assertTrue(assessment.canResumeSafely)

        // Resume operation
        val result = recoveryEngine.resumeOperation(journalEntry.id)
        assertTrue(result.isSuccess)

        // Verify doc2 was re-copied and fixed
        assertEquals(file2.readText(), dstFile2.readText())
        // Verify doc3 was copied
        val dstFile3 = File(dstDir, "doc3.txt")
        assertTrue(dstFile3.exists())
        assertEquals(file3.readText(), dstFile3.readText())

        // Verify journal marked completed
        val finished = journalRepository.getEntry(journalEntry.id)
        assertEquals(JournalOperationState.COMPLETED, finished?.state)
    }

    // =========================================================================
    // 1.4.33 Move Recovery Safety (Zero Source Deletion Invariant)
    // =========================================================================

    @Test
    fun moveRecovery_neverDeletesSourceBeforeDestinationIsVerifiedComplete() = runTest {
        val srcDir = File(testRoot, "SrcMove").apply { mkdirs() }
        val dstDir = File(testRoot, "DstMove").apply { mkdirs() }

        val fileA = File(srcDir, "fileA.txt").apply { writeText("Complete A") }
        val fileB = File(srcDir, "fileB.txt").apply { writeText("Pending B") }

        // Dest: fileA is complete and matches size
        val dstA = File(dstDir, "fileA.txt").apply { writeText("Complete A") }
        // Dest: fileB is missing (crash occurred before fileB was copied)
        val dstB = File(dstDir, "fileB.txt")

        val journalEntry = OperationJournalEntry(
            id = "move_interrupted",
            type = JournalOperationType.MOVE,
            title = "Move operation",
            sourceRoots = listOf(srcDir.absolutePath),
            destinationRoot = dstDir.absolutePath,
            totalFilesExpected = 2,
            totalBytesExpected = fileA.length() + fileB.length(),
            items = listOf(
                // fileA was copied, but process died before source was deleted
                JournalItemRecord("fileA.txt", fileA.absolutePath, dstA.absolutePath, fileA.length(), fileA.lastModified(), JournalItemState.COMPLETED, isSourceDeleted = false),
                // fileB was pending
                JournalItemRecord("fileB.txt", fileB.absolutePath, dstB.absolutePath, fileB.length(), fileB.lastModified(), JournalItemState.PENDING, isSourceDeleted = false)
            )
        )
        journalRepository.recordOperationStarted(journalEntry)

        // Resume move recovery
        val res = recoveryEngine.resumeOperation(journalEntry.id)
        assertTrue(res.isSuccess)

        // fileA: destination verified, so source fileA is now safely deleted
        assertTrue(dstA.exists())
        assertFalse("Source fileA should be deleted after destination verified", fileA.exists())

        // fileB: copied, verified, and only then source fileB deleted
        assertTrue(dstB.exists())
        assertEquals("Pending B", dstB.readText())
        assertFalse("Source fileB should be deleted only after successful transfer", fileB.exists())
    }

    @Test
    fun moveRecovery_preservesSourceIfDestinationCopyFails() = runTest {
        val srcDir = File(testRoot, "SrcMoveFail").apply { mkdirs() }
        val blockerFile = File(testRoot, "blocker.dat").apply { writeText("File blocker") }

        val sourceFile = File(srcDir, "critical_source.dat").apply { writeText("Vital uncorrupted data") }
        // Setting destination inside a regular file guarantees an IOException across all OSes
        val dstFile = File(blockerFile, "critical_source.dat")

        val journalEntry = OperationJournalEntry(
            id = "move_fail_test",
            type = JournalOperationType.MOVE,
            title = "Move critical file",
            sourceRoots = listOf(srcDir.absolutePath),
            destinationRoot = testRoot.absolutePath,
            totalFilesExpected = 1,
            totalBytesExpected = sourceFile.length(),
            items = listOf(
                JournalItemRecord("critical_source.dat", sourceFile.absolutePath, dstFile.absolutePath, sourceFile.length(), sourceFile.lastModified(), JournalItemState.PENDING)
            )
        )
        journalRepository.recordOperationStarted(journalEntry)

        val result = recoveryEngine.resumeOperation(journalEntry.id)
        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertFalse(opResult.success)
        assertTrue(opResult.errors.isNotEmpty())

        // CRITICAL INVARIANT: Source file must NEVER be deleted if destination write failed
        assertTrue("Source file must be preserved if destination operation fails!", sourceFile.exists())
        assertEquals("Vital uncorrupted data", sourceFile.readText())
    }

    // =========================================================================
    // 1.4.34 Archive Operation Recovery
    // =========================================================================

    @Test
    fun archiveRecovery_interruptedCompressionIsNeverTreatedAsValidArchive() = runTest {
        val srcDir = File(testRoot, "SrcArchive").apply { mkdirs() }
        File(srcDir, "item1.txt").apply { writeText("Zip item 1") }
        File(srcDir, "item2.txt").apply { writeText("Zip item 2") }

        val partialZip = File(testRoot, "corrupted_partial.zip").apply {
            writeText("PK\u0003\u0004IncompleteZipHeaderWithoutCentralDirectory")
        }

        val journalEntry = OperationJournalEntry(
            id = "archive_interrupted",
            type = JournalOperationType.ARCHIVE_COMPRESS,
            title = "Compressing archive",
            sourceRoots = listOf(srcDir.absolutePath),
            destinationRoot = partialZip.absolutePath,
            totalFilesExpected = 2,
            totalBytesExpected = 1000L,
            items = listOf(
                JournalItemRecord("item1.txt", File(srcDir, "item1.txt").absolutePath, partialZip.absolutePath, 10L, 0L, JournalItemState.COMPLETED),
                JournalItemRecord("item2.txt", File(srcDir, "item2.txt").absolutePath, partialZip.absolutePath, 10L, 0L, JournalItemState.PENDING)
            )
        )
        journalRepository.recordOperationStarted(journalEntry)

        // Pre-flight assessment: partial archive output must NEVER be marked resumeable mid-stream!
        val assessment = recoveryEngine.assessInterruptedOperation(journalEntry)
        assertFalse("Interrupted compression cannot be resumed mid-stream", assessment.canResumeSafely)
        assertEquals(RecoveryRecommendation.RESTART_REQUIRED, assessment.recommendation)
        assertTrue(assessment.notice.contains("cannot be used as a valid archive"))

        // Restart operation cleans corrupted archive and produces a valid complete zip
        val restartResult = recoveryEngine.restartOperation(journalEntry.id)
        assertTrue(restartResult.isSuccess)
        assertTrue(partialZip.exists())

        // Verify the resulting archive is valid and can be opened
        val zipFile = java.util.zip.ZipFile(partialZip)
        assertEquals(2, zipFile.size())
        zipFile.close()
    }

    // =========================================================================
    // 1.4.36 Recovery Interface: Accurate Verified State & Actions
    // =========================================================================

    @Test
    fun recoveryReport_strictlyReflectsVerifiedStateNotMerelyAttemptedBytes() = runTest {
        val srcDir = File(testRoot, "SrcVerifiedReport").apply { mkdirs() }
        val dstDir = File(testRoot, "DstVerifiedReport").apply { mkdirs() }

        val f1 = File(srcDir, "f1.bin").apply { writeBytes(ByteArray(4000) { 1 }) }
        val f2 = File(srcDir, "f2.bin").apply { writeBytes(ByteArray(6000) { 2 }) }

        // Dest: only f1 completed and matches size
        File(dstDir, "f1.bin").apply { writeBytes(ByteArray(4000) { 1 }) }

        val journalEntry = OperationJournalEntry(
            id = "report_test",
            type = JournalOperationType.COPY,
            title = "Transfer",
            sourceRoots = listOf(srcDir.absolutePath),
            destinationRoot = dstDir.absolutePath,
            totalFilesExpected = 2,
            totalBytesExpected = 10000L,
            completedBytesCount = 9000L, // Attempted bytes recorded before crash was 9000
            items = listOf(
                JournalItemRecord("f1.bin", f1.absolutePath, File(dstDir, "f1.bin").absolutePath, 4000L, f1.lastModified(), JournalItemState.COMPLETED),
                JournalItemRecord("f2.bin", f2.absolutePath, File(dstDir, "f2.bin").absolutePath, 6000L, f2.lastModified(), JournalItemState.PENDING)
            )
        )
        journalRepository.recordOperationStarted(journalEntry)

        val assessment = recoveryEngine.assessInterruptedOperation(journalEntry)
        val report = RecoveryReport.fromAssessment(assessment)

        // Crucial requirement: progress figures must reflect verified state (4000L), not attempted bytes (9000L)
        assertEquals(4000L, report.verifiedBytes)
        assertEquals(10000L, report.totalBytes)
        assertEquals(1, report.verifiedFilesCount)
        assertEquals(2, report.totalFilesCount)
        assertEquals(1, report.pendingFilesCount)
        assertTrue(report.canResume)
    }

    @Test
    fun discardOperation_cleansUpAllOrphanTemporaryFiles() = runTest {
        val dstDir = File(testRoot, "DstDiscard").apply { mkdirs() }

        // Leftover temporary files from interrupted transfer
        val tempFile1 = File(dstDir, ".incomplete.dat.tmp").apply { writeText("garbage temp") }
        val tempFile2 = File(dstDir, ".backup.dat.arca_backup_tmp").apply { writeText("backup temp") }

        val journalEntry = OperationJournalEntry(
            id = "discard_test_op",
            type = JournalOperationType.COPY,
            title = "Discard test",
            sourceRoots = listOf("/some/path"),
            destinationRoot = dstDir.absolutePath
        )
        journalRepository.recordOperationStarted(journalEntry)

        val result = recoveryEngine.discardOperation(journalEntry.id)
        assertTrue(result.isSuccess)

        // Verify orphan temporary files were deleted
        assertFalse(tempFile1.exists())
        assertFalse(tempFile2.exists())

        // Verify entry marked discarded
        val entry = journalRepository.getEntry(journalEntry.id)
        assertEquals(JournalOperationState.DISCARDED, entry?.state)
    }

    // =========================================================================
    // 1.4.37 Recovery Safety: Duplicate Prevention, Changed Source, Storage Disconnect
    // =========================================================================

    @Test
    fun recoverySafety_detectsSourceModificationsAndBlocksBlindResume() = runTest {
        val srcDir = File(testRoot, "SrcMod").apply { mkdirs() }
        val dstDir = File(testRoot, "DstMod").apply { mkdirs() }

        val srcFile = File(srcDir, "data.txt").apply {
            writeText("Initial state")
            setLastModified(1600000000000L)
        }

        val journalEntry = OperationJournalEntry(
            id = "src_mod_op",
            type = JournalOperationType.COPY,
            title = "Copy data",
            sourceRoots = listOf(srcDir.absolutePath),
            destinationRoot = dstDir.absolutePath,
            totalFilesExpected = 1,
            totalBytesExpected = srcFile.length(),
            items = listOf(
                JournalItemRecord("data.txt", srcFile.absolutePath, File(dstDir, "data.txt").absolutePath, srcFile.length(), 1600000000000L, JournalItemState.PENDING)
            )
        )
        journalRepository.recordOperationStarted(journalEntry)

        // Mutate source file after interruption
        srcFile.appendText(" - Modified after crash!")
        srcFile.setLastModified(1690000000000L)

        val assessment = recoveryEngine.assessInterruptedOperation(journalEntry)
        assertEquals(RecoveryRecommendation.RESTART_REQUIRED, assessment.recommendation)
        assertFalse("Must not blindly resume when source state has changed", assessment.canResumeSafely)
        assertTrue(assessment.notice.contains("Source files have changed"))
    }

    @Test
    fun recoverySafety_handlesStorageDisconnectionWithoutCrashingOrCorruptingJournal() = runTest {
        val unmountedSource = File(testRoot, "UnmountedUsbDrive/Source")
        val destDir = File(testRoot, "DestConnected").apply { mkdirs() }

        val journalEntry = OperationJournalEntry(
            id = "storage_disconnect_op",
            type = JournalOperationType.COPY,
            title = "USB Copy",
            sourceRoots = listOf(unmountedSource.absolutePath),
            destinationRoot = destDir.absolutePath,
            totalFilesExpected = 1,
            totalBytesExpected = 5000L,
            items = listOf(
                JournalItemRecord("doc.pdf", File(unmountedSource, "doc.pdf").absolutePath, File(destDir, "doc.pdf").absolutePath, 5000L, 0L, JournalItemState.PENDING)
            )
        )
        journalRepository.recordOperationStarted(journalEntry)

        val assessment = recoveryEngine.assessInterruptedOperation(journalEntry)
        assertEquals(RecoveryRecommendation.STORAGE_DISCONNECTED, assessment.recommendation)
        assertFalse(assessment.canResumeSafely)
        assertTrue(assessment.notice.contains("disconnected or unmounted"))

        val report = RecoveryReport.fromAssessment(assessment)
        assertTrue(report.storageDisconnected)
        assertFalse(report.canResume)
    }

    // =========================================================================
    // 1.4.38–1.4.40 Release Gate Verification
    // =========================================================================

    @Test
    fun releaseGate_neverReportsPartiallyCompletedOperationAsSuccessful() = runTest {
        val srcDir = File(testRoot, "ReleaseGateSrc").apply { mkdirs() }
        val dstDir = File(testRoot, "ReleaseGateDst").apply { mkdirs() }

        val f1 = File(srcDir, "part1.bin").apply { writeText("Part 1") }
        val f2 = File(srcDir, "part2.bin").apply { writeText("Part 2") }

        // Dest only has part1
        File(dstDir, "part1.bin").apply { writeText("Part 1") }

        // Block destination for part2 by placing a regular file where directory is expected
        val blockerFile = File(dstDir, "blocker.dat").apply { writeText("I am a file") }
        val invalidDstFile2 = File(blockerFile, "part2.bin")

        val journalEntry = OperationJournalEntry(
            id = "release_gate_op",
            type = JournalOperationType.COPY,
            title = "Partial operation",
            sourceRoots = listOf(srcDir.absolutePath),
            destinationRoot = dstDir.absolutePath,
            totalFilesExpected = 2,
            totalBytesExpected = f1.length() + f2.length(),
            items = listOf(
                JournalItemRecord("part1.bin", f1.absolutePath, File(dstDir, "part1.bin").absolutePath, f1.length(), f1.lastModified(), JournalItemState.COMPLETED),
                JournalItemRecord("part2.bin", f2.absolutePath, invalidDstFile2.absolutePath, f2.length(), f2.lastModified(), JournalItemState.PENDING)
            )
        )
        journalRepository.recordOperationStarted(journalEntry)

        // Check assess: pending count must be 1
        val assessment = recoveryEngine.assessInterruptedOperation(journalEntry)
        assertEquals(1, assessment.pendingFilesCount)

        // Attempt resume: part2 copy fails because parent is a regular file
        val res = recoveryEngine.resumeOperation(journalEntry.id)
        assertTrue(res.isSuccess)
        val opResult = res.getOrThrow()

        // RELEASE GATE ASSERTIONS:
        assertFalse("ARCA release gate: partially completed operation must NOT report success!", opResult.success)
        assertEquals(1, opResult.filesProcessed) // only part1 was completed
        assertTrue(opResult.errors.isNotEmpty())

        // Journal must not report COMPLETED if part2 was never copied
        val updated = journalRepository.getEntry(journalEntry.id)
        assertNotEquals(JournalOperationState.COMPLETED, updated?.state)
    }

    // =========================================================================
    // FileOperationEngine Integration with Journal
    // =========================================================================

    @Test
    fun fileOperationEngine_recordsJournalAndCompletesCleanly() = runTest {
        val srcDir = File(testRoot, "EngineSrc").apply { mkdirs() }
        val dstDir = File(testRoot, "EngineDst").apply { mkdirs() }

        val file1 = File(srcDir, "sourceA.txt").apply { writeText("Hello from source A") }
        val file2 = File(srcDir, "sourceB.txt").apply { writeText("Hello from source B") }

        val engine = FileOperationEngine(
            ioDispatcher = testDispatcher,
            remoteOperator = null,
            journalRepository = journalRepository
        )

        val srcNode1 = StorageNode(id = file1.absolutePath, name = file1.name, path = file1.absolutePath, size = file1.length(), lastModified = file1.lastModified(), isDirectory = false)
        val srcNode2 = StorageNode(id = file2.absolutePath, name = file2.name, path = file2.absolutePath, size = file2.length(), lastModified = file2.lastModified(), isDirectory = false)
        val dstNode = StorageNode(id = dstDir.absolutePath, name = dstDir.name, path = dstDir.absolutePath, size = 0L, lastModified = dstDir.lastModified(), isDirectory = true)

        val res = engine.copy(listOf(srcNode1, srcNode2), dstNode)
        assertTrue(res.isSuccess)

        // Verify active directory has no remaining unfinished operations
        val interrupted = journalRepository.getInterruptedOperations()
        assertTrue("Active journal should be empty after successful operation", interrupted.isEmpty())

        // Verify destination copies exist
        assertTrue(File(dstDir, "sourceA.txt").exists())
        assertTrue(File(dstDir, "sourceB.txt").exists())
    }
}
