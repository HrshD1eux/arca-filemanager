package com.Hrshd1eux.filemanager.core.storage.analyzer

import com.Hrshd1eux.filemanager.core.cleaner.engine.CleanupSafetyValidator
import com.Hrshd1eux.filemanager.core.cleaner.model.AnalyzerSortBy
import com.Hrshd1eux.filemanager.core.cleaner.model.CleanupCandidate
import com.Hrshd1eux.filemanager.core.cleaner.model.DeletionTruthfulness
import com.Hrshd1eux.filemanager.core.cleaner.model.DuplicateMatchLevel
import com.Hrshd1eux.filemanager.core.cleaner.model.LargeFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.model.OldFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.model.RevalidationStatus
import com.Hrshd1eux.filemanager.core.storage.model.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
 * Comprehensive Regression & Safety Test Suite for Storage Analyzer 2.0 (Milestones 1.4.11 – 1.4.20).
 *
 * Covers:
 * - 1.4.11 & 1.4.12: Large & Old file detection, configurable thresholds, sorting, honest disclaimers.
 * - 1.4.13: Duplicate detection staging (size -> metadata -> SHA-256) separating confirmed vs possible candidates.
 * - 1.4.14: Storage category breakdown non-overlapping partition guarantee with zero double-counting.
 * - 1.4.15: Safe cleanup review inventory and zero automatic deletion enforcement.
 * - 1.4.16: Cleanup safety pre-flight revalidation, stale file protection, directory emptiness, scoped storage protection.
 * - 1.4.17–1.4.20: Large tree traversal, archive recognition, empty directory scanning, diagnostic reporting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageAnalyzerRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var engine: StorageAnalyzer2Engine
    private lateinit var safetyValidator: CleanupSafetyValidator
    private lateinit var testRoot: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        engine = StorageAnalyzer2Engine(ioDispatcher = testDispatcher)
        safetyValidator = CleanupSafetyValidator(ioDispatcher = testDispatcher)
        testRoot = tempFolder.newFolder("storage_analyzer_2_test_root")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. Large Files Detection & Configurable Thresholds (1.4.11 & 1.4.12)
    // =========================================================================

    @Test
    fun scanLargeFiles_filtersByConfigurableThresholdAndSorts() = runTest {
        // Create files of various sizes
        val file30MB = File(testRoot, "file_30mb.dat").apply {
            writeBytes(ByteArray(30 * 1024)) // Representing 30KB scaled for unit test
        }
        val file60MB = File(testRoot, "file_60mb.dat").apply {
            writeBytes(ByteArray(60 * 1024))
        }
        val file120MB = File(testRoot, "file_120mb.dat").apply {
            writeBytes(ByteArray(120 * 1024))
        }

        // Custom threshold for test: threshold 50KB
        // Test standard threshold logic
        val (allFiles, diag) = engine.scanLargeFiles(
            rootPath = testRoot.absolutePath,
            threshold = LargeFileThreshold.MB_25, // default minBytes is 25 * 1024 * 1024, our small files won't match standard minBytes unless sized appropriately
            sortBy = AnalyzerSortBy.SIZE_DESC
        )
        assertNotNull(diag)
        assertEquals(3, diag.scannedFilesCount)

        // Now test with files that exceed threshold
        // Create 26MB and 55MB sparse/mock files by writing at offset or length
        val bigFileA = File(testRoot, "big_a.bin").apply {
            java.io.RandomAccessFile(this, "rw").use { it.setLength(30L * 1024 * 1024) }
        }
        val bigFileB = File(testRoot, "big_b.bin").apply {
            java.io.RandomAccessFile(this, "rw").use { it.setLength(60L * 1024 * 1024) }
        }

        // With MB_25 threshold (>= 25MB) -> both bigFileA and bigFileB match
        val (candidates25, _) = engine.scanLargeFiles(
            rootPath = testRoot.absolutePath,
            threshold = LargeFileThreshold.MB_25,
            sortBy = AnalyzerSortBy.SIZE_DESC
        )
        assertEquals(2, candidates25.size)
        assertEquals(bigFileB.absolutePath, candidates25[0].path) // 60MB before 30MB
        assertEquals(bigFileA.absolutePath, candidates25[1].path)

        // With MB_50 threshold (>= 50MB) -> only bigFileB matches
        val (candidates50, _) = engine.scanLargeFiles(
            rootPath = testRoot.absolutePath,
            threshold = LargeFileThreshold.MB_50,
            sortBy = AnalyzerSortBy.SIZE_DESC
        )
        assertEquals(1, candidates50.size)
        assertEquals(bigFileB.absolutePath, candidates50[0].path)

        // Test ascending sort
        val (candidatesAsc, _) = engine.scanLargeFiles(
            rootPath = testRoot.absolutePath,
            threshold = LargeFileThreshold.MB_25,
            sortBy = AnalyzerSortBy.SIZE_ASC
        )
        assertEquals(bigFileA.absolutePath, candidatesAsc[0].path)
        assertEquals(bigFileB.absolutePath, candidatesAsc[1].path)
    }

    // =========================================================================
    // 2. Old Files Detection & Modification Date Disclaimer (1.4.11 & 1.4.12)
    // =========================================================================

    @Test
    fun scanOldFiles_filtersByAgeAndExplainsModificationTimeDisclaimer() = runTest {
        val now = System.currentTimeMillis()
        val fourMonthsAgo = now - (120L * 24 * 60 * 60 * 1000)
        val eightMonthsAgo = now - (240L * 24 * 60 * 60 * 1000)
        val freshTime = now - (5L * 24 * 60 * 60 * 1000)

        val freshFile = File(testRoot, "fresh.txt").apply {
            writeText("Freshly modified file")
            setLastModified(freshTime)
        }
        val fourMonthFile = File(testRoot, "reference_doc.pdf").apply {
            writeText("Important reference document untouched for 4 months")
            setLastModified(fourMonthsAgo)
        }
        val eightMonthFile = File(testRoot, "old_archive.zip").apply {
            writeText("Old archive untouched for 8 months")
            setLastModified(eightMonthsAgo)
        }

        // Test with 3 Months threshold -> should match fourMonthFile and eightMonthFile
        val (oldCandidates3M, diag) = engine.scanOldFiles(
            rootPath = testRoot.absolutePath,
            threshold = OldFileThreshold.MONTHS_3,
            sortBy = AnalyzerSortBy.DATE_ASC
        )
        assertEquals(2, oldCandidates3M.size)
        // DATE_ASC: oldest first -> eightMonthsAgo before fourMonthsAgo
        assertEquals(eightMonthFile.absolutePath, oldCandidates3M[0].path)
        assertEquals(fourMonthFile.absolutePath, oldCandidates3M[1].path)

        // Test with 6 Months threshold -> should only match eightMonthFile
        val (oldCandidates6M, _) = engine.scanOldFiles(
            rootPath = testRoot.absolutePath,
            threshold = OldFileThreshold.MONTHS_6,
            sortBy = AnalyzerSortBy.DATE_DESC
        )
        assertEquals(1, oldCandidates6M.size)
        assertEquals(eightMonthFile.absolutePath, oldCandidates6M[0].path)

        // Verify Modification Date Disclaimer requirement (1.4.12):
        // Must clearly explain that modification time does not prove a file is unnecessary.
        val disclaimer = DeletionTruthfulness.MODIFICATION_DATE_DISCLAIMER
        assertTrue(disclaimer.contains("Modification time indicates when a file was last written"))
        assertTrue(disclaimer.contains("does not prove the file is unnecessary"))
    }

    // =========================================================================
    // 3. Staged Duplicate Detection (1.4.13)
    // =========================================================================

    @Test
    fun scanDuplicateCandidatesStaged_distinguishesConfirmedHashFromPossibleMetadata() = runTest {
        val payloadA = "Exact duplicate string content across two different files."
        val file1 = File(testRoot, "original.txt").apply { writeText(payloadA) }
        val file2 = File(testRoot, "copy.txt").apply { writeText(payloadA) }

        // Two files with identical length but different content
        val payloadB1 = "AAA_10_bytes_unique"
        val payloadB2 = "BBB_10_bytes_unique"
        val fileDifferent1 = File(testRoot, "diff1.txt").apply { writeText(payloadB1) }
        val fileDifferent2 = File(testRoot, "diff2.txt").apply { writeText(payloadB2) }

        // Stage 1 & 2: Without cryptographic hashes -> returns preliminary possible candidates based on size
        val (preliminaryResult, _) = engine.scanDuplicateCandidatesStaged(
            rootPath = testRoot.absolutePath,
            computeHashes = false
        )
        // Both size buckets have >= 2 files, so both are returned with matchLevel = POSSIBLE_METADATA_CANDIDATE
        assertTrue(preliminaryResult.groups.all { it.matchLevel == DuplicateMatchLevel.POSSIBLE_METADATA_CANDIDATE })

        // Stage 3 & 4: With cryptographic SHA-256 calculation
        val (confirmedResult, diag) = engine.scanDuplicateCandidatesStaged(
            rootPath = testRoot.absolutePath,
            computeHashes = true
        )
        // Only file1 and file2 have identical SHA-256; fileDifferent1 and fileDifferent2 differ!
        assertEquals(1, confirmedResult.groups.size)
        val confirmedGroup = confirmedResult.groups.first()
        assertEquals(DuplicateMatchLevel.CONFIRMED_HASH_MATCH, confirmedGroup.matchLevel)
        assertEquals(2, confirmedGroup.files.size)
        assertEquals(payloadA.toByteArray().size.toLong(), confirmedGroup.wastedBytes)

        // Verify Duplicate Certainty Note requirement (1.4.13)
        val note = DeletionTruthfulness.DUPLICATE_CERTAINTY_NOTE
        assertTrue(note.contains("Matching names or sizes alone does not prove"))
    }

    // =========================================================================
    // 4. Category Breakdown Non-Overlapping Partition Guarantee (1.4.14)
    // =========================================================================

    @Test
    fun scanCategoryBreakdown_guaranteesZeroDoubleCounting() = runTest {
        // Create files in each category
        val img = File(testRoot, "image.jpg").apply { writeBytes(ByteArray(1000)) }
        val vid = File(testRoot, "video.mp4").apply { writeBytes(ByteArray(2000)) }
        val aud = File(testRoot, "song.mp3").apply { writeBytes(ByteArray(1500)) }
        val doc = File(testRoot, "document.pdf").apply { writeBytes(ByteArray(500)) }
        val arc = File(testRoot, "archive.zip").apply { writeBytes(ByteArray(3000)) }
        val oth = File(testRoot, "misc.bin").apply { writeBytes(ByteArray(700)) }

        val expectedTotal = 1000L + 2000L + 1500L + 500L + 3000L + 700L

        val (breakdown, diagnostics) = engine.scanCategoryBreakdown(testRoot.absolutePath)

        // Strict non-overlapping partition check: sum of all categories must EQUAL total scanned bytes
        val categorySum = breakdown.categories.sumOf { it.byteCount }
        assertEquals(expectedTotal, categorySum)
        assertEquals(6, diagnostics.scannedFilesCount)
        assertFalse("Storage measurements must be honest and not marked as estimates", diagnostics.isEstimate)

        // Verify each category size
        assertEquals(1000L, breakdown.categories.first { it.category == StorageCategory.IMAGES }.byteCount)
        assertEquals(2000L, breakdown.categories.first { it.category == StorageCategory.VIDEOS }.byteCount)
        assertEquals(1500L, breakdown.categories.first { it.category == StorageCategory.AUDIO }.byteCount)
        assertEquals(500L, breakdown.categories.first { it.category == StorageCategory.DOCUMENTS }.byteCount)
        assertEquals(3000L, breakdown.categories.first { it.category == StorageCategory.ARCHIVES }.byteCount)
        assertEquals(700L, breakdown.categories.first { it.category == StorageCategory.OTHER }.byteCount)
    }

    // =========================================================================
    // 5. Empty Folders Detection (1.4.11)
    // =========================================================================

    @Test
    fun scanEmptyFolders_detectsOnlyZeroContentDirectories() = runTest {
        val emptyDirA = File(testRoot, "empty_a").apply { mkdir() }
        val emptyDirB = File(testRoot, "empty_b").apply { mkdir() }
        val nonEmptyDir = File(testRoot, "non_empty").apply {
            mkdir()
            File(this, "inside.txt").writeText("content")
        }

        val (emptyFolders, diag) = engine.scanEmptyFolders(testRoot.absolutePath)

        assertEquals(2, emptyFolders.size)
        val paths = emptyFolders.map { it.path }
        assertTrue(paths.contains(emptyDirA.absolutePath))
        assertTrue(paths.contains(emptyDirB.absolutePath))
        assertFalse(paths.contains(nonEmptyDir.absolutePath))
    }

    // =========================================================================
    // 6. Archive Format Recognition (1.4.11)
    // =========================================================================

    @Test
    fun scanArchives_detectsAllSupportedFormats() = runTest {
        val formats = listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "apk")
        formats.forEachIndexed { i, ext ->
            File(testRoot, "archive_$i.$ext").apply {
                writeBytes(ByteArray(100 * (i + 1)))
            }
        }
        // Non-archive file
        File(testRoot, "not_archive.txt").writeText("hello")

        val (archives, _) = engine.scanArchives(testRoot.absolutePath)
        assertEquals(formats.size, archives.size)
        assertTrue(archives.all { it.category == StorageCategory.ARCHIVES })
    }

    // =========================================================================
    // 7. Cleanup Safety Engine & Pre-Flight Revalidation (1.4.15 & 1.4.16)
    // =========================================================================

    @Test
    fun cleanupSafety_revalidationProtectsAgainstStaleModifiedTargets() = runTest {
        val targetFile = File(testRoot, "safety_test.txt").apply {
            writeText("Original content")
        }

        val candidate = CleanupCandidate(
            id = targetFile.absolutePath,
            path = targetFile.absolutePath,
            name = targetFile.name,
            size = targetFile.length(),
            lastModified = targetFile.lastModified(),
            isDirectory = false,
            category = StorageCategory.DOCUMENTS,
            flagReason = "Test candidate"
        )

        // 1. Initial revalidation: file exists and matches size/timestamp -> VALID
        val initialValidation = safetyValidator.revalidateTarget(candidate)
        assertEquals(RevalidationStatus.VALID, initialValidation.status)

        // 2. Simulate external modification (e.g. user or app edited file after scan)
        targetFile.writeText("Modified new content with different length and timestamp")
        targetFile.setLastModified(System.currentTimeMillis() + 5000)

        // Pre-flight revalidation MUST reject stale modified file
        val staleValidation = safetyValidator.revalidateTarget(candidate)
        assertEquals(RevalidationStatus.STALE_MODIFIED, staleValidation.status)

        // 3. Attempting deletion must abort deletion for stale file
        val report = safetyValidator.executeSafeDeletion(listOf(candidate))
        assertEquals(0, report.deletedCount)
        assertEquals(1, report.skippedStaleCount)
        assertTrue(targetFile.exists()) // File is safely preserved!
    }

    @Test
    fun cleanupSafety_revalidationProtectsAndroidRestrictedPaths() = runTest {
        // Simulate an Android restricted path such as /Android/data or /Android/obb
        val restrictedCandidate = CleanupCandidate(
            id = "/storage/emulated/0/Android/data/com.example/cache.tmp",
            path = "/storage/emulated/0/Android/data/com.example/cache.tmp",
            name = "cache.tmp",
            size = 1024L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false,
            category = StorageCategory.OTHER,
            flagReason = "Restricted directory candidate"
        )

        val validation = safetyValidator.revalidateTarget(restrictedCandidate)
        assertEquals(RevalidationStatus.RESTRICTED_ACCESS, validation.status)

        val report = safetyValidator.executeSafeDeletion(listOf(restrictedCandidate))
        assertEquals(0, report.deletedCount)
        assertEquals(1, report.restrictedCount)
    }

    @Test
    fun cleanupSafety_revalidationProtectsEmptyFolderThatBecameNonEmpty() = runTest {
        val folder = File(testRoot, "empty_dir_to_clean").apply { mkdir() }

        val candidate = CleanupCandidate(
            id = folder.absolutePath,
            path = folder.absolutePath,
            name = folder.name,
            size = 0L,
            lastModified = folder.lastModified(),
            isDirectory = true,
            category = StorageCategory.OTHER,
            flagReason = "Empty directory"
        )

        // Valid while empty
        assertEquals(RevalidationStatus.VALID, safetyValidator.revalidateTarget(candidate).status)

        // An external process writes a file into the directory
        File(folder, "new_file.txt").writeText("Surprise file added!")

        // Pre-flight check MUST abort deletion because directory is no longer empty!
        val validationAfterAdd = safetyValidator.revalidateTarget(candidate)
        assertEquals(RevalidationStatus.DIRECTORY_NOT_EMPTY, validationAfterAdd.status)

        val report = safetyValidator.executeSafeDeletion(listOf(candidate))
        assertEquals(0, report.deletedCount)
        assertTrue(folder.exists())
    }

    @Test
    fun cleanupSafety_executeSafeDeletion_deletesValidTargetsSafely() = runTest {
        val file1 = File(testRoot, "valid_delete_1.tmp").apply { writeText("Temp 1") }
        val file2 = File(testRoot, "valid_delete_2.tmp").apply { writeText("Temp 2") }

        val candidates = listOf(
            CleanupCandidate(
                id = file1.absolutePath,
                path = file1.absolutePath,
                name = file1.name,
                size = file1.length(),
                lastModified = file1.lastModified(),
                isDirectory = false,
                category = StorageCategory.OTHER,
                flagReason = "Temporary file"
            ),
            CleanupCandidate(
                id = file2.absolutePath,
                path = file2.absolutePath,
                name = file2.name,
                size = file2.length(),
                lastModified = file2.lastModified(),
                isDirectory = false,
                category = StorageCategory.OTHER,
                flagReason = "Temporary file"
            )
        )

        val report = safetyValidator.executeSafeDeletion(candidates)
        assertEquals(2, report.deletedCount)
        assertEquals(0, report.failedCount)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    @Test
    fun cleanupSafety_flashStorageDisclaimerTruthfulness() {
        // Milestone 1.4.16: Avoid claiming that ordinary deletion securely erases data from flash storage.
        val disclaimer = DeletionTruthfulness.FLASH_STORAGE_WEAR_LEVELING_NOTE
        assertTrue(disclaimer.contains("unlinks file directory entries"))
        assertTrue(disclaimer.contains("Wear-leveling on flash memory"))
        assertTrue(disclaimer.contains("does not cryptographically overwrite physical storage blocks"))
    }
}
