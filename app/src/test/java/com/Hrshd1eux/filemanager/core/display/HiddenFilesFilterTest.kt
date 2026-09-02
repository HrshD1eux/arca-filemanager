package com.Hrshd1eux.filemanager.core.display

import com.Hrshd1eux.filemanager.core.display.preferences.DisplayPreferencesRepository
import com.Hrshd1eux.filemanager.core.search.engine.FileSearchEngine
import com.Hrshd1eux.filemanager.core.search.model.SearchCriteria
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenFilesFilterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var rootDir: File
    private lateinit var searchEngine: FileSearchEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("hidden_test_root")
        searchEngine = FileSearchEngine(ioDispatcher = testDispatcher)

        // Create visible and hidden test files/folders
        File(rootDir, ".hidden_file.txt").apply { writeText("secret") }
        File(rootDir, ".nomedia").apply { writeText("") }
        File(rootDir, ".config").apply { mkdir() }
        File(rootDir, "regular_file.txt").apply { writeText("hello") }
        File(rootDir, "VisibleFolder").apply { mkdir() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun displayPreferencesRepository_defaultShowHiddenFilesIsFalse() {
        val repo = DisplayPreferencesRepository(context = null)
        assertFalse(repo.showHiddenFiles.value)
    }

    @Test
    fun displayPreferencesRepository_updateShowHiddenFilesTogglesFlow() {
        val repo = DisplayPreferencesRepository(context = null)
        assertFalse(repo.showHiddenFiles.value)

        repo.updateShowHiddenFiles(true)
        assertTrue(repo.showHiddenFiles.value)

        repo.updateShowHiddenFiles(false)
        assertFalse(repo.showHiddenFiles.value)
    }

    @Test
    fun nodeFiltering_filtersOutDotfilesWhenDisabled() {
        val nodes = listOf(
            StorageNode(id = "1", name = ".nomedia", path = "/.nomedia", isDirectory = false, size = 0L, lastModified = 1L),
            StorageNode(id = "2", name = ".config", path = "/.config", isDirectory = true, size = 0L, lastModified = 1L),
            StorageNode(id = "3", name = ".hidden", path = "/.hidden", isDirectory = false, size = 0L, lastModified = 1L),
            StorageNode(id = "4", name = "Documents", path = "/Documents", isDirectory = true, size = 0L, lastModified = 1L),
            StorageNode(id = "5", name = "photo.jpg", path = "/photo.jpg", isDirectory = false, size = 100L, lastModified = 1L)
        )

        val filteredHiddenOff = nodes.filter { !it.name.startsWith(".") }
        assertEquals(2, filteredHiddenOff.size)
        assertEquals(listOf("Documents", "photo.jpg"), filteredHiddenOff.map { it.name })

        val filteredHiddenOn = nodes
        assertEquals(5, filteredHiddenOn.size)
    }

    @Test
    fun searchEngine_respectsIncludeHiddenFlag() = runTest {
        // Search with includeHidden = false (default)
        val criteriaHiddenOff = SearchCriteria(
            query = "",
            rootPath = rootDir.absolutePath,
            includeHidden = false
        )
        val resultsHiddenOff = searchEngine.search(criteriaHiddenOff).last()

        assertEquals(2, resultsHiddenOff.size)
        val namesOff = resultsHiddenOff.map { it.name }.toSet()
        assertTrue(namesOff.contains("regular_file.txt"))
        assertTrue(namesOff.contains("VisibleFolder"))
        assertFalse(namesOff.contains(".hidden_file.txt"))
        assertFalse(namesOff.contains(".nomedia"))
        assertFalse(namesOff.contains(".config"))

        // Search with includeHidden = true
        val criteriaHiddenOn = SearchCriteria(
            query = "",
            rootPath = rootDir.absolutePath,
            includeHidden = true
        )
        val resultsHiddenOn = searchEngine.search(criteriaHiddenOn).last()

        assertEquals(5, resultsHiddenOn.size)
        val namesOn = resultsHiddenOn.map { it.name }.toSet()
        assertTrue(namesOn.contains(".hidden_file.txt"))
        assertTrue(namesOn.contains(".nomedia"))
        assertTrue(namesOn.contains(".config"))
        assertTrue(namesOn.contains("regular_file.txt"))
        assertTrue(namesOn.contains("VisibleFolder"))
    }
}
