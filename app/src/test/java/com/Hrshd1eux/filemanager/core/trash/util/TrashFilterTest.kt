package com.Hrshd1eux.filemanager.core.trash.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashFilterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun isTrashed_detectsAndroidMediaStoreTrashFiles() {
        val root = tempFolder.newFolder("camera")
        val file1 = File(root, ".trashed-1712345678-IMG_2024.jpg")
        val file2 = File(root, ".trashed_document.pdf")
        val file3 = File(root, ".trashed")

        assertTrue(TrashFilter.isTrashed(file1))
        assertTrue(TrashFilter.isTrashed(file2))
        assertTrue(TrashFilter.isTrashed(file3))
    }

    @Test
    fun isTrashed_detectsArcaTrashFilesAndExtensions() {
        val root = tempFolder.newFolder("downloads")
        val file1 = File(root, "c983a7df-5e4d-4b89-a541-e7377c8ef40a.trash")
        val file2 = File(root, "archive.trashed")

        assertTrue(TrashFilter.isTrashed(file1))
        assertTrue(TrashFilter.isTrashed(file2))
    }

    @Test
    fun isTrashed_detectsFilesInKnownTrashDirectories() {
        val root = tempFolder.newFolder("storage")
        val arcaTrash = File(root, ".arca_trash").apply { mkdir() }
        val nestedInArca = File(arcaTrash, "sample.txt")

        val standardTrash = File(root, ".trash").apply { mkdir() }
        val nestedInStandard = File(standardTrash, "doc.pdf")

        val capitalizedTrash = File(root, "Trash").apply { mkdir() }
        val nestedInCapital = File(capitalizedTrash, "video.mp4")

        val recycleTrash = File(root, ".recycle").apply { mkdir() }
        val nestedInRecycle = File(recycleTrash, "data.bin")

        assertTrue(TrashFilter.isTrashed(nestedInArca))
        assertTrue(TrashFilter.isTrashed(nestedInStandard))
        assertTrue(TrashFilter.isTrashed(nestedInCapital))
        assertTrue(TrashFilter.isTrashed(nestedInRecycle))
    }

    @Test
    fun isTrashed_returnsFalseForNormalFiles() {
        val root = tempFolder.newFolder("documents")
        val file1 = File(root, "photo.jpg")
        val file2 = File(root, "presentation.pptx")
        val file3 = File(root, "archive.zip")

        assertFalse(TrashFilter.isTrashed(file1))
        assertFalse(TrashFilter.isTrashed(file2))
        assertFalse(TrashFilter.isTrashed(file3))
    }

    @Test
    fun isTrashDirectory_detectsTrashDirectoriesCorrectly() {
        val root = tempFolder.newFolder("fs_root")
        val arcaTrashDir = File(root, ".arca_trash")
        val dotTrashDir = File(root, ".trash")
        val trashDir = File(root, "Trash")
        val normalDir = File(root, "Pictures")

        assertTrue(TrashFilter.isTrashDirectory(arcaTrashDir))
        assertTrue(TrashFilter.isTrashDirectory(dotTrashDir))
        assertTrue(TrashFilter.isTrashDirectory(trashDir))
        assertFalse(TrashFilter.isTrashDirectory(normalDir))
    }
}
