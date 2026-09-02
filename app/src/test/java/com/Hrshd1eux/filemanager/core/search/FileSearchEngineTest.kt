package com.Hrshd1eux.filemanager.core.search

import com.Hrshd1eux.filemanager.core.search.engine.FileSearchEngine
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.DateFilter
import com.Hrshd1eux.filemanager.core.search.model.SearchCriteria
import com.Hrshd1eux.filemanager.core.search.model.SizeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.last
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
class FileSearchEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var rootDir: File
    private lateinit var searchEngine: FileSearchEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("search_root")
        searchEngine = FileSearchEngine(ioDispatcher = testDispatcher)

        // Seed test directory structure
        File(rootDir, "Vacation_Photo.jpg").apply { writeBytes(ByteArray(1024)) } // 1 KB
        File(rootDir, "vacation_video.mp4").apply { writeBytes(ByteArray(5 * 1024 * 1024)) } // 5 MB
        File(rootDir, "work_notes.pdf").apply { writeBytes(ByteArray(2048)) } // 2 KB
        File(rootDir, "invoice.PDF").apply { writeBytes(ByteArray(4096)) } // 4 KB
        File(rootDir, "archive.zip").apply { writeBytes(ByteArray(1024)) } // 1 KB

        val subFolder = File(rootDir, "VacationFolder").apply { mkdir() }
        File(subFolder, "itinerary.docx").apply { writeBytes(ByteArray(1024)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun search_substringMatching_findsMatchingFilesAndFolders() = runTest {
        val criteria = SearchCriteria(
            query = "vacation",
            rootPath = rootDir.absolutePath
        )

        val results = searchEngine.search(criteria).last()

        assertEquals(3, results.size)
        val names = results.map { it.name }.toSet()
        assertTrue(names.contains("Vacation_Photo.jpg"))
        assertTrue(names.contains("vacation_video.mp4"))
        assertTrue(names.contains("VacationFolder"))
    }

    @Test
    fun search_extensionMatching_findsAllMatchingExtensions() = runTest {
        val criteria = SearchCriteria(
            query = "pdf",
            rootPath = rootDir.absolutePath
        )

        val results = searchEngine.search(criteria).last()

        assertEquals(2, results.size)
        val names = results.map { it.name }.toSet()
        assertTrue(names.contains("work_notes.pdf"))
        assertTrue(names.contains("invoice.PDF"))
    }

    @Test
    fun search_categoryFilter_imagesOnly() = runTest {
        val criteria = SearchCriteria(
            query = "",
            rootPath = rootDir.absolutePath,
            category = CategoryFilter.IMAGES
        )

        val results = searchEngine.search(criteria).last()

        assertEquals(1, results.size)
        assertEquals("Vacation_Photo.jpg", results.first().name)
    }

    @Test
    fun search_categoryFilter_documentsOnly() = runTest {
        val criteria = SearchCriteria(
            query = "",
            rootPath = rootDir.absolutePath,
            category = CategoryFilter.DOCUMENTS
        )

        val results = searchEngine.search(criteria).last()

        assertEquals(3, results.size)
        val names = results.map { it.name }.toSet()
        assertTrue(names.contains("work_notes.pdf"))
        assertTrue(names.contains("invoice.PDF"))
        assertTrue(names.contains("itinerary.docx"))
    }

    @Test
    fun search_sizeFilter_between1MBAnd100MB() = runTest {
        val criteria = SearchCriteria(
            query = "",
            rootPath = rootDir.absolutePath,
            sizeFilter = SizeFilter.BETWEEN_1MB_AND_100MB
        )

        val results = searchEngine.search(criteria).last()

        assertEquals(1, results.size)
        assertEquals("vacation_video.mp4", results.first().name)
    }

    @Test
    fun search_dateFilter_today() = runTest {
        val criteria = SearchCriteria(
            query = "Vacation_Photo",
            rootPath = rootDir.absolutePath,
            dateFilter = DateFilter.TODAY
        )

        val results = searchEngine.search(criteria).last()

        assertEquals(1, results.size)
        assertEquals("Vacation_Photo.jpg", results.first().name)
    }
}
