package com.Hrshd1eux.filemanager.feature.category

import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class CategoryRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repository: CategoryRepository

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("category_test_root")
        repository = CategoryRepository(ioDispatcher = Dispatchers.Unconfined)
    }

    @Test
    fun matchesCategory_images() {
        assertTrue(repository.matchesCategory(File("photo.jpg"), CategoryFilter.IMAGES))
        assertTrue(repository.matchesCategory(File("PHOTO.PNG"), CategoryFilter.IMAGES))
        assertTrue(repository.matchesCategory(File("graphic.webp"), CategoryFilter.IMAGES))
        assertTrue(repository.matchesCategory(File("camera.heic"), CategoryFilter.IMAGES))
        assertFalse(repository.matchesCategory(File("song.mp3"), CategoryFilter.IMAGES))
        assertFalse(repository.matchesCategory(File("doc.pdf"), CategoryFilter.IMAGES))
    }

    @Test
    fun matchesCategory_videos() {
        assertTrue(repository.matchesCategory(File("clip.mp4"), CategoryFilter.VIDEOS))
        assertTrue(repository.matchesCategory(File("movie.mkv"), CategoryFilter.VIDEOS))
        assertTrue(repository.matchesCategory(File("recording.avi"), CategoryFilter.VIDEOS))
        assertFalse(repository.matchesCategory(File("photo.jpg"), CategoryFilter.VIDEOS))
    }

    @Test
    fun matchesCategory_audio() {
        assertTrue(repository.matchesCategory(File("track.mp3"), CategoryFilter.AUDIO))
        assertTrue(repository.matchesCategory(File("song.flac"), CategoryFilter.AUDIO))
        assertTrue(repository.matchesCategory(File("recording.wav"), CategoryFilter.AUDIO))
        assertTrue(repository.matchesCategory(File("podcast.m4a"), CategoryFilter.AUDIO))
        assertFalse(repository.matchesCategory(File("clip.mp4"), CategoryFilter.AUDIO))
    }

    @Test
    fun matchesCategory_documents() {
        assertTrue(repository.matchesCategory(File("resume.pdf"), CategoryFilter.DOCUMENTS))
        assertTrue(repository.matchesCategory(File("notes.docx"), CategoryFilter.DOCUMENTS))
        assertTrue(repository.matchesCategory(File("sheet.xlsx"), CategoryFilter.DOCUMENTS))
        assertFalse(repository.matchesCategory(File("image.png"), CategoryFilter.DOCUMENTS))
    }

    @Test
    fun matchesCategory_archives() {
        assertTrue(repository.matchesCategory(File("backup.zip"), CategoryFilter.ARCHIVES))
        assertTrue(repository.matchesCategory(File("data.7z"), CategoryFilter.ARCHIVES))
        assertTrue(repository.matchesCategory(File("bundle.tar.gz"), CategoryFilter.ARCHIVES))
        assertFalse(repository.matchesCategory(File("track.mp3"), CategoryFilter.ARCHIVES))
    }

    @Test
    fun matchesCategory_apks() {
        assertTrue(repository.matchesCategory(File("application.apk"), CategoryFilter.APKS))
        assertTrue(repository.matchesCategory(File("bundle.xapk"), CategoryFilter.APKS))
        assertTrue(repository.matchesCategory(File("split.apks"), CategoryFilter.APKS))
        assertFalse(repository.matchesCategory(File("text.txt"), CategoryFilter.APKS))
    }

    @Test
    fun matchesCategory_other() {
        assertTrue(repository.matchesCategory(File("firmware.bin"), CategoryFilter.OTHER))
        assertTrue(repository.matchesCategory(File("data.dat"), CategoryFilter.OTHER))
        assertFalse(repository.matchesCategory(File("photo.jpg"), CategoryFilter.OTHER))
        assertFalse(repository.matchesCategory(File("song.mp3"), CategoryFilter.OTHER))
    }

    @Test
    fun getFilesForCategory_scansDirectoryCorrectly() = runTest {
        // Create files in root
        File(rootDir, "photo1.jpg").apply { writeBytes(ByteArray(1024)) }
        File(rootDir, "photo2.png").apply { writeBytes(ByteArray(2048)) }
        File(rootDir, "video1.mp4").apply { writeBytes(ByteArray(4096)) }
        File(rootDir, "audio1.mp3").apply { writeBytes(ByteArray(512)) }
        File(rootDir, "document1.pdf").apply { writeBytes(ByteArray(128)) }

        val imageFiles = repository.getFilesForCategory(CategoryFilter.IMAGES, rootDir.absolutePath)
        assertEquals(2, imageFiles.size)
        val imageNames = imageFiles.map { it.name }.toSet()
        assertTrue(imageNames.contains("photo1.jpg"))
        assertTrue(imageNames.contains("photo2.png"))

        val videoFiles = repository.getFilesForCategory(CategoryFilter.VIDEOS, rootDir.absolutePath)
        assertEquals(1, videoFiles.size)
        assertEquals("video1.mp4", videoFiles.first().name)

        val audioFiles = repository.getFilesForCategory(CategoryFilter.AUDIO, rootDir.absolutePath)
        assertEquals(1, audioFiles.size)
        assertEquals("audio1.mp3", audioFiles.first().name)

        val docFiles = repository.getFilesForCategory(CategoryFilter.DOCUMENTS, rootDir.absolutePath)
        assertEquals(1, docFiles.size)
        assertEquals("document1.pdf", docFiles.first().name)
    }

    @Test(expected = IOException::class)
    fun getFilesForCategory_inaccessibleStorageThrowsException() = runTest {
        repository.getFilesForCategory(CategoryFilter.IMAGES, "/inaccessible/or/removed/storage/path")
    }

    @Test
    fun sortItems_byName() {
        val items = listOf(
            createNode("Charlie.jpg", size = 100, lastModified = 10),
            createNode("Alpha.jpg", size = 300, lastModified = 30),
            createNode("Bravo.jpg", size = 200, lastModified = 20)
        )

        val asc = repository.sortItems(items, SortOrder(field = SortField.NAME, direction = SortDirection.ASCENDING))
        assertEquals("Alpha.jpg", asc[0].name)
        assertEquals("Bravo.jpg", asc[1].name)
        assertEquals("Charlie.jpg", asc[2].name)

        val desc = repository.sortItems(items, SortOrder(field = SortField.NAME, direction = SortDirection.DESCENDING))
        assertEquals("Charlie.jpg", desc[0].name)
        assertEquals("Bravo.jpg", desc[1].name)
        assertEquals("Alpha.jpg", desc[2].name)
    }

    @Test
    fun sortItems_bySize() {
        val items = listOf(
            createNode("fileA.jpg", size = 500, lastModified = 10),
            createNode("fileB.jpg", size = 100, lastModified = 30),
            createNode("fileC.jpg", size = 300, lastModified = 20)
        )

        val asc = repository.sortItems(items, SortOrder(field = SortField.SIZE, direction = SortDirection.ASCENDING))
        assertEquals(100L, asc[0].size)
        assertEquals(300L, asc[1].size)
        assertEquals(500L, asc[2].size)

        val desc = repository.sortItems(items, SortOrder(field = SortField.SIZE, direction = SortDirection.DESCENDING))
        assertEquals(500L, desc[0].size)
        assertEquals(300L, desc[1].size)
        assertEquals(100L, desc[2].size)
    }

    @Test
    fun sortItems_byDate() {
        val items = listOf(
            createNode("old.jpg", size = 100, lastModified = 1000),
            createNode("newest.jpg", size = 100, lastModified = 3000),
            createNode("mid.jpg", size = 100, lastModified = 2000)
        )

        val asc = repository.sortItems(items, SortOrder(field = SortField.DATE, direction = SortDirection.ASCENDING))
        assertEquals("old.jpg", asc[0].name)
        assertEquals("mid.jpg", asc[1].name)
        assertEquals("newest.jpg", asc[2].name)

        val desc = repository.sortItems(items, SortOrder(field = SortField.DATE, direction = SortDirection.DESCENDING))
        assertEquals("newest.jpg", desc[0].name)
        assertEquals("mid.jpg", desc[1].name)
        assertEquals("old.jpg", desc[2].name)
    }

    @Test
    fun sortItems_byType() {
        val items = listOf(
            createNode("file.png", size = 100, lastModified = 10),
            createNode("file.bmp", size = 100, lastModified = 20),
            createNode("file.jpg", size = 100, lastModified = 30)
        )

        val asc = repository.sortItems(items, SortOrder(field = SortField.TYPE, direction = SortDirection.ASCENDING))
        assertEquals("file.bmp", asc[0].name)
        assertEquals("file.jpg", asc[1].name)
        assertEquals("file.png", asc[2].name)
    }

    @Test
    fun fileOperations_deleteAndRename() = runTest {
        val testFile = File(rootDir, "test_file.txt").apply { writeText("Hello") }
        val node = createNode(testFile.name, path = testFile.absolutePath)

        // Rename
        val renameResult = repository.renameFile(node, "renamed_file.txt")
        assertTrue(renameResult.isSuccess)
        assertFalse(File(rootDir, "test_file.txt").exists())
        assertTrue(File(rootDir, "renamed_file.txt").exists())

        // Delete
        val updatedNode = renameResult.getOrThrow()
        val deleteResult = repository.deleteFile(updatedNode)
        assertTrue(deleteResult.isSuccess)
        assertFalse(File(rootDir, "renamed_file.txt").exists())
    }

    @Test
    fun fileOperations_bulkDelete() = runTest {
        val f1 = File(rootDir, "bulk_a.txt").apply { writeText("A") }
        val f2 = File(rootDir, "bulk_b.txt").apply { writeText("B") }

        val nodes = listOf(
            createNode(f1.name, path = f1.absolutePath),
            createNode(f2.name, path = f2.absolutePath)
        )

        val bulkResult = repository.deleteFiles(nodes)
        assertTrue(bulkResult.isSuccess)
        assertEquals(2, bulkResult.getOrThrow())
        assertFalse(f1.exists())
        assertFalse(f2.exists())
    }

    private fun createNode(
        name: String,
        size: Long = 1024L,
        lastModified: Long = 1000L,
        path: String = "/path/$name"
    ): StorageNode {
        return StorageNode(
            id = path,
            name = name,
            path = path,
            size = size,
            lastModified = lastModified,
            isDirectory = false
        )
    }
}
