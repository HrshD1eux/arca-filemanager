package com.Hrshd1eux.filemanager.core.favorites

import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavoritesRepositoryTest {

    private lateinit var repository: FavoritesRepository

    @Before
    fun setUp() {
        repository = FavoritesRepository(context = null)
    }

    @Test
    fun addFavorite_itemAppearsInFavorites() {
        val downloadNode = StorageNode(
            id = "/storage/emulated/0/Download",
            name = "Download",
            path = "/storage/emulated/0/Download",
            isDirectory = true,
            size = 0L,
            lastModified = 1000L
        )

        repository.addFavorite(downloadNode)

        assertTrue(repository.isFavorite(downloadNode.path))
        assertEquals(1, repository.favorites.value.size)
        assertEquals("Download", repository.favorites.value.first().displayName)
        assertTrue(repository.favorites.value.first().isDirectory)
    }

    @Test
    fun toggleFavorite_addsAndRemovesCorrectly() {
        val projectNode = StorageNode(
            id = "/storage/emulated/0/Projects",
            name = "Projects",
            path = "/storage/emulated/0/Projects",
            isDirectory = true,
            size = 0L,
            lastModified = 2000L
        )

        val added = repository.toggleFavorite(projectNode)
        assertTrue(added)
        assertTrue(repository.isFavorite(projectNode.path))
        assertEquals(1, repository.favorites.value.size)

        val removed = repository.toggleFavorite(projectNode)
        assertFalse(removed)
        assertFalse(repository.isFavorite(projectNode.path))
        assertEquals(0, repository.favorites.value.size)
    }

    @Test
    fun removeFavorite_removesSpecificItem() {
        val node1 = StorageNode(id = "1", name = "A", path = "/a", isDirectory = true, size = 0L, lastModified = 1L)
        val node2 = StorageNode(id = "2", name = "B", path = "/b", isDirectory = false, size = 100L, lastModified = 2L)

        repository.addFavorite(node1)
        repository.addFavorite(node2)
        assertEquals(2, repository.favorites.value.size)

        repository.removeFavorite("/a")
        assertEquals(1, repository.favorites.value.size)
        assertFalse(repository.isFavorite("/a"))
        assertTrue(repository.isFavorite("/b"))
    }

    @Test
    fun addFavorite_preventsDuplicates() {
        val node = StorageNode(id = "1", name = "Backup", path = "/sdcard/Backup", isDirectory = true, size = 0L, lastModified = 1L)

        repository.addFavorite(node)
        repository.addFavorite(node)

        assertEquals(1, repository.favorites.value.size)
    }
}
