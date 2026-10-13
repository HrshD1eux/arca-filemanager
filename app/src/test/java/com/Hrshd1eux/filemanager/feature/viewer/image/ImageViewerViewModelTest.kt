package com.Hrshd1eux.filemanager.feature.viewer.image

import android.graphics.Bitmap
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.feature.category.CategoryRepository
import com.Hrshd1eux.filemanager.feature.viewer.image.decoder.ImageBitmapDecoder
import com.Hrshd1eux.filemanager.feature.viewer.image.model.ImageDimensions
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
class ImageViewerViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repository: CategoryRepository
    private lateinit var operationEngine: FileOperationEngine
    private lateinit var fakeDecoder: FakeImageBitmapDecoder
    private lateinit var viewModel: ImageViewerViewModel
    private val testDispatcher = StandardTestDispatcher()

    private class FakeImageBitmapDecoder : ImageBitmapDecoder {
        var shouldFail = false
        val inspectedPaths = mutableListOf<String>()
        val decodedPaths = mutableListOf<String>()

        private val dummyBitmap: Bitmap by lazy {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = theUnsafeField.get(null)
            val allocateInstanceMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
            allocateInstanceMethod.invoke(unsafe, Bitmap::class.java) as Bitmap
        }

        override suspend fun inspectDimensions(filePath: String): ImageDimensions? {
            inspectedPaths.add(filePath)
            if (shouldFail) return null
            return ImageDimensions(width = 1920, height = 1080)
        }

        override suspend fun decodeBitmap(filePath: String, maxDimension: Int): Result<Bitmap> {
            decodedPaths.add(filePath)
            if (shouldFail) return Result.failure(IllegalStateException("Failed to decode"))
            return Result.success(dummyBitmap)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("viewer_test_dir")
        repository = CategoryRepository(ioDispatcher = testDispatcher)
        operationEngine = FileOperationEngine(ioDispatcher = testDispatcher)
        fakeDecoder = FakeImageBitmapDecoder()
        viewModel = ImageViewerViewModel(
            categoryRepository = repository,
            bitmapDecoder = fakeDecoder,
            operationEngine = operationEngine,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialize_loadsFolderImagesAndSelectsInitialImage() = runTest {
        val img1 = File(rootDir, "a.jpg").apply { writeBytes(ByteArray(100)) }
        val img2 = File(rootDir, "b.png").apply { writeBytes(ByteArray(200)) }
        val img3 = File(rootDir, "c.webp").apply { writeBytes(ByteArray(300)) }

        viewModel.initialize(initialPath = img2.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        val state = viewModel.listState.value
        assertTrue(state is ImageViewerListState.Ready)
        val ready = state as ImageViewerListState.Ready
        assertEquals(3, ready.images.size)
        assertEquals(1, viewModel.currentIndex.value)
        assertEquals(img2.name, viewModel.currentImage?.name)

        // Previous and Next flags
        assertTrue(viewModel.hasPrevious)
        assertTrue(viewModel.hasNext)

        // Bitmap loaded successfully
        val bitmapState = viewModel.currentBitmapState.value
        assertTrue(bitmapState is ImageBitmapState.Success)
        val success = bitmapState as ImageBitmapState.Success
        assertEquals(1920, success.dimensions.width)
        assertEquals(1080, success.dimensions.height)
    }

    @Test
    fun navigation_previousAndNextAndBounds() = runTest {
        val img1 = File(rootDir, "1.jpg").apply { writeBytes(ByteArray(10)) }
        val img2 = File(rootDir, "2.jpg").apply { writeBytes(ByteArray(20)) }
        val img3 = File(rootDir, "3.jpg").apply { writeBytes(ByteArray(30)) }

        viewModel.initialize(initialPath = img1.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        assertEquals(0, viewModel.currentIndex.value)
        assertFalse(viewModel.hasPrevious)
        assertTrue(viewModel.hasNext)

        viewModel.goToNext()
        advanceUntilIdle()
        assertEquals(1, viewModel.currentIndex.value)
        assertTrue(viewModel.hasPrevious)
        assertTrue(viewModel.hasNext)

        viewModel.goToNext()
        advanceUntilIdle()
        assertEquals(2, viewModel.currentIndex.value)
        assertTrue(viewModel.hasPrevious)
        assertFalse(viewModel.hasNext)

        // Cannot go past end
        viewModel.goToNext()
        advanceUntilIdle()
        assertEquals(2, viewModel.currentIndex.value)

        viewModel.goToPrevious()
        advanceUntilIdle()
        assertEquals(1, viewModel.currentIndex.value)
    }

    @Test
    fun corruptedImage_reportsErrorStateGracefully() = runTest {
        val badImg = File(rootDir, "corrupt.jpg").apply { writeBytes(ByteArray(50)) }
        fakeDecoder.shouldFail = true

        viewModel.initialize(initialPath = badImg.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        val bitmapState = viewModel.currentBitmapState.value
        assertTrue(bitmapState is ImageBitmapState.Error)
        val err = bitmapState as ImageBitmapState.Error
        assertTrue(err.message.contains("Corrupted") || err.message.contains("Failed"))

        // Dimensions null or error preserved
        assertNotNull(viewModel.currentImage)
        assertEquals(badImg.name, viewModel.currentImage?.name)
    }

    @Test
    fun deleteCurrentImage_updatesListAndAdvancesToAdjacent() = runTest {
        val img1 = File(rootDir, "first.jpg").apply { writeBytes(ByteArray(100)) }
        val img2 = File(rootDir, "second.jpg").apply { writeBytes(ByteArray(200)) }
        val img3 = File(rootDir, "third.jpg").apply { writeBytes(ByteArray(300)) }

        viewModel.initialize(initialPath = img2.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        assertEquals(1, viewModel.currentIndex.value)
        assertEquals("second.jpg", viewModel.currentImage?.name)

        var allDeletedInvoked = false
        viewModel.deleteCurrentImage(onAllDeleted = { allDeletedInvoked = true })
        advanceUntilIdle()

        assertFalse(allDeletedInvoked)
        assertEquals(2, viewModel.images.value.size)
        assertEquals(1, viewModel.currentIndex.value)
        assertEquals("third.jpg", viewModel.currentImage?.name)
    }

    @Test
    fun deleteLastImage_invokesOnAllDeleted() = runTest {
        val singleImg = File(rootDir, "sole.jpg").apply { writeBytes(ByteArray(100)) }

        viewModel.initialize(initialPath = singleImg.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        assertEquals(1, viewModel.images.value.size)

        var allDeletedInvoked = false
        viewModel.deleteCurrentImage(onAllDeleted = { allDeletedInvoked = true })
        advanceUntilIdle()

        assertTrue(allDeletedInvoked)
        assertEquals(0, viewModel.images.value.size)
        assertTrue(viewModel.listState.value is ImageViewerListState.Empty)
    }
}
