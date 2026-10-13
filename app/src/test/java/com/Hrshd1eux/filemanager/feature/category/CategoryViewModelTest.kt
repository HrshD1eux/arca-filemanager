package com.Hrshd1eux.filemanager.feature.category

import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class CategoryViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repository: CategoryRepository
    private lateinit var viewModel: CategoryViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("vm_category_root")
        repository = CategoryRepository(ioDispatcher = testDispatcher)
        viewModel = CategoryViewModel(repository = repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadCategory_emptyStateWhenNoFiles() = runTest {
        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        assertEquals(CategoryFilter.IMAGES, viewModel.category.value)
        assertTrue(viewModel.uiState.value is CategoryUiState.Empty)
    }

    @Test
    fun loadCategory_successStateWhenFilesFound() = runTest {
        File(rootDir, "photo1.jpg").apply { writeBytes(ByteArray(1024)) }
        File(rootDir, "photo2.png").apply { writeBytes(ByteArray(2048)) }

        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CategoryUiState.Success)
        val success = state as CategoryUiState.Success
        assertEquals(2, success.fileCount)
        assertEquals(3072L, success.totalSizeBytes)
    }

    @Test
    fun sortingChange_reordersImmediately() = runTest {
        val f1 = File(rootDir, "Zebra.jpg").apply {
            writeBytes(ByteArray(100))
            setLastModified(1000)
        }
        val f2 = File(rootDir, "Apple.jpg").apply {
            writeBytes(ByteArray(500))
            setLastModified(3000)
        }

        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        // Change sort to Name Ascending
        viewModel.updateSortOrder(SortOrder(field = SortField.NAME, direction = SortDirection.ASCENDING))
        advanceUntilIdle()

        val state = viewModel.uiState.value as CategoryUiState.Success
        assertEquals("Apple.jpg", state.items[0].name)
        assertEquals("Zebra.jpg", state.items[1].name)

        // Change sort to Size Descending
        viewModel.updateSortOrder(SortOrder(field = SortField.SIZE, direction = SortDirection.DESCENDING))
        advanceUntilIdle()

        val state2 = viewModel.uiState.value as CategoryUiState.Success
        assertEquals("Apple.jpg", state2.items[0].name) // 500 bytes
        assertEquals("Zebra.jpg", state2.items[1].name) // 100 bytes
    }

    @Test
    fun toggleViewMode_switchesListAndGrid() {
        viewModel.setViewMode(ViewMode.LIST)
        assertEquals(ViewMode.LIST, viewModel.viewMode.value)

        viewModel.toggleViewMode()
        assertEquals(ViewMode.GRID, viewModel.viewMode.value)

        viewModel.toggleViewMode()
        assertEquals(ViewMode.LIST, viewModel.viewMode.value)
    }

    @Test
    fun deleteFile_updatesStateAndFileCount() = runTest {
        val f1 = File(rootDir, "del1.jpg").apply { writeBytes(ByteArray(100)) }
        val f2 = File(rootDir, "del2.jpg").apply { writeBytes(ByteArray(200)) }

        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        val initialSuccess = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(2, initialSuccess.fileCount)

        viewModel.deleteFile(initialSuccess.items[0])
        advanceUntilIdle()

        val afterDelete = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(1, afterDelete.fileCount)
    }
}
