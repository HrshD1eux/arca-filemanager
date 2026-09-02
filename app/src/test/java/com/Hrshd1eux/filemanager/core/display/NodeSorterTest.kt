package com.Hrshd1eux.filemanager.core.display

import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.util.NodeSorter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import org.junit.Assert.assertEquals
import org.junit.Test

class NodeSorterTest {

    private val folderAlpha = StorageNode(id = "1", name = "AlphaFolder", path = "/a", isDirectory = true, lastModified = 1000L, size = 0L)
    private val folderBeta = StorageNode(id = "2", name = "BetaFolder", path = "/b", isDirectory = true, lastModified = 3000L, size = 0L)
    private val fileA = StorageNode(id = "3", name = "audio.mp3", path = "/audio.mp3", isDirectory = false, lastModified = 2000L, size = 500L)
    private val fileB = StorageNode(id = "4", name = "video.mp4", path = "/video.mp4", isDirectory = false, lastModified = 4000L, size = 2000L)
    private val fileC = StorageNode(id = "5", name = "document.pdf", path = "/document.pdf", isDirectory = false, lastModified = 5000L, size = 1000L)

    private val allNodes = listOf(fileC, folderBeta, fileA, folderAlpha, fileB)

    @Test
    fun sort_byNameAscending_foldersFirst() {
        val order = SortOrder(field = SortField.NAME, direction = SortDirection.ASCENDING, foldersFirst = true)
        val result = NodeSorter.sort(allNodes, order)

        assertEquals(listOf(folderAlpha, folderBeta, fileA, fileC, fileB), result)
    }

    @Test
    fun sort_byNameDescending_foldersFirst() {
        val order = SortOrder(field = SortField.NAME, direction = SortDirection.DESCENDING, foldersFirst = true)
        val result = NodeSorter.sort(allNodes, order)

        assertEquals(listOf(folderBeta, folderAlpha, fileB, fileC, fileA), result)
    }

    @Test
    fun sort_bySizeAscending_foldersFirst() {
        val order = SortOrder(field = SortField.SIZE, direction = SortDirection.ASCENDING, foldersFirst = true)
        val result = NodeSorter.sort(allNodes, order)

        // Folders (size 0) sorted by name -> folderAlpha, folderBeta; Files -> fileA (500), fileC (1000), fileB (2000)
        assertEquals(listOf(folderAlpha, folderBeta, fileA, fileC, fileB), result)
    }

    @Test
    fun sort_bySizeDescending_foldersFirst() {
        val order = SortOrder(field = SortField.SIZE, direction = SortDirection.DESCENDING, foldersFirst = true)
        val result = NodeSorter.sort(allNodes, order)

        // Folders first; Files by descending size: fileB (2000), fileC (1000), fileA (500)
        assertEquals(listOf(folderBeta, folderAlpha, fileB, fileC, fileA), result)
    }

    @Test
    fun sort_byDateDescending_foldersFirst() {
        val order = SortOrder(field = SortField.DATE, direction = SortDirection.DESCENDING, foldersFirst = true)
        val result = NodeSorter.sort(allNodes, order)

        // Folders: Beta (3000), Alpha (1000); Files: fileC (5000), fileB (4000), fileA (2000)
        assertEquals(listOf(folderBeta, folderAlpha, fileC, fileB, fileA), result)
    }

    @Test
    fun sort_byTypeAscending_foldersFirst() {
        val order = SortOrder(field = SortField.TYPE, direction = SortDirection.ASCENDING, foldersFirst = true)
        val result = NodeSorter.sort(allNodes, order)

        // Extensions: .mp3, .mp4, .pdf -> fileA, fileB, fileC
        assertEquals(listOf(folderAlpha, folderBeta, fileA, fileB, fileC), result)
    }
}
