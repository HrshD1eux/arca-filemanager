package com.Hrshd1eux.filemanager.feature.browser

import com.Hrshd1eux.filemanager.core.favorites.model.FavoriteItem
import com.Hrshd1eux.filemanager.core.favorites.repository.FavoritesRepository
import com.Hrshd1eux.filemanager.core.recents.model.RecentItem
import com.Hrshd1eux.filemanager.core.recents.repository.RecentsRepository
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.DestinationCategory
import com.Hrshd1eux.filemanager.core.storage.util.SmartDestinationResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SmartDestinationPickerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var recentsRepo: RecentsRepository

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("smart_picker_root")
        favoritesRepo = FavoritesRepository()
        recentsRepo = RecentsRepository()
    }

    @Test
    fun recentDestinations_containsStandardUserFolders() {
        val recentDestinations = SmartDestinationResolver.getRecentDestinations(
            context = null,
            recentsRepository = recentsRepo
        )

        val names = recentDestinations.map { it.name }
        assertTrue("Recent must contain Download", names.contains("Download"))
        assertTrue("Recent must contain Documents", names.contains("Documents"))
        assertTrue("Recent must contain Pictures", names.contains("Pictures"))

        recentDestinations.forEach { item ->
            assertEquals(DestinationCategory.RECENT, item.category)
            assertNotNull(item.path)
        }
    }

    @Test
    fun recentDestinations_mergesDirectoryHistoryExcludingFiles() {
        val folder1 = File(rootDir, "WorkReports").apply { mkdir() }
        val file1 = File(rootDir, "todo.txt").apply { writeText("task") }

        recentsRepo.recordAccess(
            StorageNode(
                id = folder1.absolutePath,
                name = "WorkReports",
                path = folder1.absolutePath,
                isDirectory = true
            )
        )
        recentsRepo.recordAccess(
            StorageNode(
                id = file1.absolutePath,
                name = "todo.txt",
                path = file1.absolutePath,
                isDirectory = false
            )
        )

        val recentDestinations = SmartDestinationResolver.getRecentDestinations(
            context = null,
            recentsRepository = recentsRepo
        )

        val paths = recentDestinations.map { it.path }
        assertTrue(paths.contains(folder1.absolutePath))
        assertFalse("Recent destination picker must NOT contain regular files", paths.contains(file1.absolutePath))
    }

    @Test
    fun favoriteDestinations_resolvesDirectoryFavoritesWithStar() {
        val projectFolder = File(rootDir, "Projects").apply { mkdir() }
        val backupFolder = File(rootDir, "Backup").apply { mkdir() }
        val secretDoc = File(rootDir, "secret.pdf").apply { writeText("secret") }

        favoritesRepo.addFavorite(
            StorageNode(
                id = projectFolder.absolutePath,
                name = "Projects",
                path = projectFolder.absolutePath,
                isDirectory = true
            )
        )
        favoritesRepo.addFavorite(
            StorageNode(
                id = backupFolder.absolutePath,
                name = "Backup",
                path = backupFolder.absolutePath,
                isDirectory = true
            )
        )
        favoritesRepo.addFavorite(
            StorageNode(
                id = secretDoc.absolutePath,
                name = "secret.pdf",
                path = secretDoc.absolutePath,
                isDirectory = false
            )
        )

        val favorites = SmartDestinationResolver.getFavoriteDestinations(favoritesRepo)

        assertEquals(2, favorites.size)
        val names = favorites.map { it.name }
        assertTrue(names.contains("★ Projects"))
        assertTrue(names.contains("★ Backup"))

        favorites.forEach { fav ->
            assertEquals(DestinationCategory.FAVORITE, fav.category)
            val node = fav.toStorageNode()
            assertTrue(node.isDirectory)
            assertFalse(node.name.startsWith("★"))
        }
    }

    @Test
    fun storageDestinations_resolvesInternalAndExternalDrives() {
        val storageDestinations = SmartDestinationResolver.getStorageDestinations(context = null)

        val internal = storageDestinations.firstOrNull { it.name == "Internal" }
        assertNotNull("Internal storage must be present", internal)
        assertEquals(DestinationCategory.STORAGE, internal?.category)
        assertTrue(internal?.isAvailable == true)

        val sdCard = storageDestinations.firstOrNull { it.name == "SD Card" }
        assertNotNull("SD Card target must be present", sdCard)
        assertEquals(DestinationCategory.STORAGE, sdCard?.category)

        val usb = storageDestinations.firstOrNull { it.name == "USB" }
        assertNotNull("USB target must be present", usb)
        assertEquals(DestinationCategory.STORAGE, usb?.category)
    }

    @Test
    fun excludedPaths_filtersOutSourceFolderFromDestinationShortcuts() {
        val movingDir = File(rootDir, "Documents").apply { mkdir() }

        val recentDestinations = SmartDestinationResolver.getRecentDestinations(
            context = null,
            recentsRepository = recentsRepo
        )

        val excluded = setOf(movingDir.absolutePath)
        val filtered = recentDestinations.filter { !excluded.contains(it.path) }

        assertFalse(filtered.any { it.path == movingDir.absolutePath })
    }
}
