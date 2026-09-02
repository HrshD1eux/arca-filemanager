package com.Hrshd1eux.filemanager.core.storage

import com.Hrshd1eux.filemanager.core.storage.analyzer.LocalStorageAnalyzer
import com.Hrshd1eux.filemanager.core.storage.model.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LocalStorageAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var analyzer: LocalStorageAnalyzer
    private lateinit var rootDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        analyzer = LocalStorageAnalyzer(ioDispatcher = testDispatcher)
        rootDir = tempFolder.newFolder("storage_analyzer_root")

        // Create mock files across categories
        File(rootDir, "photo1.jpg").apply { writeBytes(ByteArray(1024)) }
        File(rootDir, "photo2.png").apply { writeBytes(ByteArray(2048)) }
        File(rootDir, "movie.mp4").apply { writeBytes(ByteArray(4096)) }
        File(rootDir, "song.mp3").apply { writeBytes(ByteArray(1024)) }
        File(rootDir, "paper.pdf").apply { writeBytes(ByteArray(512)) }
        File(rootDir, "notes.txt").apply { writeBytes(ByteArray(256)) }
        File(rootDir, "archive.zip").apply { writeBytes(ByteArray(1024)) }
        File(rootDir, "binary.dat").apply { writeBytes(ByteArray(2048)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun classifyFile_correctlyClassifiesExtensions() {
        assertEquals(StorageCategory.IMAGES, analyzer.classifyFile(File("test.jpg")))
        assertEquals(StorageCategory.IMAGES, analyzer.classifyFile(File("photo.PNG")))
        assertEquals(StorageCategory.VIDEOS, analyzer.classifyFile(File("clip.mp4")))
        assertEquals(StorageCategory.VIDEOS, analyzer.classifyFile(File("film.mkv")))
        assertEquals(StorageCategory.AUDIO, analyzer.classifyFile(File("track.mp3")))
        assertEquals(StorageCategory.AUDIO, analyzer.classifyFile(File("audio.flac")))
        assertEquals(StorageCategory.DOCUMENTS, analyzer.classifyFile(File("book.pdf")))
        assertEquals(StorageCategory.DOCUMENTS, analyzer.classifyFile(File("doc.docx")))
        assertEquals(StorageCategory.ARCHIVES, analyzer.classifyFile(File("backup.zip")))
        assertEquals(StorageCategory.ARCHIVES, analyzer.classifyFile(File("package.apk")))
        assertEquals(StorageCategory.OTHER, analyzer.classifyFile(File("unknown.dat")))
    }

    @Test
    fun analyzeStorage_aggregatesCategoryBytesAccurately() = runTest {
        val breakdown = analyzer.analyzeStorage(rootDir.absolutePath)

        val imageCat = breakdown.categories.first { it.category == StorageCategory.IMAGES }
        assertEquals(3072L, imageCat.byteCount) // 1024 + 2048
        assertEquals(2, imageCat.fileCount)

        val videoCat = breakdown.categories.first { it.category == StorageCategory.VIDEOS }
        assertEquals(4096L, videoCat.byteCount)
        assertEquals(1, videoCat.fileCount)

        val audioCat = breakdown.categories.first { it.category == StorageCategory.AUDIO }
        assertEquals(1024L, audioCat.byteCount)
        assertEquals(1, audioCat.fileCount)

        val docCat = breakdown.categories.first { it.category == StorageCategory.DOCUMENTS }
        assertEquals(768L, docCat.byteCount) // 512 + 256
        assertEquals(2, docCat.fileCount)

        val archiveCat = breakdown.categories.first { it.category == StorageCategory.ARCHIVES }
        assertEquals(1024L, archiveCat.byteCount)
        assertEquals(1, archiveCat.fileCount)
    }

    @Test
    fun analyzeStorage_handlesEmptyDirectory() = runTest {
        val emptyDir = tempFolder.newFolder("empty_dir")
        val breakdown = analyzer.analyzeStorage(emptyDir.absolutePath)

        assertTrue(breakdown.categories.all { it.byteCount == 0L || it.category == StorageCategory.OTHER })
    }
}
