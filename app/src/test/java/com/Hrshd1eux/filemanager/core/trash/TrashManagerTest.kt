package com.Hrshd1eux.filemanager.core.trash

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.trash.manager.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var trashDir: File

    @org.junit.Before
    fun setUp() {
        trashDir = tempFolder.newFolder("trash_test_${System.nanoTime()}")
    }

    private fun createNode(file: File, isDir: Boolean = false): StorageNode {
        return StorageNode(
            id = file.absolutePath,
            name = file.name,
            path = file.absolutePath,
            size = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            isDirectory = isDir
        )
    }

    @Test
    fun moveToTrash_movesFileAndUpdatesEntries() = runBlocking {
        val testFile = tempFolder.newFile("sample.txt").apply { writeText("Sample Content") }
        val node = createNode(testFile)

        val trashManager = TrashManager(context = null, ioDispatcher = Dispatchers.IO, baseTrashDir = trashDir)
        val result = trashManager.moveToTrash(node)

        assertTrue(result.isSuccess)
        val entry = result.getOrNull()
        assertNotNull(entry)
        assertEquals("sample.txt", entry!!.originalName)
        assertEquals(testFile.absolutePath, entry.originalPath)

        assertFalse("Original file should no longer exist at original path", testFile.exists())
        assertTrue("File should now exist inside trash directory", File(entry.trashedPath).exists())
        assertEquals(1, trashManager.entries.value.size)
    }

    @Test
    fun restore_movesFileBackToOriginalLocation() = runBlocking {
        val original = tempFolder.newFile("document.pdf").apply { writeText("Important Document") }
        val node = createNode(original)

        val trashManager = TrashManager(context = null, ioDispatcher = Dispatchers.IO, baseTrashDir = trashDir)
        val moveResult = trashManager.moveToTrash(node)
        val entry = moveResult.getOrThrow()

        assertFalse(original.exists())

        val restoreResult = trashManager.restore(entry)
        assertTrue(restoreResult.isSuccess)
        assertTrue("File must be restored to original path", original.exists())
        assertEquals("Important Document", original.readText())
        assertEquals(0, trashManager.entries.value.size)
    }

    @Test
    fun restore_handlesNameCollisionGracefully() = runBlocking {
        val original = tempFolder.newFile("notes.txt").apply { writeText("Original Notes") }
        val node = createNode(original)

        val trashManager = TrashManager(context = null, ioDispatcher = Dispatchers.IO, baseTrashDir = trashDir)
        val entry = trashManager.moveToTrash(node).getOrThrow()

        // Create a new conflicting file with the same name before restoring
        original.writeText("New Notes that collide")

        val restoreResult = trashManager.restore(entry)
        assertTrue(restoreResult.isSuccess)

        assertTrue(original.exists())
        assertEquals("New Notes that collide", original.readText())

        val restoredFile = File(original.parentFile, "notes (Restored 1).txt")
        assertTrue("Colliding restored file must have (Restored 1) suffix", restoredFile.exists())
        assertEquals("Original Notes", restoredFile.readText())
    }

    @Test
    fun shred_cryptographicallyRemovesTrashedFile() = runBlocking {
        val file = tempFolder.newFile("delete_me.txt").apply { writeText("To be shredded") }
        val node = createNode(file)

        val trashManager = TrashManager(context = null, ioDispatcher = Dispatchers.IO, baseTrashDir = trashDir)
        val entry = trashManager.moveToTrash(node).getOrThrow()

        val shredResult = trashManager.shred(entry)
        assertTrue(shredResult.isSuccess)
        assertFalse(File(entry.trashedPath).exists())
        assertEquals(0, trashManager.entries.value.size)
    }

    @Test
    fun emptyTrash_shredsAllEntries() = runBlocking {
        val f1 = tempFolder.newFile("f1.txt").apply { writeText("1") }
        val f2 = tempFolder.newFile("f2.txt").apply { writeText("2") }

        val trashManager = TrashManager(context = null, ioDispatcher = Dispatchers.IO, baseTrashDir = trashDir)
        trashManager.moveToTrash(listOf(createNode(f1), createNode(f2)))

        assertEquals(2, trashManager.entries.value.size)

        val emptyResult = trashManager.emptyTrash()
        assertTrue(emptyResult.isSuccess)
        assertEquals(2, emptyResult.getOrNull())
        assertEquals(0, trashManager.entries.value.size)
    }
}
