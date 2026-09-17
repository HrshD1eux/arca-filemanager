package com.Hrshd1eux.filemanager.feature.browser

import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.feature.browser.model.BrowserTab
import com.Hrshd1eux.filemanager.feature.browser.model.TabType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserTabTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var rootDir: File
    private lateinit var subDir: File
    private lateinit var provider: LocalFileSystemProvider
    private lateinit var viewModel: FileBrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("tab_test_root")
        subDir = File(rootDir, "SubFolder").apply { mkdir() }
        provider = LocalFileSystemProvider(
            rootDirectory = rootDir,
            displayName = "Internal",
            ioDispatcher = testDispatcher
        )
        viewModel = FileBrowserViewModel(
            storageProvider = provider,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initDefaultTabs_createsInternalSdCardUsbTabs() = runTest {
        viewModel.initDefaultTabs(context = null)
        advanceUntilIdle()

        val tabs = viewModel.tabs.value
        assertTrue("Should create at least 3 default storage tabs", tabs.size >= 3)
        assertEquals("Internal", tabs[0].title)
        assertEquals(TabType.INTERNAL, tabs[0].type)
        assertFalse("Internal tab must not be closable", tabs[0].isClosable)

        assertEquals("SD Card", tabs[1].title)
        assertEquals(TabType.SD_CARD, tabs[1].type)

        assertEquals("USB", tabs[2].title)
        assertEquals(TabType.USB, tabs[2].type)

        assertNotNull("Active tab ID should be selected", viewModel.activeTabId.value)
    }

    @Test
    fun selectTab_switchesActiveTabAndLoadsDirectory() = runTest {
        viewModel.initDefaultTabs(context = null)
        advanceUntilIdle()

        val newTabPath = subDir.absolutePath
        viewModel.addTab(path = newTabPath, title = "SubFolder", type = TabType.CUSTOM, hasPermission = true)
        advanceUntilIdle()

        val newTabId = viewModel.activeTabId.value
        val initialTabId = viewModel.tabs.value.first().id

        // Switch back to initial Internal tab
        viewModel.selectTab(initialTabId, hasPermission = true)
        advanceUntilIdle()

        assertEquals(initialTabId, viewModel.activeTabId.value)

        // Switch back to custom tab
        viewModel.selectTab(newTabId, hasPermission = true)
        advanceUntilIdle()

        assertEquals(newTabId, viewModel.activeTabId.value)
    }

    @Test
    fun selectTab_unmountedTab_emitsWarningAndDoesNotSwitch() = runTest {
        viewModel.initDefaultTabs(context = null)
        advanceUntilIdle()

        val currentActiveId = viewModel.activeTabId.value
        val unmountedTab = viewModel.tabs.value.find { !it.isAvailable }

        if (unmountedTab != null) {
            var emittedMessage = ""
            val job = launch {
                viewModel.userMessage.collect { emittedMessage = it }
            }

            viewModel.selectTab(unmountedTab.id, hasPermission = true)
            advanceUntilIdle()

            // Active tab shouldn't change to the unmounted tab
            assertEquals(currentActiveId, viewModel.activeTabId.value)
            assertTrue("Should emit warning message", emittedMessage.contains("not mounted") || emittedMessage.contains("not connected"))
            job.cancel()
        }
    }

    @Test
    fun addTab_appendsNewTabAndSwitchesToIt() = runTest {
        viewModel.initDefaultTabs(context = null)
        advanceUntilIdle()

        val beforeCount = viewModel.tabs.value.size
        viewModel.addTab(path = subDir.absolutePath, title = "Projects", type = TabType.CUSTOM, hasPermission = true)
        advanceUntilIdle()

        val afterTabs = viewModel.tabs.value
        assertEquals(beforeCount + 1, afterTabs.size)
        val addedTab = afterTabs.last()
        assertEquals(subDir.name, addedTab.title)
        assertEquals(subDir.absolutePath, addedTab.path)
        assertEquals(addedTab.id, viewModel.activeTabId.value)
    }

    @Test
    fun closeTab_removesTabAndSelectsNearestAvailable() = runTest {
        viewModel.initDefaultTabs(context = null)
        advanceUntilIdle()

        viewModel.addTab(path = subDir.absolutePath, title = "Temporary", type = TabType.CUSTOM, hasPermission = true)
        advanceUntilIdle()

        val addedTabId = viewModel.activeTabId.value
        val countBeforeClose = viewModel.tabs.value.size

        viewModel.closeTab(addedTabId, hasPermission = true)
        advanceUntilIdle()

        val afterTabs = viewModel.tabs.value
        assertEquals(countBeforeClose - 1, afterTabs.size)
        assertTrue(afterTabs.none { it.id == addedTabId })
        assertTrue(viewModel.activeTabId.value.isNotEmpty())
    }

    @Test
    fun closeTab_doesNotCloseNonClosableTab() = runTest {
        viewModel.initDefaultTabs(context = null)
        advanceUntilIdle()

        val internalTab = viewModel.tabs.value.first { !it.isClosable }
        val countBefore = viewModel.tabs.value.size

        viewModel.closeTab(internalTab.id, hasPermission = true)
        advanceUntilIdle()

        assertEquals("Non-closable tab must not be removed", countBefore, viewModel.tabs.value.size)
    }

    @Test
    fun directoryNavigation_updatesActiveTabPathAndTitle() = runTest {
        viewModel.initDefaultTabs(context = null)
        advanceUntilIdle()

        val activeId = viewModel.activeTabId.value
        viewModel.loadDirectory(subDir.absolutePath, hasPermission = true)
        advanceUntilIdle()

        val currentActiveTab = viewModel.tabs.value.first { it.id == activeId }
        assertEquals(subDir.absolutePath, currentActiveTab.path)
        assertEquals(subDir.name, currentActiveTab.title)
    }
}
