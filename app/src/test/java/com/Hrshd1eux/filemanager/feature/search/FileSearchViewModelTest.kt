package com.Hrshd1eux.filemanager.feature.search

import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.search.engine.FileSearchEngine
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.DateFilter
import com.Hrshd1eux.filemanager.core.search.model.SizeFilter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.feature.category.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class FileSearchViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var viewModel: FileSearchViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("search_vm_root")
        val storageProvider = LocalFileSystemProvider(rootDirectory = rootDir)
        val searchEngine = FileSearchEngine(ioDispatcher = testDispatcher)
        val repository = CategoryRepository(ioDispatcher = testDispatcher)
        val operationEngine = FileOperationEngine(ioDispatcher = testDispatcher)

        viewModel = FileSearchViewModel(
            storageProvider = storageProvider,
            ioDispatcher = testDispatcher,
            searchEngine = searchEngine,
            repository = repository,
            operationEngine = operationEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun multiSelection_toggleSelectAllAndClear() = runTest {
        val node1 = StorageNode(
            id = File(rootDir, "file1.txt").apply { writeText("1") }.absolutePath,
            name = "file1.txt",
            path = File(rootDir, "file1.txt").absolutePath,
            isDirectory = false,
            size = 1L,
            lastModified = System.currentTimeMillis()
        )
        val node2 = StorageNode(
            id = File(rootDir, "file2.txt").apply { writeText("2") }.absolutePath,
            name = "file2.txt",
            path = File(rootDir, "file2.txt").absolutePath,
            isDirectory = false,
            size = 1L,
            lastModified = System.currentTimeMillis()
        )

        assertFalse(viewModel.isSelectionMode)
        assertEquals(0, viewModel.selectedNodes.value.size)

        // Start selection with node1
        viewModel.startSelection(node1)
        assertTrue(viewModel.isSelectionMode)
        assertEquals(1, viewModel.selectedNodes.value.size)

        // Toggle node2
        viewModel.toggleSelection(node2)
        assertEquals(2, viewModel.selectedNodes.value.size)

        // Toggle Select All (when all items are selected, it should deselect all)
        val allItems = listOf(node1, node2)
        viewModel.toggleSelectAll(allItems)
        assertEquals(0, viewModel.selectedNodes.value.size)
        assertFalse(viewModel.isSelectionMode)

        // Toggle Select All again (selects all)
        viewModel.toggleSelectAll(allItems)
        assertEquals(2, viewModel.selectedNodes.value.size)
        assertTrue(viewModel.isSelectionMode)

        // Clear Selection
        viewModel.clearSelection()
        assertEquals(0, viewModel.selectedNodes.value.size)
        assertFalse(viewModel.isSelectionMode)
    }

    @Test
    fun deleteSingle_removesNodeFromSuccessResults() = runTest {
        val testFile = File(rootDir, "to_delete.txt").apply { writeText("hello") }
        val node = StorageNode(
            id = testFile.absolutePath,
            name = testFile.name,
            path = testFile.absolutePath,
            isDirectory = false,
            size = testFile.length(),
            lastModified = testFile.lastModified()
        )

        // Simulate search results
        viewModel.setRootPath(rootDir.absolutePath)
        viewModel.onQueryChange("to_delete")
        advanceUntilIdle()

        var doneSuccess = false
        viewModel.deleteSingle(node, permanent = true) { success ->
            doneSuccess = success
        }
        advanceUntilIdle()

        assertTrue(doneSuccess)
        assertFalse(testFile.exists())
    }

    @Test
    fun filterToggles_workAsExpected() = runTest {
        viewModel.onCategorySelect(CategoryFilter.AUDIO)
        assertEquals(CategoryFilter.AUDIO, viewModel.category.value)

        // Toggling same category resets to ALL
        viewModel.onCategorySelect(CategoryFilter.AUDIO)
        assertEquals(CategoryFilter.ALL, viewModel.category.value)

        viewModel.onSizeFilterSelect(SizeFilter.BETWEEN_1MB_AND_100MB)
        assertEquals(SizeFilter.BETWEEN_1MB_AND_100MB, viewModel.sizeFilter.value)

        viewModel.onDateFilterSelect(DateFilter.THIS_WEEK)
        assertEquals(DateFilter.THIS_WEEK, viewModel.dateFilter.value)

        viewModel.onQueryChange("search_term")
        assertEquals("search_term", viewModel.query.value)

        viewModel.clearQuery()
        assertEquals("", viewModel.query.value)
    }
}
