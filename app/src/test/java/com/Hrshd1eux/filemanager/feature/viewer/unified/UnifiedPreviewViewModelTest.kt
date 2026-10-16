package com.Hrshd1eux.filemanager.feature.viewer.unified

import android.graphics.Bitmap
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.feature.category.CategoryRepository
import com.Hrshd1eux.filemanager.feature.viewer.image.decoder.ImageBitmapDecoder
import com.Hrshd1eux.filemanager.feature.viewer.image.model.ImageDimensions
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewState
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewType
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedPreviewViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: UnifiedPreviewViewModel

    private class FakeImageBitmapDecoder : ImageBitmapDecoder {
        override suspend fun inspectDimensions(filePath: String): ImageDimensions {
            return ImageDimensions(width = 1920, height = 1080)
        }

        override suspend fun decodeBitmap(filePath: String, maxDimension: Int): Result<Bitmap> {
            return Result.failure(UnsupportedOperationException("Fake decoder"))
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = UnifiedPreviewViewModel(
            categoryRepository = CategoryRepository(),
            operationEngine = FileOperationEngine(),
            bitmapDecoder = FakeImageBitmapDecoder(),
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes queue and resolves current file and index correctly`() = runTest(testDispatcher) {
        val file1 = tempFolder.newFile("alpha.txt").apply { writeText("Alpha text content") }
        val file2 = tempFolder.newFile("beta.txt").apply { writeText("Beta text content") }
        val file3 = tempFolder.newFile("gamma.txt").apply { writeText("Gamma text content") }

        viewModel.initialize(
            initialPath = file2.absolutePath,
            directoryPath = tempFolder.root.absolutePath
        )
        advanceUntilIdle()

        assertEquals(3, viewModel.files.value.size)
        assertEquals(1, viewModel.currentIndex.value)
        assertEquals("beta.txt", viewModel.currentFile?.name)
        assertTrue(viewModel.hasPrevious)
        assertTrue(viewModel.hasNext)
        assertEquals(PreviewType.TEXT, viewModel.previewType.value)
        assertTrue(viewModel.previewState.value is PreviewState.TextReady)
    }

    @Test
    fun `navigates next and previous properly within queue bounds`() = runTest(testDispatcher) {
        val file1 = tempFolder.newFile("a.txt").apply { writeText("A") }
        val file2 = tempFolder.newFile("b.txt").apply { writeText("B") }
        val file3 = tempFolder.newFile("c.txt").apply { writeText("C") }

        viewModel.initialize(
            initialPath = file1.absolutePath,
            directoryPath = tempFolder.root.absolutePath
        )
        advanceUntilIdle()

        assertEquals(0, viewModel.currentIndex.value)
        assertFalse(viewModel.hasPrevious)
        assertTrue(viewModel.hasNext)

        viewModel.navigateNext()
        advanceUntilIdle()

        assertEquals(1, viewModel.currentIndex.value)
        assertEquals("b.txt", viewModel.currentFile?.name)
        assertTrue(viewModel.hasPrevious)
        assertTrue(viewModel.hasNext)

        viewModel.navigateNext()
        advanceUntilIdle()

        assertEquals(2, viewModel.currentIndex.value)
        assertEquals("c.txt", viewModel.currentFile?.name)
        assertTrue(viewModel.hasPrevious)
        assertFalse(viewModel.hasNext)

        // Try navigating past end
        viewModel.navigateNext()
        advanceUntilIdle()
        assertEquals(2, viewModel.currentIndex.value)

        // Navigate backwards
        viewModel.navigatePrevious()
        advanceUntilIdle()
        assertEquals(1, viewModel.currentIndex.value)
        assertEquals("b.txt", viewModel.currentFile?.name)
    }

    @Test
    fun `toggles text settings state flows`() = runTest(testDispatcher) {
        val file = tempFolder.newFile("code.kt").apply { writeText("fun main() = println()") }

        viewModel.initialize(
            initialPath = file.absolutePath,
            directoryPath = tempFolder.root.absolutePath
        )
        advanceUntilIdle()

        val initialLineNums = viewModel.showLineNumbers.value
        viewModel.toggleLineNumbers()
        assertEquals(!initialLineNums, viewModel.showLineNumbers.value)

        val initialWordWrap = viewModel.wordWrap.value
        viewModel.toggleWordWrap()
        assertEquals(!initialWordWrap, viewModel.wordWrap.value)

        val initialMonospace = viewModel.isMonospace.value
        viewModel.toggleMonospace()
        assertEquals(!initialMonospace, viewModel.isMonospace.value)
    }

    @Test
    fun `dispatches unsupported state for unknown file types`() = runTest(testDispatcher) {
        val unsupportedFile = tempFolder.newFile("binary_data.bin").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        }

        viewModel.initialize(
            initialPath = unsupportedFile.absolutePath,
            directoryPath = tempFolder.root.absolutePath
        )
        advanceUntilIdle()

        assertEquals(PreviewType.UNSUPPORTED, viewModel.previewType.value)
        val state = viewModel.previewState.value
        assertTrue(state is PreviewState.Unsupported)
        assertEquals("binary_data.bin", (state as PreviewState.Unsupported).file.name)
    }

    @Test
    fun `deletes current file and advances queue correctly`() = runTest(testDispatcher) {
        val file1 = tempFolder.newFile("file1.txt").apply { writeText("File 1") }
        val file2 = tempFolder.newFile("file2.txt").apply { writeText("File 2") }

        viewModel.initialize(
            initialPath = file1.absolutePath,
            directoryPath = tempFolder.root.absolutePath
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.files.value.size)
        assertEquals(0, viewModel.currentIndex.value)

        var dismissed = false
        viewModel.deleteCurrent { dismissed = true }
        advanceUntilIdle()

        assertFalse(dismissed)
        assertEquals(1, viewModel.files.value.size)
        assertEquals("file2.txt", viewModel.currentFile?.name)
        assertFalse(file1.exists())

        // Delete the last remaining file
        viewModel.deleteCurrent { dismissed = true }
        advanceUntilIdle()

        assertTrue(dismissed)
        assertTrue(viewModel.files.value.isEmpty())
        assertFalse(file2.exists())
    }

    @Test
    fun `updates file list when file is moved`() = runTest(testDispatcher) {
        val file1 = tempFolder.newFile("doc1.txt").apply { writeText("Doc 1") }
        val file2 = tempFolder.newFile("doc2.txt").apply { writeText("Doc 2") }

        viewModel.initialize(
            initialPath = file1.absolutePath,
            directoryPath = tempFolder.root.absolutePath
        )
        advanceUntilIdle()

        var dismissed = false
        viewModel.onFileMoved("/destination/doc1.txt") { dismissed = true }

        assertFalse(dismissed)
        assertEquals(1, viewModel.files.value.size)
        assertEquals("doc2.txt", viewModel.currentFile?.name)
    }
}
