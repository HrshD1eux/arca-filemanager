package com.Hrshd1eux.filemanager.feature.browser

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
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
class FileBrowserViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var rootDir: File
    private lateinit var provider: LocalFileSystemProvider
    private lateinit var viewModel: FileBrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("arca_root")
        provider = LocalFileSystemProvider(
            rootDirectory = rootDir,
            displayName = "Test Storage",
            ioDispatcher = testDispatcher
        )
        viewModel = FileBrowserViewModel(storageProvider = provider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun buildBreadcrumbs_returnsRootWhenAtRoot() {
        val breadcrumbs = FileBrowserViewModel.buildBreadcrumbs(
            currentPath = rootDir.absolutePath,
            rootPath = rootDir.absolutePath,
            rootDisplayName = "Test Storage"
        )

        assertEquals(1, breadcrumbs.size)
        assertEquals("Test Storage", breadcrumbs[0].name)
        assertEquals(rootDir.absolutePath, breadcrumbs[0].path)
        assertTrue(breadcrumbs[0].isCurrent)
    }

    @Test
    fun buildBreadcrumbs_returnsHierarchyForSubfolder() {
        val sub1 = File(rootDir, "Download").apply { mkdir() }
        val sub2 = File(sub1, "Projects").apply { mkdir() }

        val breadcrumbs = FileBrowserViewModel.buildBreadcrumbs(
            currentPath = sub2.absolutePath,
            rootPath = rootDir.absolutePath,
            rootDisplayName = "Test Storage"
        )

        assertEquals(3, breadcrumbs.size)
        assertEquals("Test Storage", breadcrumbs[0].name)
        assertFalse(breadcrumbs[0].isCurrent)

        assertEquals("Download", breadcrumbs[1].name)
        assertFalse(breadcrumbs[1].isCurrent)

        assertEquals("Projects", breadcrumbs[2].name)
        assertTrue(breadcrumbs[2].isCurrent)
    }

    @Test
    fun loadDirectory_emitsPermissionRequiredWhenPermissionDenied() {
        viewModel.loadDirectory(rootDir.absolutePath, hasPermission = false)
        val state = viewModel.uiState.value
        assertTrue(state is BrowserUiState.PermissionRequired)
    }

    @Test
    fun loadDirectory_sortsDirectoriesBeforeFilesAlphabetically() = runTest {
        // Arrange
        File(rootDir, "zebra.txt").createNewFile()
        File(rootDir, "alpha.txt").createNewFile()
        File(rootDir, "beta_folder").mkdir()
        File(rootDir, "alpha_folder").mkdir()

        // Act
        viewModel.loadDirectory(rootDir.absolutePath, hasPermission = true)
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is BrowserUiState.Success)
        val items = (state as BrowserUiState.Success).items

        assertEquals(4, items.size)
        // Folders first, sorted alphabetically
        assertEquals("alpha_folder", items[0].name)
        assertTrue(items[0].isDirectory)
        assertEquals("beta_folder", items[1].name)
        assertTrue(items[1].isDirectory)
        // Files next, sorted alphabetically
        assertEquals("alpha.txt", items[2].name)
        assertFalse(items[2].isDirectory)
        assertEquals("zebra.txt", items[3].name)
        assertFalse(items[3].isDirectory)
    }

    @Test
    fun navigateInto_entersSubdirectory() = runTest {
        val subDir = File(rootDir, "Subfolder").apply { mkdir() }
        File(subDir, "inner.txt").createNewFile()

        val subDirNode = StorageNode(
            id = subDir.absolutePath,
            name = "Subfolder",
            path = subDir.absolutePath,
            isDirectory = true
        )

        viewModel.navigateInto(subDirNode, hasPermission = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is BrowserUiState.Success)
        val success = state as BrowserUiState.Success
        assertEquals(subDir.absolutePath, success.currentPath)
        assertEquals(1, success.items.size)
        assertEquals("inner.txt", success.items[0].name)
    }

    @Test
    fun navigateUp_returnsFalseWhenAtRoot() = runTest {
        viewModel.loadDirectory(rootDir.absolutePath, hasPermission = true)
        advanceUntilIdle()

        val handled = viewModel.navigateUp(hasPermission = true)
        assertFalse(handled)
    }

    @Test
    fun navigateUp_navigatesToParentWhenInSubfolder() = runTest {
        val subDir = File(rootDir, "Subfolder").apply { mkdir() }

        viewModel.loadDirectory(subDir.absolutePath, hasPermission = true)
        advanceUntilIdle()

        val handled = viewModel.navigateUp(hasPermission = true)
        assertTrue(handled)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is BrowserUiState.Success)
        assertEquals(rootDir.absolutePath, (state as BrowserUiState.Success).currentPath)
    }
}
