package com.Hrshd1eux.filemanager.feature.category

import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
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
class CategoryViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repository: CategoryRepository
    private lateinit var operationEngine: FileOperationEngine
    private lateinit var viewModel: CategoryViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("vm_category_root")
        repository = CategoryRepository(ioDispatcher = testDispatcher)
        operationEngine = FileOperationEngine(ioDispatcher = testDispatcher)
        viewModel = CategoryViewModel(
            repository = repository,
            operationEngine = operationEngine,
            ioDispatcher = testDispatcher
        )
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
        val state = viewModel.uiState.value
        assertTrue(state is CategoryUiState.Empty)
        assertTrue((state as CategoryUiState.Empty).message.contains("Photos and pictures"))
    }

    @Test
    fun loadCategory_inaccessibleStorageReportsGracefulError() = runTest {
        viewModel.loadCategory("IMAGES", "/non_existent_or_removed_usb_storage")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CategoryUiState.Error)
        val error = state as CategoryUiState.Error
        assertTrue(error.message.contains("unavailable") || error.message.contains("inaccessible"))
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
    fun multiSelection_toggleAndSelectAllAndClear() = runTest {
        File(rootDir, "p1.jpg").apply { writeBytes(ByteArray(100)) }
        File(rootDir, "p2.jpg").apply { writeBytes(ByteArray(200)) }
        File(rootDir, "p3.jpg").apply { writeBytes(ByteArray(300)) }

        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        val success = viewModel.uiState.value as CategoryUiState.Success
        val items = success.items

        assertFalse(viewModel.isSelectionMode)
        assertEquals(0, viewModel.selectedNodes.value.size)

        // Start selection with first item
        viewModel.startSelection(items[0])
        assertTrue(viewModel.isSelectionMode)
        assertEquals(1, viewModel.selectedNodes.value.size)
        assertTrue(viewModel.selectedNodes.value.contains(items[0]))

        // Toggle second item
        viewModel.toggleSelection(items[1])
        assertEquals(2, viewModel.selectedNodes.value.size)

        // Select All
        viewModel.selectAll()
        assertEquals(3, viewModel.selectedNodes.value.size)

        // Toggle Select All (when all selected -> deselects all)
        viewModel.toggleSelectAll()
        assertEquals(0, viewModel.selectedNodes.value.size)
        assertFalse(viewModel.isSelectionMode)

        // Toggle Select All again (selects all)
        viewModel.toggleSelectAll()
        assertEquals(3, viewModel.selectedNodes.value.size)
        assertTrue(viewModel.isSelectionMode)

        // Clear Selection
        viewModel.clearSelection()
        assertFalse(viewModel.isSelectionMode)
        assertEquals(0, viewModel.selectedNodes.value.size)
    }

    @Test
    fun sortOrder_directionAndFieldControls() = runTest {
        viewModel.loadCategory("DOCUMENTS", rootDir.absolutePath)
        advanceUntilIdle()

        assertEquals(SortField.DATE, viewModel.sortOrder.value.field)
        assertEquals(SortDirection.DESCENDING, viewModel.sortOrder.value.direction)

        // Toggle direction
        viewModel.toggleSortDirection()
        assertEquals(SortDirection.ASCENDING, viewModel.sortOrder.value.direction)

        // Change field
        viewModel.setSortField(SortField.NAME)
        assertEquals(SortField.NAME, viewModel.sortOrder.value.field)

        // Toggle on same field flips direction
        viewModel.setSortField(SortField.NAME)
        assertEquals(SortDirection.DESCENDING, viewModel.sortOrder.value.direction)
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
    fun deleteSingle_updatesStateAndFileCount() = runTest {
        val f1 = File(rootDir, "del1.jpg").apply { writeBytes(ByteArray(100)) }
        val f2 = File(rootDir, "del2.jpg").apply { writeBytes(ByteArray(200)) }

        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        val initialSuccess = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(2, initialSuccess.fileCount)

        viewModel.deleteSingle(initialSuccess.items[0])
        advanceUntilIdle()

        val afterDelete = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(1, afterDelete.fileCount)
    }

    @Test
    fun deleteSelected_bulkDeletion() = runTest {
        val f1 = File(rootDir, "bulk1.jpg").apply { writeBytes(ByteArray(100)) }
        val f2 = File(rootDir, "bulk2.jpg").apply { writeBytes(ByteArray(200)) }

        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        val initialSuccess = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(2, initialSuccess.fileCount)

        viewModel.selectAll()
        assertEquals(2, viewModel.selectedNodes.value.size)

        var doneCalled = false
        viewModel.deleteSelected { doneCalled = true }
        advanceUntilIdle()

        assertTrue(doneCalled)
        assertFalse(viewModel.isSelectionMode)
        assertTrue(viewModel.uiState.value is CategoryUiState.Empty)
    }

    @Test
    fun search_filtersDocumentsCaseInsensitivelyAndRestoresOnClose() = runTest {
        File(rootDir, "Annual_Report_2025.pdf").apply { writeBytes(ByteArray(300)) }
        File(rootDir, "Meeting_Notes.docx").apply { writeBytes(ByteArray(150)) }
        File(rootDir, "Tax_Invoice.pdf").apply { writeBytes(ByteArray(450)) }

        viewModel.loadCategory("DOCUMENTS", rootDir.absolutePath)
        advanceUntilIdle()

        assertFalse(viewModel.isSearching.value)
        assertEquals(3, (viewModel.uiState.value as CategoryUiState.Success).fileCount)

        viewModel.toggleSearch()
        assertTrue(viewModel.isSearching.value)

        viewModel.setSearchQuery("invoice")
        val filtered = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(1, filtered.fileCount)
        assertEquals(450L, filtered.totalSizeBytes)
        assertEquals("Tax_Invoice.pdf", filtered.items[0].name)

        // Select All selects only the visible filtered result
        viewModel.selectAll()
        assertEquals(1, viewModel.selectedNodes.value.size)
        assertEquals("Tax_Invoice.pdf", viewModel.selectedNodes.value.first().name)

        // Non-matching query transitions to search-specific Empty state
        viewModel.setSearchQuery("spreadsheet_missing")
        val emptyState = viewModel.uiState.value as CategoryUiState.Empty
        assertTrue(emptyState.isSearchFilterEmpty)
        assertTrue(emptyState.message.contains("spreadsheet_missing"))

        // Closing search clears query and restores all documents
        viewModel.closeSearch()
        assertFalse(viewModel.isSearching.value)
        assertEquals("", viewModel.searchQuery.value)
        assertEquals(3, (viewModel.uiState.value as CategoryUiState.Success).fileCount)
    }

    @Test
    fun search_filtersAudioFilesAndResetsWhenSwitchingCategory() = runTest {
        File(rootDir, "ambient_rain.mp3").apply { writeBytes(ByteArray(500)) }
        File(rootDir, "podcast_episode_12.m4a").apply { writeBytes(ByteArray(700)) }
        File(rootDir, "contract.pdf").apply { writeBytes(ByteArray(200)) }

        viewModel.loadCategory("AUDIO", rootDir.absolutePath)
        advanceUntilIdle()

        assertEquals(2, (viewModel.uiState.value as CategoryUiState.Success).fileCount)

        viewModel.toggleSearch()
        viewModel.setSearchQuery("podcast")
        val filtered = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(1, filtered.fileCount)
        assertEquals("podcast_episode_12.m4a", filtered.items[0].name)

        // Switching category resets search mode and query
        viewModel.loadCategory("DOCUMENTS", rootDir.absolutePath)
        advanceUntilIdle()
        assertFalse(viewModel.isSearching.value)
        assertEquals("", viewModel.searchQuery.value)
        val docs = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(1, docs.fileCount)
        assertEquals("contract.pdf", docs.items[0].name)
    }

    @Test
    fun dateGrouping_populatesDateGroupsAndCanBeToggled() = runTest {
        val now = System.currentTimeMillis()
        File(rootDir, "photo_today.jpg").apply {
            writeBytes(ByteArray(100))
            setLastModified(now)
        }
        File(rootDir, "photo_yesterday.jpg").apply {
            writeBytes(ByteArray(200))
            setLastModified(now - 26 * 3600 * 1000L)
        }

        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        assertTrue(viewModel.groupByDate.value)
        val state = viewModel.uiState.value as CategoryUiState.Success
        assertEquals(2, state.fileCount)
        assertTrue(state.dateGroups.isNotEmpty())

        // Toggle date grouping off
        viewModel.toggleGroupByDate()
        advanceUntilIdle()
        assertFalse(viewModel.groupByDate.value)
        val stateNoGroups = viewModel.uiState.value as CategoryUiState.Success
        assertTrue(stateNoGroups.dateGroups.isEmpty())
        assertEquals(2, stateNoGroups.items.size)

        // Toggle back on
        viewModel.toggleGroupByDate()
        advanceUntilIdle()
        assertTrue(viewModel.groupByDate.value)
        val stateRegrouped = viewModel.uiState.value as CategoryUiState.Success
        assertTrue(stateRegrouped.dateGroups.isNotEmpty())
    }

    @Test
    fun toggleGroupSelection_selectsAndDeselectsEntireGroup() = runTest {
        val now = System.currentTimeMillis()
        File(rootDir, "f1.jpg").apply {
            writeBytes(ByteArray(100))
            setLastModified(now)
        }
        File(rootDir, "f2.jpg").apply {
            writeBytes(ByteArray(200))
            setLastModified(now)
        }

        viewModel.loadCategory("IMAGES", rootDir.absolutePath)
        advanceUntilIdle()

        val state = viewModel.uiState.value as CategoryUiState.Success
        val group = state.dateGroups.first()
        assertEquals(2, group.items.size)

        // Select the group
        viewModel.toggleGroupSelection(group)
        assertEquals(2, viewModel.selectedNodes.value.size)
        assertTrue(viewModel.isSelectionMode)

        // Toggle again deselects the group
        viewModel.toggleGroupSelection(group)
        assertEquals(0, viewModel.selectedNodes.value.size)
        assertFalse(viewModel.isSelectionMode)
    }
}
