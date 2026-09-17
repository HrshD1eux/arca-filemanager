package com.Hrshd1eux.filemanager.core.storage.util

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderComparatorTest {

    @Test
    fun compareFolders_withEmptyLists_returnsIdenticalReport() {
        val report = FolderComparator.compareFolders(emptyList(), emptyList())
        assertTrue("Empty folders should be identical", report.isIdentical)
        assertEquals(0, report.totalCount)
        assertEquals(0, report.identicalCount)
        assertEquals(0, report.modifiedCount)
        assertEquals(0, report.leftOnlyCount)
        assertEquals(0, report.rightOnlyCount)
    }

    @Test
    fun compareFolders_withIdenticalItems_returnsIdentical() {
        val now = System.currentTimeMillis()
        val left = listOf(
            StorageNode("1", "Docs", "/left/Docs", isDirectory = true, size = 0L, lastModified = now),
            StorageNode("2", "notes.txt", "/left/notes.txt", isDirectory = false, size = 1024L, lastModified = now)
        )
        val right = listOf(
            StorageNode("3", "Docs", "/right/Docs", isDirectory = true, size = 0L, lastModified = now),
            StorageNode("4", "notes.txt", "/right/notes.txt", isDirectory = false, size = 1024L, lastModified = now + 500L) // < 2s delta
        )

        val report = FolderComparator.compareFolders(left, right)
        assertTrue("Report should be identical", report.isIdentical)
        assertEquals(2, report.totalCount)
        assertEquals(2, report.identicalCount)
        assertEquals(0, report.modifiedCount)
        assertEquals(0, report.leftOnlyCount)
        assertEquals(0, report.rightOnlyCount)

        val dirItem = report.items.find { it.name == "Docs" }
        assertNotNull(dirItem)
        assertEquals(ComparisonStatus.IDENTICAL, dirItem?.status)
        assertTrue(dirItem?.isDirectory == true)
    }

    @Test
    fun compareFolders_withModifiedFileSize_detectsModified() {
        val now = System.currentTimeMillis()
        val left = listOf(
            StorageNode("1", "file.dat", "/left/file.dat", isDirectory = false, size = 2048L, lastModified = now)
        )
        val right = listOf(
            StorageNode("2", "file.dat", "/right/file.dat", isDirectory = false, size = 4096L, lastModified = now)
        )

        val report = FolderComparator.compareFolders(left, right)
        assertFalse("Report should not be identical", report.isIdentical)
        assertEquals(1, report.totalCount)
        assertEquals(0, report.identicalCount)
        assertEquals(1, report.modifiedCount)
        assertEquals(ComparisonStatus.MODIFIED, report.items.first().status)
    }

    @Test
    fun compareFolders_withModifiedTimestamp_detectsModified() {
        val now = System.currentTimeMillis()
        val left = listOf(
            StorageNode("1", "log.txt", "/left/log.txt", isDirectory = false, size = 500L, lastModified = now)
        )
        val right = listOf(
            StorageNode("2", "log.txt", "/right/log.txt", isDirectory = false, size = 500L, lastModified = now + 10000L) // 10s difference
        )

        val report = FolderComparator.compareFolders(left, right)
        assertFalse("Report should not be identical", report.isIdentical)
        assertEquals(1, report.modifiedCount)
        assertEquals(ComparisonStatus.MODIFIED, report.items.first().status)
    }

    @Test
    fun compareFolders_withTypeMismatch_detectsModified() {
        val now = System.currentTimeMillis()
        val left = listOf(
            StorageNode("1", "Target", "/left/Target", isDirectory = true, size = 0L, lastModified = now)
        )
        val right = listOf(
            StorageNode("2", "Target", "/right/Target", isDirectory = false, size = 100L, lastModified = now)
        )

        val report = FolderComparator.compareFolders(left, right)
        assertFalse("Type mismatch should be marked modified", report.isIdentical)
        assertEquals(1, report.modifiedCount)
        assertEquals(ComparisonStatus.MODIFIED, report.items.first().status)
    }

    @Test
    fun compareFolders_withUniqueLeftAndRight_detectsUniqueStatuses() {
        val now = System.currentTimeMillis()
        val left = listOf(
            StorageNode("1", "left_only.txt", "/left/left_only.txt", isDirectory = false, size = 100L, lastModified = now)
        )
        val right = listOf(
            StorageNode("2", "right_only.txt", "/right/right_only.txt", isDirectory = false, size = 200L, lastModified = now)
        )

        val report = FolderComparator.compareFolders(left, right)
        assertFalse(report.isIdentical)
        assertEquals(2, report.totalCount)
        assertEquals(1, report.leftOnlyCount)
        assertEquals(1, report.rightOnlyCount)

        val leftItem = report.items.find { it.name == "left_only.txt" }
        assertNotNull(leftItem)
        assertEquals(ComparisonStatus.UNIQUE_LEFT, leftItem?.status)
        assertNotNull(leftItem?.leftNode)
        assertNull(leftItem?.rightNode)

        val rightItem = report.items.find { it.name == "right_only.txt" }
        assertNotNull(rightItem)
        assertEquals(ComparisonStatus.UNIQUE_RIGHT, rightItem?.status)
        assertNull(rightItem?.leftNode)
        assertNotNull(rightItem?.rightNode)
    }

    @Test
    fun compareFolders_sortsDirectoriesFirstThenAlphabetical() {
        val now = System.currentTimeMillis()
        val left = listOf(
            StorageNode("1", "zebra.txt", "/left/zebra.txt", isDirectory = false, size = 10L, lastModified = now),
            StorageNode("2", "alpha_folder", "/left/alpha_folder", isDirectory = true, size = 0L, lastModified = now)
        )
        val right = listOf(
            StorageNode("3", "beta_folder", "/right/beta_folder", isDirectory = true, size = 0L, lastModified = now),
            StorageNode("4", "apple.txt", "/right/apple.txt", isDirectory = false, size = 10L, lastModified = now)
        )

        val report = FolderComparator.compareFolders(left, right)
        val names = report.items.map { it.name }
        assertEquals(listOf("alpha_folder", "beta_folder", "apple.txt", "zebra.txt"), names)
    }
}
