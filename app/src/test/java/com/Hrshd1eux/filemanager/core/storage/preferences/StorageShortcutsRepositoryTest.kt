package com.Hrshd1eux.filemanager.core.storage.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StorageShortcutsRepositoryTest {

    private lateinit var repository: StorageShortcutsRepository

    @Before
    fun setUp() {
        repository = StorageShortcutsRepository(null)
    }

    @Test
    fun defaultShortcuts_areInitializedProperly() {
        val shortcuts = repository.shortcuts.value
        assertEquals(4, shortcuts.size)
        val names = shortcuts.map { it.name }
        assertTrue(names.contains("Download"))
        assertTrue(names.contains("Documents"))
        assertTrue(names.contains("Pictures"))
        assertTrue(names.contains("Movies"))
        assertTrue(shortcuts.all { it.isDefault })
    }

    @Test
    fun addShortcut_addsCustomPath() {
        repository.addShortcut("Camera", "/storage/emulated/0/DCIM/Camera")
        val shortcuts = repository.shortcuts.value
        assertEquals(5, shortcuts.size)
        val custom = shortcuts.find { it.name == "Camera" }
        assertTrue(custom != null)
        assertEquals("/storage/emulated/0/DCIM/Camera", custom?.path)
        assertFalse(custom?.isDefault ?: true)
    }

    @Test
    fun updateShortcut_modifiesNameAndPath() {
        val firstId = repository.shortcuts.value.first().id
        repository.updateShortcut(firstId, "My Downloads", "/storage/emulated/0/CustomDownloads")
        val updated = repository.shortcuts.value.first { it.id == firstId }
        assertEquals("My Downloads", updated.name)
        assertEquals("/storage/emulated/0/CustomDownloads", updated.path)
    }

    @Test
    fun removeShortcut_removesItem() {
        val firstId = repository.shortcuts.value.first().id
        repository.removeShortcut(firstId)
        val remaining = repository.shortcuts.value
        assertEquals(3, remaining.size)
        assertFalse(remaining.any { it.id == firstId })
    }

    @Test
    fun resetToDefaults_restoresInitialFourFolders() {
        val firstId = repository.shortcuts.value.first().id
        repository.removeShortcut(firstId)
        repository.addShortcut("Test", "/path/to/test")
        assertEquals(4, repository.shortcuts.value.size)

        repository.resetToDefaults()
        val resetList = repository.shortcuts.value
        assertEquals(4, resetList.size)
        val names = resetList.map { it.name }
        assertTrue(names.contains("Download"))
        assertTrue(names.contains("Documents"))
        assertTrue(names.contains("Pictures"))
        assertTrue(names.contains("Movies"))
    }
}
