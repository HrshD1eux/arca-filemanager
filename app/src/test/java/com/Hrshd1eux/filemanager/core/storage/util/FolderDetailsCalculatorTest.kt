package com.Hrshd1eux.filemanager.core.storage.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FolderDetailsCalculatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun calculateDetails_emptyFolder_returnsZeros() = runTest(testDispatcher) {
        val emptyDir = tempFolder.newFolder("empty")
        val details = FolderDetailsCalculator.calculateDetails(emptyDir, testDispatcher)

        assertEquals(0L, details.totalFiles)
        assertEquals(0L, details.totalFolders)
        assertEquals(0L, details.totalBytes)
    }

    @Test
    fun calculateDetails_nestedStructure_countsAccurately() = runTest(testDispatcher) {
        val root = tempFolder.newFolder("nested_root")
        val sub1 = File(root, "sub1").apply { mkdir() }
        val sub2 = File(root, "sub2").apply { mkdir() }
        val sub1_1 = File(sub1, "inner").apply { mkdir() }

        val f1 = File(root, "file1.txt").apply { writeText("12345") } // 5 bytes
        val f2 = File(sub1, "file2.txt").apply { writeText("1234567890") } // 10 bytes
        val f3 = File(sub1_1, "file3.txt").apply { writeText("12345") } // 5 bytes

        val details = FolderDetailsCalculator.calculateDetails(root, testDispatcher)

        assertEquals(3L, details.totalFiles)
        assertEquals(3L, details.totalFolders)
        assertEquals(20L, details.totalBytes)
    }
}
