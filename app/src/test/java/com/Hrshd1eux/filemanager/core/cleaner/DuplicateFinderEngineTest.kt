package com.Hrshd1eux.filemanager.core.cleaner

import com.Hrshd1eux.filemanager.core.cleaner.engine.DuplicateFinderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateFinderEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var engine: DuplicateFinderEngine
    private lateinit var rootDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        engine = DuplicateFinderEngine(ioDispatcher = testDispatcher)
        rootDir = tempFolder.newFolder("duplicate_finder_test_root")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scanDuplicates_findsDuplicatesWithDifferentNames() = runTest {
        val content = "This is exact duplicate payload across multiple files."
        val subDir = File(rootDir, "subfolder").apply { mkdir() }

        // Create 3 files with identical content but different names/locations
        File(rootDir, "original.txt").writeText(content)
        File(rootDir, "copy_of_original.txt").writeText(content)
        File(subDir, "nested_backup.txt").writeText(content)

        // Unique file with different content & length
        File(rootDir, "unique.txt").writeText("Unique content!")

        val result = engine.scanDuplicates(rootDir.absolutePath)

        assertEquals(1, result.groups.size)
        val group = result.groups.first()
        assertEquals(3, group.files.size)
        val expectedWasted = content.toByteArray().size.toLong() * 2L // 2 duplicate copies
        assertEquals(expectedWasted, group.wastedBytes)
        assertEquals(expectedWasted, result.totalWastedBytes)
    }

    @Test
    fun scanDuplicates_sameSizeDifferentContent_notFlaggedAsDuplicates() = runTest {
        // Two files with exact same size (10 bytes) but different bytes
        val fileA = File(rootDir, "fileA.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)) }
        val fileB = File(rootDir, "fileB.bin").apply { writeBytes(byteArrayOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)) }

        val result = engine.scanDuplicates(rootDir.absolutePath)

        // SHA-256 differs -> 0 duplicate groups
        assertTrue(result.groups.isEmpty())
        assertEquals(0, result.totalDuplicateFilesCount)
        assertEquals(0L, result.totalWastedBytes)
    }

    @Test
    fun scanDuplicates_multipleDuplicateGroups_sortedByWastedBytesDescending() = runTest {
        // Group 1: 3 copies of 100-byte file (wasted = 200 bytes)
        val payload100 = ByteArray(100) { 1 }
        File(rootDir, "doc1.dat").writeBytes(payload100)
        File(rootDir, "doc2.dat").writeBytes(payload100)
        File(rootDir, "doc3.dat").writeBytes(payload100)

        // Group 2: 2 copies of 500-byte file (wasted = 500 bytes)
        val payload500 = ByteArray(500) { 2 }
        File(rootDir, "heavy1.dat").writeBytes(payload500)
        File(rootDir, "heavy2.dat").writeBytes(payload500)

        val result = engine.scanDuplicates(rootDir.absolutePath)

        assertEquals(2, result.groups.size)
        // Group 2 (500 bytes wasted) should be first
        assertEquals(500L, result.groups[0].sizePerFile)
        assertEquals(500L, result.groups[0].wastedBytes)

        // Group 1 (200 bytes wasted) should be second
        assertEquals(100L, result.groups[1].sizePerFile)
        assertEquals(200L, result.groups[1].wastedBytes)

        assertEquals(700L, result.totalWastedBytes)
        assertEquals(5, result.totalDuplicateFilesCount)
    }

    @Test
    fun scanDuplicates_emptyDirectory_returnsEmptyResult() = runTest {
        val emptyDir = tempFolder.newFolder("empty_dir")
        val result = engine.scanDuplicates(emptyDir.absolutePath)

        assertTrue(result.groups.isEmpty())
        assertEquals(0, result.totalDuplicateFilesCount)
        assertEquals(0L, result.totalWastedBytes)
    }
}
