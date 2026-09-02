package com.Hrshd1eux.filemanager.core.recents

import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentsRepositoryTest {

    private lateinit var repository: RecentsRepository

    @Before
    fun setUp() {
        repository = RecentsRepository(context = null)
    }

    @Test
    fun recordAccess_addsNewItemAtHead() {
        val node = StorageNode(
            id = "/storage/emulated/0/Download",
            name = "Download",
            path = "/storage/emulated/0/Download",
            isDirectory = true,
            size = 0L,
            lastModified = 1000L
        )

        repository.recordAccess(node)

        assertEquals(1, repository.recents.value.size)
        assertEquals("Download", repository.recents.value.first().displayName)
        assertEquals(node.path, repository.recents.value.first().path)
    }

    @Test
    fun recordAccess_existingItemMovedToHead() {
        val nodeA = StorageNode(id = "1", name = "FileA.txt", path = "/a.txt", isDirectory = false, size = 10L, lastModified = 1L)
        val nodeB = StorageNode(id = "2", name = "FileB.txt", path = "/b.txt", isDirectory = false, size = 20L, lastModified = 2L)

        repository.recordAccess(nodeA)
        repository.recordAccess(nodeB)

        assertEquals(2, repository.recents.value.size)
        assertEquals("FileB.txt", repository.recents.value[0].displayName)
        assertEquals("FileA.txt", repository.recents.value[1].displayName)

        // Re-access nodeA
        repository.recordAccess(nodeA)

        assertEquals(2, repository.recents.value.size)
        assertEquals("FileA.txt", repository.recents.value[0].displayName)
        assertEquals("FileB.txt", repository.recents.value[1].displayName)
    }

    @Test
    fun recordAccess_capsAtMaxRecents() {
        for (i in 1..60) {
            val node = StorageNode(
                id = "$i",
                name = "File$i.txt",
                path = "/file$i.txt",
                isDirectory = false,
                size = 100L,
                lastModified = 1L
            )
            repository.recordAccess(node)
        }

        assertEquals(RecentsRepository.MAX_RECENTS, repository.recents.value.size)
        assertEquals("File60.txt", repository.recents.value.first().displayName)
    }

    @Test
    fun removeRecent_removesSpecificItem() {
        val nodeA = StorageNode(id = "1", name = "A", path = "/a", isDirectory = true, size = 0L, lastModified = 1L)
        val nodeB = StorageNode(id = "2", name = "B", path = "/b", isDirectory = false, size = 100L, lastModified = 2L)

        repository.recordAccess(nodeA)
        repository.recordAccess(nodeB)
        assertEquals(2, repository.recents.value.size)

        repository.removeRecent("/a")

        assertEquals(1, repository.recents.value.size)
        assertEquals("/b", repository.recents.value.first().path)
    }

    @Test
    fun clearRecents_wipesAllHistory() {
        val node = StorageNode(id = "1", name = "Doc.pdf", path = "/doc.pdf", isDirectory = false, size = 500L, lastModified = 1L)
        repository.recordAccess(node)
        assertEquals(1, repository.recents.value.size)

        repository.clearRecents()

        assertTrue(repository.recents.value.isEmpty())
    }
}
