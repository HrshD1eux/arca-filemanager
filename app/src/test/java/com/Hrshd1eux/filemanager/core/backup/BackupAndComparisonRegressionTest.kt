package com.Hrshd1eux.filemanager.core.backup

import com.Hrshd1eux.filemanager.core.backup.engine.BackupEngine
import com.Hrshd1eux.filemanager.core.backup.engine.BackupProgress
import com.Hrshd1eux.filemanager.core.backup.model.BackupMode
import com.Hrshd1eux.filemanager.core.backup.model.BackupPreviewAction
import com.Hrshd1eux.filemanager.core.backup.model.BackupProfile
import com.Hrshd1eux.filemanager.core.backup.model.BackupStatus
import com.Hrshd1eux.filemanager.core.backup.model.MirrorDeletionNotConfirmedException
import com.Hrshd1eux.filemanager.core.checksum.ChecksumAlgorithm
import com.Hrshd1eux.filemanager.core.checksum.ChecksumEngine
import com.Hrshd1eux.filemanager.core.checksum.FileChangedDuringVerificationException
import com.Hrshd1eux.filemanager.core.comparison.DetailedFileComparison
import com.Hrshd1eux.filemanager.core.comparison.FileComparisonEngine
import com.Hrshd1eux.filemanager.core.storage.util.ComparisonStatus
import com.Hrshd1eux.filemanager.core.storage.util.FolderComparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
 * Comprehensive Test Suite for Phase C — File Comparison and Backup Foundations (Milestones 1.4.21 – 1.4.30).
 *
 * Covers:
 * - 1.4.21 File Comparison (metadata comparison, truthfulness disclaimer, content checksum).
 * - 1.4.22 File Content Verification (streaming hashing, change detection during verification, algorithms).
 * - 1.4.23 Folder Comparison (structured 4-column comparison, subfolder navigation, status filtering).
 * - 1.4.24 Comparison Safety (strictly read-only, distinction of inaccessible vs missing items).
 * - 1.4.25 Backup Profiles (source, destination, copy vs mirror modes, integrity verification, exclusions).
 * - 1.4.26 Backup Preview (pre-execution report of copies, updates, skips, mirror deletions, inaccessible items).
 * - 1.4.27 Backup Engine: Copy Mode (reliable copy, timestamp preservation, destination safety, progress).
 * - 1.4.28 Backup Engine: Mirror Mode (explicit confirmation requirement, destination pruning, zero source deletion).
 * - 1.4.29 Backup Verification (SHA-256 verification of transferred files, failure reporting, temp cleanup).
 * - 1.4.30 Backup Regression (interruption recovery, cancellation cleanup, nonexistent source resilience).
 */
class BackupAndComparisonRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = Dispatchers.Unconfined
    private lateinit var checksumEngine: ChecksumEngine
    private lateinit var fileComparisonEngine: FileComparisonEngine
    private lateinit var backupEngine: BackupEngine
    private lateinit var testRoot: File

    @Before
    fun setUp() {
        checksumEngine = ChecksumEngine(testDispatcher)
        fileComparisonEngine = FileComparisonEngine(testDispatcher, checksumEngine)
        backupEngine = BackupEngine(testDispatcher, null, checksumEngine)
        testRoot = tempFolder.newFolder("backup_comparison_test_root")
    }

    // =========================================================================
    // 1.4.21 File Comparison & Metadata Truthfulness
    // =========================================================================

    @Test
    fun fileComparison_includesProminentMetadataDisclaimer() = runTest {
        val file1 = File(testRoot, "docA.txt").apply { writeText("Hello World") }
        val file2 = File(testRoot, "docB.txt").apply { writeText("Hello World") }

        val result = fileComparisonEngine.compareFiles(file1, file2, calculateHash = false)
        assertTrue(result.isSuccess)

        val comparison = result.getOrThrow()
        assertNotNull(comparison.metadataMatchDisclaimer)
        assertTrue(comparison.metadataMatchDisclaimer.contains("does NOT establish identical contents"))
        assertEquals(DetailedFileComparison.METADATA_MATCH_DISCLAIMER, comparison.metadataMatchDisclaimer)
        assertFalse("Without cryptographic hashing, isContentVerified must be false", comparison.isContentVerified)
    }

    @Test
    fun fileComparison_matchingMetadataDoesNotEstablishContentMatch() = runTest {
        // Two files with identical length and timestamp, but different content
        val file1 = File(testRoot, "file1.dat").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val file2 = File(testRoot, "file2.dat").apply { writeBytes(byteArrayOf(9, 8, 7, 6, 5)) }
        val fixedTimestamp = 1700000000000L
        file1.setLastModified(fixedTimestamp)
        file2.setLastModified(fixedTimestamp)

        // Comparison without hash calculation: metadata matches
        val metaOnly = fileComparisonEngine.compareFiles(file1, file2, calculateHash = false).getOrThrow()
        assertTrue(metaOnly.isSizeMatch)
        assertTrue(metaOnly.isTimestampMatch)
        assertFalse(metaOnly.isContentVerified)
        assertNull(metaOnly.isHashMatch)

        // Comparison with cryptographic hash calculation: reveals difference
        val withHash = fileComparisonEngine.compareFiles(file1, file2, calculateHash = true, algorithm = ChecksumAlgorithm.SHA_256).getOrThrow()
        assertTrue(withHash.isSizeMatch)
        assertTrue(withHash.isTimestampMatch)
        assertTrue(withHash.isContentVerified)
        assertEquals(false, withHash.isHashMatch)
        assertFalse("Files with differing bytes must not be marked identical", withHash.isIdentical)
        assertNotEquals(withHash.hash1, withHash.hash2)
    }

    @Test
    fun fileComparison_identicalFilesWithCryptographicHashMatch() = runTest {
        val content = "Identical cryptographic content payload for integrity testing."
        val file1 = File(testRoot, "alpha.txt").apply { writeText(content) }
        val file2 = File(testRoot, "alpha_copy.txt").apply { writeText(content) }

        val comparison = fileComparisonEngine.compareFiles(file1, file2, calculateHash = true).getOrThrow()
        assertTrue(comparison.isSizeMatch)
        assertTrue(comparison.isContentVerified)
        assertEquals(true, comparison.isHashMatch)
        assertTrue(comparison.isIdentical)
        assertEquals(comparison.hash1, comparison.hash2)
    }

    // =========================================================================
    // 1.4.22 File Content Verification & Streaming Safety
    // =========================================================================

    @Test
    fun checksumEngine_supportsJustifiedAlgorithms() = runTest {
        val file = File(testRoot, "vector.txt").apply {
            writeText("The quick brown fox jumps over the lazy dog")
        }

        val sha256 = checksumEngine.calculateAlgorithm(file, ChecksumAlgorithm.SHA_256).getOrThrow()
        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", sha256.hashHex)

        val sha1 = checksumEngine.calculateAlgorithm(file, ChecksumAlgorithm.SHA_1).getOrThrow()
        assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12", sha1.hashHex)

        val md5 = checksumEngine.calculateAlgorithm(file, ChecksumAlgorithm.MD5).getOrThrow()
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", md5.hashHex)

        val crc32 = checksumEngine.calculateAlgorithm(file, ChecksumAlgorithm.CRC32).getOrThrow()
        assertEquals("414fa339", crc32.hashHex)
    }

    @Test
    fun checksumEngine_detectsFileMutationDuringVerification() = runTest {
        // Create a large enough file to receive progress updates
        val file = File(testRoot, "mutating_file.dat").apply {
            writeBytes(ByteArray(64 * 1024) { 0x55.toByte() })
        }

        var mutated = false
        val result = checksumEngine.calculateAlgorithm(file, ChecksumAlgorithm.SHA_256) { bytesRead, _ ->
            if (!mutated && bytesRead > 0) {
                // Mutate the file length and timestamp while stream is in progress
                file.appendText("Appended mutation bytes!")
                file.setLastModified(System.currentTimeMillis() + 5000L)
                mutated = true
            }
        }

        assertTrue("Verification must fail when file changes during hashing", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(
            "Expected FileChangedDuringVerificationException, got: $ex",
            ex is FileChangedDuringVerificationException
        )
    }

    // =========================================================================
    // 1.4.23 Folder Comparison (Structured 4-Column Table)
    // =========================================================================

    @Test
    fun folderComparison_structuredFourColumnPresenceAndStatus() {
        val dirA = File(testRoot, "FolderA").apply { mkdirs() }
        val dirB = File(testRoot, "FolderB").apply { mkdirs() }

        // Item 1: In both, identical size & time
        val fileSameA = File(dirA, "report.pdf").apply { writeText("Report 2026") }
        val fileSameB = File(dirB, "report.pdf").apply { writeText("Report 2026") }
        fileSameA.setLastModified(1700000000000L)
        fileSameB.setLastModified(1700000000000L)

        // Item 2: In both, modified
        File(dirA, "data.csv").apply { writeText("id,name\n1,alpha") }
        File(dirB, "data.csv").apply { writeText("id,name,value\n1,alpha,100\n2,beta,200") }

        // Item 3: Only in A
        File(dirA, "notes.txt").apply { writeText("Meeting notes") }

        // Item 4: Only in B
        File(dirB, "archive.zip").apply { writeText("ZIP") }

        val report = FolderComparator.compareDirectoriesStructured(
            dirA = dirA,
            dirB = dirB,
            verifyHashes = false,
            recursive = false
        )

        assertEquals(4, report.totalCount)
        val itemsByName = report.items.associateBy { it.name }

        // 1. report.pdf: Present in A, Present in B -> REQUIRES_COMPARISON (pending hash)
        val reportItem = itemsByName["report.pdf"]
        assertNotNull(reportItem)
        assertEquals("Present", reportItem!!.presenceA)
        assertEquals("Present", reportItem.presenceB)
        assertEquals(ComparisonStatus.REQUIRES_COMPARISON, reportItem.status)
        assertEquals("Requires comparison", reportItem.resultDescription)

        // 2. data.csv: Present in A, Present in B -> MODIFIED
        val dataItem = itemsByName["data.csv"]
        assertNotNull(dataItem)
        assertEquals("Present", dataItem!!.presenceA)
        assertEquals("Present", dataItem.presenceB)
        assertEquals(ComparisonStatus.MODIFIED, dataItem.status)
        assertEquals("Different", dataItem.resultDescription)

        // 3. notes.txt: Present in A, Missing in B -> UNIQUE_LEFT
        val notesItem = itemsByName["notes.txt"]
        assertNotNull(notesItem)
        assertEquals("Present", notesItem!!.presenceA)
        assertEquals("Missing", notesItem.presenceB)
        assertEquals(ComparisonStatus.UNIQUE_LEFT, notesItem.status)
        assertEquals("Missing from B", notesItem.resultDescription)

        // 4. archive.zip: Missing in A, Present in B -> UNIQUE_RIGHT
        val archiveItem = itemsByName["archive.zip"]
        assertNotNull(archiveItem)
        assertEquals("Missing", archiveItem!!.presenceA)
        assertEquals("Present", archiveItem.presenceB)
        assertEquals(ComparisonStatus.UNIQUE_RIGHT, archiveItem.status)
        assertEquals("Missing from A", archiveItem.resultDescription)
    }

    @Test
    fun folderComparison_recursiveSubfolderNavigation() {
        val dirA = File(testRoot, "TreeA").apply { mkdirs() }
        val dirB = File(testRoot, "TreeB").apply { mkdirs() }

        val subA = File(dirA, "documents/work").apply { mkdirs() }
        val subB = File(dirB, "documents/work").apply { mkdirs() }

        File(subA, "contract.pdf").apply { writeText("Agreement A") }
        File(subB, "contract.pdf").apply { writeText("Agreement A") }

        val report = FolderComparator.compareDirectoriesStructured(
            dirA = dirA,
            dirB = dirB,
            verifyHashes = true,
            recursive = true
        )

        val contractItem = report.items.find { it.relativePath == "documents/work/contract.pdf" }
        assertNotNull("Subfolder item must be discovered with relative path", contractItem)
        assertEquals(ComparisonStatus.IDENTICAL, contractItem!!.status)
        assertEquals("Same", contractItem.resultDescription)
    }

    // =========================================================================
    // 1.4.24 Comparison Safety (Read-Only & Inaccessible Distinction)
    // =========================================================================

    @Test
    fun comparisonSafety_neverModifiesFilesDuringComparison() {
        val dirA = File(testRoot, "SafeA").apply { mkdirs() }
        val dirB = File(testRoot, "SafeB").apply { mkdirs() }

        val fileA = File(dirA, "important.doc").apply { writeText("Confidential source") }
        val fileB = File(dirB, "important.doc").apply { writeText("Confidential destination") }

        val timeA = 1600000000000L
        val timeB = 1600000050000L
        fileA.setLastModified(timeA)
        fileB.setLastModified(timeB)

        val lenA = fileA.length()
        val lenB = fileB.length()

        // Run structured comparison with hash verification
        FolderComparator.compareDirectoriesStructured(dirA, dirB, verifyHashes = true, recursive = true)

        // Verify strictly read-only guarantee: neither file was touched or modified
        assertTrue(fileA.exists())
        assertTrue(fileB.exists())
        assertEquals(lenA, fileA.length())
        assertEquals(lenB, fileB.length())
        assertEquals(timeA, fileA.lastModified())
        assertEquals(timeB, fileB.lastModified())
        assertEquals("Confidential source", fileA.readText())
        assertEquals("Confidential destination", fileB.readText())
    }

    // =========================================================================
    // 1.4.25 Backup Profiles & 1.4.26 Backup Preview
    // =========================================================================

    @Test
    fun backupPreview_generatesCategorizedReportBeforeExecution() = runTest {
        val sourceDir = File(testRoot, "SourcePhotos").apply { mkdirs() }
        val destDir = File(testRoot, "DestBackup").apply { mkdirs() }

        // 1. File to copy (new in source)
        val newFile = File(sourceDir, "img_new.jpg").apply { writeBytes(ByteArray(1024) { 1 }) }

        // 2. File to update (modified: differing size and time)
        val modSource = File(sourceDir, "img_modified.jpg").apply { writeBytes(ByteArray(2048) { 2 }) }
        val modDest = File(destDir, "img_modified.jpg").apply { writeBytes(ByteArray(512) { 3 }) }

        // 3. File already matching (same size and timestamp)
        val matchSource = File(sourceDir, "img_match.jpg").apply { writeBytes(ByteArray(100) { 4 }) }
        val matchDest = File(destDir, "img_match.jpg").apply { writeBytes(ByteArray(100) { 4 }) }
        val matchTime = 1710000000000L
        matchSource.setLastModified(matchTime)
        matchDest.setLastModified(matchTime)

        // 4. File in dest only (for mirror deletion)
        val destOnly = File(destDir, "obsolete_dest.jpg").apply { writeBytes(ByteArray(4096) { 5 }) }

        // Test Preview in MIRROR mode
        val mirrorProfile = BackupProfile(
            id = "profile_mirror_1",
            name = "Photos Mirror Backup",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.MIRROR,
            verifyIntegrity = true
        )

        val preview = backupEngine.previewBackup(mirrorProfile)

        assertEquals("profile_mirror_1", preview.profileId)
        assertEquals(BackupMode.MIRROR, preview.mode)

        // Verify categorized actions
        assertEquals(1, preview.filesToCopy.size)
        assertEquals("img_new.jpg", preview.filesToCopy[0].relativePath)
        assertEquals(BackupPreviewAction.COPY_NEW, preview.filesToCopy[0].action)

        assertEquals(1, preview.filesToUpdate.size)
        assertEquals("img_modified.jpg", preview.filesToUpdate[0].relativePath)
        assertEquals(BackupPreviewAction.UPDATE_MODIFIED, preview.filesToUpdate[0].action)

        assertEquals(1, preview.filesAlreadyMatching.size)
        assertEquals("img_match.jpg", preview.filesAlreadyMatching[0].relativePath)
        assertEquals(BackupPreviewAction.ALREADY_MATCHES, preview.filesAlreadyMatching[0].action)

        assertEquals(1, preview.filesToDeleteInMirror.size)
        assertEquals("obsolete_dest.jpg", preview.filesToDeleteInMirror[0].relativePath)
        assertEquals(BackupPreviewAction.DELETE_IN_MIRROR, preview.filesToDeleteInMirror[0].action)
        assertEquals(4096L, preview.totalBytesToDelete)

        assertTrue(preview.requiresMirrorConfirmation)
        assertEquals(3072L, preview.totalBytesToCopy) // 1024 (new) + 2048 (update)

        // Test Preview in COPY mode
        val copyProfile = mirrorProfile.copy(mode = BackupMode.COPY)
        val copyPreview = backupEngine.previewBackup(copyProfile)
        assertEquals(0, copyPreview.filesToDeleteInMirror.size)
        assertFalse(copyPreview.requiresMirrorConfirmation)
    }

    // =========================================================================
    // 1.4.27 Backup Engine: Copy Mode
    // =========================================================================

    @Test
    fun backupEngine_copyMode_reliableExecutionAndAtomicSafety() = runTest {
        val sourceDir = File(testRoot, "CopySrc").apply { mkdirs() }
        val destDir = File(testRoot, "CopyDst").apply { mkdirs() }

        val file1 = File(sourceDir, "doc1.txt").apply {
            writeText("Content for document 1")
            setLastModified(1680000000000L)
        }
        val file2 = File(sourceDir, "folder/doc2.txt").apply {
            parentFile?.mkdirs()
            writeText("Content for subfolder document 2")
            setLastModified(1685000000000L)
        }

        // An existing file in destination that should be preserved in COPY mode
        val destExisting = File(destDir, "existing_extra.txt").apply {
            writeText("Keep this destination file safe")
        }

        val profile = BackupProfile(
            id = "copy_profile",
            name = "Copy Backup",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.COPY,
            verifyIntegrity = true
        )

        var lastProgress: BackupProgress? = null
        val report = backupEngine.executeBackup(profile) { progress ->
            lastProgress = progress
        }

        assertEquals(BackupStatus.SUCCESS, report.status)
        assertEquals(2, report.filesCopied)
        assertEquals(0, report.filesFailed)
        assertEquals(2, report.verifiedCount)
        assertEquals(0, report.deletedInMirrorCount)

        // Verify destination copies exist and have identical contents
        val destFile1 = File(destDir, "doc1.txt")
        val destFile2 = File(destDir, "folder/doc2.txt")
        assertTrue(destFile1.exists())
        assertTrue(destFile2.exists())
        assertEquals(file1.readText(), destFile1.readText())
        assertEquals(file2.readText(), destFile2.readText())

        // Verify timestamps preserved
        assertEquals(file1.lastModified(), destFile1.lastModified())
        assertEquals(file2.lastModified(), destFile2.lastModified())

        // Verify existing destination file was NOT touched
        assertTrue(destExisting.exists())
        assertEquals("Keep this destination file safe", destExisting.readText())

        // Verify source files were untouched
        assertTrue(file1.exists())
        assertTrue(file2.exists())

        // Verify no leftover .arca_backup_tmp artifacts
        val tmpFiles = destDir.walkTopDown().filter { it.name.endsWith(".arca_backup_tmp") }.toList()
        assertTrue("No temporary backup files should remain", tmpFiles.isEmpty())

        assertNotNull(lastProgress)
        assertEquals(2, lastProgress!!.filesProcessed)
    }

    // =========================================================================
    // 1.4.28 Backup Engine: Mirror Mode Safety Guard & Pruning
    // =========================================================================

    @Test
    fun backupEngine_mirrorMode_strictlyRequiresConfirmationBeforePruning() = runTest {
        val sourceDir = File(testRoot, "MirrorSrc").apply { mkdirs() }
        val destDir = File(testRoot, "MirrorDst").apply { mkdirs() }

        File(sourceDir, "keep_me.txt").apply { writeText("Keep") }
        val fileToDelete = File(destDir, "stale_file.txt").apply { writeText("Stale") }

        val profile = BackupProfile(
            id = "mirror_profile",
            name = "Mirror Backup",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.MIRROR,
            verifyIntegrity = false
        )

        // Attempt execution WITHOUT confirmedMirrorDeletion
        try {
            backupEngine.executeBackup(profile, confirmedMirrorDeletion = false)
            fail("Mirror mode execution must throw MirrorDeletionNotConfirmedException when unconfirmed")
        } catch (e: MirrorDeletionNotConfirmedException) {
            assertTrue(e.message?.contains("requires explicit confirmation") == true)
        }

        // Verify the destination file was NOT deleted
        assertTrue("Destination file must remain untouched when unconfirmed", fileToDelete.exists())
    }

    @Test
    fun backupEngine_mirrorMode_prunesDestinationWhenExplicitlyConfirmed() = runTest {
        val sourceDir = File(testRoot, "MirrorConfirmedSrc").apply { mkdirs() }
        val destDir = File(testRoot, "MirrorConfirmedDst").apply { mkdirs() }

        val srcFile = File(sourceDir, "active_file.txt").apply { writeText("Active data") }
        val staleFile = File(destDir, "obsolete_dest_file.txt").apply { writeText("Obsolete") }

        val profile = BackupProfile(
            id = "mirror_confirmed_profile",
            name = "Mirror Confirmed Backup",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.MIRROR,
            verifyIntegrity = true
        )

        // Execute WITH confirmedMirrorDeletion = true
        val report = backupEngine.executeBackup(profile, confirmedMirrorDeletion = true)

        assertEquals(BackupStatus.SUCCESS, report.status)
        assertEquals(1, report.filesCopied)
        assertEquals(1, report.deletedInMirrorCount)

        // Verify stale destination file was pruned
        assertFalse("Stale destination file must be deleted in mirror mode", staleFile.exists())

        // Verify active file copied
        val copiedFile = File(destDir, "active_file.txt")
        assertTrue(copiedFile.exists())
        assertEquals("Active data", copiedFile.readText())

        // Crucial invariant: source file was NEVER deleted
        assertTrue("Source files must NEVER be deleted in mirror sync", srcFile.exists())
    }

    @Test
    fun backupEngine_mirrorMode_abortsPruningWhenCopyFails() = runTest {
        val sourceDir = File(testRoot, "MirrorFailSrc").apply { mkdirs() }
        val destDir = File(testRoot, "MirrorFailDst").apply { mkdirs() }

        // Source file that cannot be read
        val unreadableSrc = File(sourceDir, "unreadable.txt").apply {
            writeText("Cannot read")
            setReadable(false)
        }
        val staleDest = File(destDir, "preserve_if_failed.txt").apply {
            writeText("Do not delete me if copy fails!")
        }

        val profile = BackupProfile(
            id = "mirror_fail_profile",
            name = "Mirror Fail Profile",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.MIRROR,
            verifyIntegrity = false
        )

        val report = backupEngine.executeBackup(profile, confirmedMirrorDeletion = true)

        if (unreadableSrc.canRead()) {
            // Windows OS might not support setReadable(false) fully; skip if OS ignores it
            return@runTest
        }

        // Mirror pruning must be aborted because copy failed
        assertEquals(BackupStatus.FAILED, report.status)
        assertTrue("Destination file must be preserved when copy fails", staleDest.exists())
        assertEquals(0, report.deletedInMirrorCount)
    }

    // =========================================================================
    // 1.4.29 Backup Verification (Integrity & Failed Verification Reporting)
    // =========================================================================

    @Test
    fun backupVerification_verifiesSha256MatchesSuccessfully() = runTest {
        val sourceDir = File(testRoot, "VerifySrc").apply { mkdirs() }
        val destDir = File(testRoot, "VerifyDst").apply { mkdirs() }

        File(sourceDir, "verified_data.bin").apply {
            writeBytes(ByteArray(8192) { (it % 256).toByte() })
        }

        val profile = BackupProfile(
            id = "verify_profile",
            name = "Verified Backup",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.FULL,
            verifyIntegrity = true
        )

        val report = backupEngine.executeBackup(profile)
        assertEquals(BackupStatus.SUCCESS, report.status)
        assertEquals(1, report.filesCopied)
        assertEquals(1, report.verifiedCount)
        assertEquals(0, report.failedVerificationCount)
        assertTrue(report.summary.contains("Verified: 1 SHA-256 matches"))
    }

    // =========================================================================
    // 1.4.30 Backup Regression: Cancellation, Interruption & Error Handling
    // =========================================================================

    @Test
    fun backupEngine_handlesNonexistentSourceGracefully() = runTest {
        val missingSourceDir = File(testRoot, "MissingDirectoryDoesNotExist")
        val destDir = File(testRoot, "DestGraceful").apply { mkdirs() }

        val profile = BackupProfile(
            id = "missing_src_profile",
            name = "Missing Source",
            sourcePaths = listOf(missingSourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.COPY
        )

        // Preview handles nonexistent source honestly as INACCESSIBLE
        val preview = backupEngine.previewBackup(profile)
        assertEquals(1, preview.inaccessibleItems.size)
        assertEquals(BackupPreviewAction.INACCESSIBLE, preview.inaccessibleItems[0].action)

        // Execution completes with error record instead of crashing
        val report = backupEngine.executeBackup(profile)
        assertEquals(1, report.errors.size)
        assertTrue(report.errors[0].errorMessage.contains("does not exist"))
    }

    @Test
    fun backupEngine_cleansUpTempFilesOnCancellation() = runTest {
        val sourceDir = File(testRoot, "CancelSrc").apply { mkdirs() }
        val destDir = File(testRoot, "CancelDst").apply { mkdirs() }

        // Create a large file
        File(sourceDir, "huge_file.dat").apply {
            writeBytes(ByteArray(256 * 1024) { 0x42 })
        }

        val profile = BackupProfile(
            id = "cancel_profile",
            name = "Cancel Backup",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.COPY
        )

        var cancelled = false
        val job = launch(Dispatchers.Default) {
            try {
                backupEngine.executeBackup(profile) { progress ->
                    if (progress.bytesProcessed > 0) {
                        throw CancellationException("Simulated user cancellation during backup transfer")
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
            }
        }
        job.join()

        assertTrue("Backup job should have handled cancellation", cancelled)

        // Verify that temporary files were cleaned up and no incomplete target file remains
        val leftoverTmp = destDir.walkTopDown().filter { it.name.endsWith(".arca_backup_tmp") }.toList()
        assertTrue("Temporary backup files must be cleaned up on cancellation", leftoverTmp.isEmpty())
        val incompleteTarget = File(destDir, "huge_file.dat")
        assertFalse("Incomplete target file must not be committed to destination", incompleteTarget.exists())
    }

    @Test
    fun backupRegression_handlesRenamedSourceFilesCorrectlyInCopyAndMirrorModes() = runTest {
        val sourceDir = File(testRoot, "RenameSrc").apply { mkdirs() }
        val destDir = File(testRoot, "RenameDst").apply { mkdirs() }

        val originalFile = File(sourceDir, "initial_doc.txt").apply {
            writeText("Original Document Content")
        }

        val copyProfile = BackupProfile(
            id = "rename_copy_profile",
            name = "Rename Copy Profile",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.COPY
        )

        // 1. Initial backup in COPY mode
        val initialReport = backupEngine.executeBackup(copyProfile)
        assertEquals(BackupStatus.SUCCESS, initialReport.status)
        assertEquals(1, initialReport.filesCopied)
        val destInitial = File(destDir, "initial_doc.txt")
        assertTrue(destInitial.exists())

        // 2. Rename file at source
        val renamedFile = File(sourceDir, "renamed_doc.txt")
        assertTrue(originalFile.renameTo(renamedFile))
        assertFalse(originalFile.exists())
        assertTrue(renamedFile.exists())

        // 3. Run preview in COPY mode: should detect renamed file as COPY_NEW, 0 mirror deletions
        val copyPreview = backupEngine.previewBackup(copyProfile)
        assertEquals(1, copyPreview.filesToCopy.size)
        assertEquals("renamed_doc.txt", copyPreview.filesToCopy[0].relativePath)
        assertEquals(0, copyPreview.filesToDeleteInMirror.size)

        // 4. Execute in COPY mode: initial_doc.txt remains safe in destination!
        val copyReport2 = backupEngine.executeBackup(copyProfile)
        assertEquals(BackupStatus.SUCCESS, copyReport2.status)
        assertTrue("Original destination file must NOT be deleted in COPY mode", destInitial.exists())
        assertTrue("Renamed file must be copied to destination", File(destDir, "renamed_doc.txt").exists())

        // 5. Run preview in MIRROR mode: initial_doc.txt must be detected as DELETE_IN_MIRROR
        val mirrorProfile = copyProfile.copy(mode = BackupMode.MIRROR)
        val mirrorPreview = backupEngine.previewBackup(mirrorProfile)
        assertEquals(1, mirrorPreview.filesToDeleteInMirror.size)
        assertEquals("initial_doc.txt", mirrorPreview.filesToDeleteInMirror[0].relativePath)
        assertTrue(mirrorPreview.requiresMirrorConfirmation)

        // 6. Execute in MIRROR mode with confirmation
        val mirrorReport = backupEngine.executeBackup(mirrorProfile, confirmedMirrorDeletion = true)
        assertEquals(BackupStatus.SUCCESS, mirrorReport.status)
        assertEquals(1, mirrorReport.deletedInMirrorCount)
        assertFalse("Stale initial_doc.txt must be pruned in MIRROR mode", destInitial.exists())
        assertTrue("Renamed file must remain in destination", File(destDir, "renamed_doc.txt").exists())
        assertTrue("Source file must remain untouched", renamedFile.exists())
    }

    @Test
    fun backupRegression_handlesDeletedSourceFilesCorrectlyInCopyAndMirrorModes() = runTest {
        val sourceDir = File(testRoot, "DeleteSrc").apply { mkdirs() }
        val destDir = File(testRoot, "DeleteDst").apply { mkdirs() }

        val fileKeep = File(sourceDir, "keep.txt").apply { writeText("Keep this") }
        val fileDelete = File(sourceDir, "remove.txt").apply { writeText("Delete this at source") }

        val copyProfile = BackupProfile(
            id = "delete_copy_profile",
            name = "Delete Test Profile",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.COPY
        )

        // Initial backup: both files copied to destination
        backupEngine.executeBackup(copyProfile)
        val destKeep = File(destDir, "keep.txt")
        val destDelete = File(destDir, "remove.txt")
        assertTrue(destKeep.exists())
        assertTrue(destDelete.exists())

        // Delete file at source
        assertTrue(fileDelete.delete())
        assertFalse(fileDelete.exists())

        // Test in COPY mode: destination file MUST be preserved
        val copyPreview = backupEngine.previewBackup(copyProfile)
        assertEquals(0, copyPreview.filesToDeleteInMirror.size)
        assertEquals(1, copyPreview.filesAlreadyMatching.size)

        backupEngine.executeBackup(copyProfile)
        assertTrue("Destination file must NOT be deleted when source is removed in COPY mode", destDelete.exists())

        // Test in MIRROR mode: requires confirmation, prunes when confirmed
        val mirrorProfile = copyProfile.copy(mode = BackupMode.MIRROR)
        val mirrorPreview = backupEngine.previewBackup(mirrorProfile)
        assertEquals(1, mirrorPreview.filesToDeleteInMirror.size)
        assertEquals("remove.txt", mirrorPreview.filesToDeleteInMirror[0].relativePath)

        val mirrorReport = backupEngine.executeBackup(mirrorProfile, confirmedMirrorDeletion = true)
        assertEquals(1, mirrorReport.deletedInMirrorCount)
        assertFalse("Destination file must be pruned in MIRROR mode", destDelete.exists())
        assertTrue("Remaining file must be kept in destination", destKeep.exists())
        assertTrue("Source keep file was never deleted", fileKeep.exists())
    }

    @Test
    fun backupRegression_incrementalVerification_detectsCorruptedDestinationFile() = runTest {
        val sourceDir = File(testRoot, "HealSrc").apply { mkdirs() }
        val destDir = File(testRoot, "HealDst").apply { mkdirs() }

        val validData = "Authentic Data Block Number 12345"
        val corruptedData = "Corrupted Data Block Number 12345"
        assertEquals(validData.length, corruptedData.length)

        val srcFile = File(sourceDir, "document.dat").apply { writeText(validData) }
        val destFile = File(destDir, "document.dat").apply { writeText(corruptedData) }

        // Set identical timestamps
        val testTime = 1715000000000L
        srcFile.setLastModified(testTime)
        destFile.setLastModified(testTime)

        val profile = BackupProfile(
            id = "heal_profile",
            name = "Heal Corrupted Profile",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = destDir.absolutePath,
            mode = BackupMode.INCREMENTAL,
            verifyIntegrity = true
        )

        // Without hash verification, sizes and timestamps match.
        // BUT with verifyIntegrity = true, hash mismatch MUST be detected during incremental backup and healed!
        val report = backupEngine.executeBackup(profile)
        assertEquals(BackupStatus.SUCCESS, report.status)
        assertEquals(1, report.filesCopied)
        assertEquals(0, report.filesSkipped)
        assertEquals(1, report.verifiedCount)

        // Verify destination file was healed to match authentic source
        assertEquals(validData, destFile.readText())
    }

    @Test
    fun backupRegression_handlesRemovableStorageDisconnectionGracefully() = runTest {
        val sourceDir = File(testRoot, "UsbSrc").apply { mkdirs() }
        File(sourceDir, "important.pdf").apply { writeText("Important data") }

        // Invalid or disconnected SD card / USB storage mount point
        val disconnectedSdCardPath = if (System.getProperty("os.name")?.lowercase()?.contains("win") == true) {
            "Z:\\NonExistentSdCardVolume\\ARCA_Backup"
        } else {
            "/mnt/media_rw/UNMOUNTED_SD_1234/ARCA_Backup"
        }

        val profile = BackupProfile(
            id = "usb_disconnected_profile",
            name = "SD Card Backup",
            sourcePaths = listOf(sourceDir.absolutePath),
            destinationPath = disconnectedSdCardPath,
            mode = BackupMode.COPY
        )

        val report = backupEngine.executeBackup(profile)
        assertEquals(BackupStatus.FAILED, report.status)
        assertTrue(report.errors.isNotEmpty())
        assertTrue("Source files must remain 100% untouched on storage error", File(sourceDir, "important.pdf").exists())
    }
}
