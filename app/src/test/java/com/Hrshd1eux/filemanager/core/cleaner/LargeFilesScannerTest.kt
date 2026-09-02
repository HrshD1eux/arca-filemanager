package com.Hrshd1eux.filemanager.core.cleaner

import com.Hrshd1eux.filemanager.core.cleaner.model.LargeFileThreshold
import com.Hrshd1eux.filemanager.core.cleaner.scanner.LargeFilesScanner
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
import java.io.RandomAccessFile

@OptIn(ExperimentalCoroutinesApi::class)
class LargeFilesScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scanner: LargeFilesScanner
    private lateinit var rootDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scanner = LargeFilesScanner(ioDispatcher = testDispatcher)
        rootDir = tempFolder.newFolder("large_files_test_root")

        // Create sparse files of various sizes using RandomAccessFile.setLength()
        createSparseFile("tiny.txt", 1024L) // 1 KB
        createSparseFile("small_video.mp4", 50L * 1024L * 1024L) // 50 MB
        createSparseFile("medium_backup.zip", 150L * 1024L * 1024L) // 150 MB (> 100 MB)
        createSparseFile("heavy_movie.mkv", 600L * 1024L * 1024L) // 600 MB (> 500 MB)
        createSparseFile("huge_image.iso", 1500L * 1024L * 1024L) // 1.5 GB (> 1 GB)
    }

    private fun createSparseFile(name: String, sizeBytes: Long) {
        val file = File(rootDir, name)
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scanLargeFiles_threshold100MB_returnsAllFilesAbove100MB() = runTest {
        val results = scanner.scanLargeFiles(rootDir.absolutePath, LargeFileThreshold.MB_100)

        assertEquals(3, results.size)
        // Verify size descending sort order
        assertEquals("huge_image.iso", results[0].name)
        assertEquals(1500L * 1024L * 1024L, results[0].size)

        assertEquals("heavy_movie.mkv", results[1].name)
        assertEquals(600L * 1024L * 1024L, results[1].size)

        assertEquals("medium_backup.zip", results[2].name)
        assertEquals(150L * 1024L * 1024L, results[2].size)
    }

    @Test
    fun scanLargeFiles_threshold500MB_returnsAllFilesAbove500MB() = runTest {
        val results = scanner.scanLargeFiles(rootDir.absolutePath, LargeFileThreshold.MB_500)

        assertEquals(2, results.size)
        assertEquals("huge_image.iso", results[0].name)
        assertEquals("heavy_movie.mkv", results[1].name)
    }

    @Test
    fun scanLargeFiles_threshold1GB_returnsOnlyFilesAbove1GB() = runTest {
        val results = scanner.scanLargeFiles(rootDir.absolutePath, LargeFileThreshold.GB_1)

        assertEquals(1, results.size)
        assertEquals("huge_image.iso", results[0].name)
    }

    @Test
    fun scanLargeFiles_emptyDir_returnsEmptyList() = runTest {
        val emptyDir = tempFolder.newFolder("empty_root")
        val results = scanner.scanLargeFiles(emptyDir.absolutePath, LargeFileThreshold.MB_100)
        assertTrue(results.isEmpty())
    }
}
