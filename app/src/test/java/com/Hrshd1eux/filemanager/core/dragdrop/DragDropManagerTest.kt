package com.Hrshd1eux.filemanager.core.dragdrop

import android.content.ClipData
import android.net.Uri
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
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

class DragDropManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var subDir: File
    private lateinit var childOfSubDir: File
    private lateinit var testFile: File

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("drag_drop_root")
        subDir = File(rootDir, "SubFolder").apply { mkdir() }
        childOfSubDir = File(subDir, "Nested").apply { mkdir() }
        testFile = File(rootDir, "doc.pdf").apply { writeText("data") }
    }

    @Test
    fun canDrop_allowsValidFileMoveIntoSubdirectory() {
        val payload = DragDropPayload.StorageNodes(listOf(testFile.absolutePath))
        val targetNode = StorageNode(
            id = subDir.absolutePath,
            name = subDir.name,
            path = subDir.absolutePath,
            isDirectory = true
        )

        assertTrue(
            "Dropping file into subfolder must be allowed",
            DragDropManager.canDrop(payload, targetNode)
        )
    }

    @Test
    fun canDrop_rejectsDroppingIntoNonDirectory() {
        val payload = DragDropPayload.StorageNodes(listOf(testFile.absolutePath))
        val nonDirNode = StorageNode(
            id = testFile.absolutePath,
            name = testFile.name,
            path = testFile.absolutePath,
            isDirectory = false
        )

        assertFalse(
            "Dropping into a non-directory file must be rejected",
            DragDropManager.canDrop(payload, nonDirNode)
        )
    }

    @Test
    fun canDrop_rejectsDroppingFolderIntoItself() {
        val payload = DragDropPayload.StorageNodes(listOf(subDir.absolutePath))
        val targetNode = StorageNode(
            id = subDir.absolutePath,
            name = subDir.name,
            path = subDir.absolutePath,
            isDirectory = true
        )

        assertFalse(
            "Dropping a folder into itself must be rejected",
            DragDropManager.canDrop(payload, targetNode)
        )
    }

    @Test
    fun canDrop_rejectsDroppingParentFolderIntoItsChild() {
        val payload = DragDropPayload.StorageNodes(listOf(subDir.absolutePath))
        val targetChildNode = StorageNode(
            id = childOfSubDir.absolutePath,
            name = childOfSubDir.name,
            path = childOfSubDir.absolutePath,
            isDirectory = true
        )

        assertFalse(
            "Dropping a parent directory into its own child directory must be rejected",
            DragDropManager.canDrop(payload, targetChildNode)
        )
    }

    @Test
    fun canDrop_rejectsDroppingItemIntoSameDirectory() {
        val payload = DragDropPayload.StorageNodes(listOf(testFile.absolutePath))
        val currentParentNode = StorageNode(
            id = rootDir.absolutePath,
            name = rootDir.name,
            path = rootDir.absolutePath,
            isDirectory = true
        )

        assertFalse(
            "Dropping an item into the directory it already resides in must be rejected",
            DragDropManager.canDrop(payload, currentParentNode)
        )
    }

    @Test
    fun canDrop_allowsArchiveEntryIntoFolder() {
        val payload = DragDropPayload.ArchiveEntry(
            archivePath = "/storage/emulated/0/backup.zip",
            entryPath = "photos/summer.jpg"
        )
        val targetNode = StorageNode(
            id = subDir.absolutePath,
            name = subDir.name,
            path = subDir.absolutePath,
            isDirectory = true
        )

        assertTrue(
            "Extracting archive entry into valid folder must be allowed",
            DragDropManager.canDrop(payload, targetNode)
        )
    }
}
