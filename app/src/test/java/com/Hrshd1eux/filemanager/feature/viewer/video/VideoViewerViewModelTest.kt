package com.Hrshd1eux.filemanager.feature.viewer.video

import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewerViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repository: CategoryRepository
    private lateinit var operationEngine: FileOperationEngine
    private lateinit var viewModel: VideoViewerViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        rootDir = tempFolder.newFolder("video_viewer_test_dir")
        repository = CategoryRepository(ioDispatcher = testDispatcher)
        operationEngine = FileOperationEngine(ioDispatcher = testDispatcher)
        viewModel = VideoViewerViewModel(
            categoryRepository = repository,
            operationEngine = operationEngine,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialize_discoversFolderVideosAndSelectsInitialVideo() = runTest {
        val vid1 = File(rootDir, "clip_a.mp4").apply { writeBytes(ByteArray(100)) }
        val vid2 = File(rootDir, "clip_b.mkv").apply { writeBytes(ByteArray(200)) }
        val vid3 = File(rootDir, "clip_c.webm").apply { writeBytes(ByteArray(300)) }

        viewModel.initialize(initialPath = vid2.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        val listState = viewModel.listState.value
        assertTrue(listState is VideoViewerListState.Ready)
        val ready = listState as VideoViewerListState.Ready
        assertEquals(3, ready.videos.size)
        assertEquals(1, viewModel.currentIndex.value)
        assertEquals(vid2.name, viewModel.currentVideo?.name)

        assertTrue(viewModel.hasPrevious)
        assertTrue(viewModel.hasNext)
    }

    @Test
    fun navigation_previousNextAndBounds() = runTest {
        val vid1 = File(rootDir, "v1.mp4").apply { writeBytes(ByteArray(10)) }
        val vid2 = File(rootDir, "v2.mp4").apply { writeBytes(ByteArray(20)) }
        val vid3 = File(rootDir, "v3.mp4").apply { writeBytes(ByteArray(30)) }

        viewModel.initialize(initialPath = vid1.absolutePath, directoryPath = rootDir.absolutePath)
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

        // Bounded at last item
        viewModel.goToNext()
        advanceUntilIdle()
        assertEquals(2, viewModel.currentIndex.value)

        viewModel.goToPrevious()
        advanceUntilIdle()
        assertEquals(1, viewModel.currentIndex.value)
    }

    @Test
    fun playbackStateTransitions_preparedPlayingPausedCompleted() = runTest {
        val vid = File(rootDir, "test.mp4").apply { writeBytes(ByteArray(50)) }
        viewModel.initialize(initialPath = vid.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        // Prepared
        val duration = 120_000L
        viewModel.onPrepared(duration)
        assertEquals(duration, viewModel.durationMs.value)
        assertEquals(VideoPlaybackState.Playing, viewModel.playbackState.value)

        // Pause
        viewModel.onPause()
        assertEquals(VideoPlaybackState.Paused, viewModel.playbackState.value)

        // Resume
        viewModel.onPlay()
        assertEquals(VideoPlaybackState.Playing, viewModel.playbackState.value)

        // Toggle play/pause
        viewModel.togglePlayPause()
        assertEquals(VideoPlaybackState.Paused, viewModel.playbackState.value)

        viewModel.togglePlayPause()
        assertEquals(VideoPlaybackState.Playing, viewModel.playbackState.value)

        // Completed
        viewModel.onCompleted()
        assertEquals(VideoPlaybackState.Completed, viewModel.playbackState.value)
        assertEquals(duration, viewModel.currentPositionMs.value)
    }

    @Test
    fun seeking_seekStartAndEndAndRelative() = runTest {
        val vid = File(rootDir, "test.mp4").apply { writeBytes(ByteArray(50)) }
        viewModel.initialize(initialPath = vid.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()
        viewModel.onPrepared(60_000L)

        // Seek start
        viewModel.onSeekStart(15_000L)
        assertEquals(15_000L, viewModel.seekTargetMs.value)

        // Seek end
        viewModel.onSeekEnd(20_000L)
        assertEquals(20_000L, viewModel.currentPositionMs.value)
        assertEquals(20_000L, viewModel.seekTargetMs.value)

        viewModel.clearSeekTarget()
        assertEquals(null, viewModel.seekTargetMs.value)

        // Seek relative +10s
        viewModel.seekRelative(10_000L)
        assertEquals(30_000L, viewModel.currentPositionMs.value)

        // Seek relative -15s
        viewModel.seekRelative(-15_000L)
        assertEquals(15_000L, viewModel.currentPositionMs.value)
    }

    @Test
    fun lifecyclePauseAndAudioFocus_pausesPlaybackImmediately() = runTest {
        val vid = File(rootDir, "test.mp4").apply { writeBytes(ByteArray(50)) }
        viewModel.initialize(initialPath = vid.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()
        viewModel.onPrepared(30_000L)
        assertEquals(VideoPlaybackState.Playing, viewModel.playbackState.value)

        // Screen leaves foreground (ON_PAUSE/ON_STOP) -> Must pause to avoid background battery drain!
        viewModel.onLifecyclePause()
        assertEquals(VideoPlaybackState.Paused, viewModel.playbackState.value)

        // Resume and simulate phone call / audio focus loss
        viewModel.onPlay()
        assertEquals(VideoPlaybackState.Playing, viewModel.playbackState.value)

        viewModel.onAudioFocusLost()
        assertEquals(VideoPlaybackState.Paused, viewModel.playbackState.value)
    }

    @Test
    fun errorHandling_unsupportedCodecAndDamagedFile() = runTest {
        val vid = File(rootDir, "bad.mp4").apply { writeBytes(ByteArray(50)) }
        viewModel.initialize(initialPath = vid.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        // Unsupported codec (-1010)
        viewModel.onError(what = 1, extra = -1010)
        val state = viewModel.playbackState.value
        assertTrue(state is VideoPlaybackState.Error)
        val err = state as VideoPlaybackState.Error
        assertTrue(err.isUnsupportedCodec)
        assertTrue(err.message.contains("Unsupported"))

        // Damaged file (-1007)
        viewModel.onError(what = 1, extra = -1007)
        val stateDamaged = viewModel.playbackState.value as VideoPlaybackState.Error
        assertTrue(stateDamaged.message.contains("damaged") || stateDamaged.message.contains("Malformed"))
    }

    @Test
    fun deleteCurrentVideo_updatesListAndAdvances() = runTest {
        val vid1 = File(rootDir, "clip1.mp4").apply { writeBytes(ByteArray(100)) }
        val vid2 = File(rootDir, "clip2.mp4").apply { writeBytes(ByteArray(200)) }

        viewModel.initialize(initialPath = vid1.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()
        viewModel.onPrepared(10_000L)

        var allDeleted = false
        viewModel.deleteCurrentVideo(onAllDeleted = { allDeleted = true })
        advanceUntilIdle()

        assertFalse(allDeleted)
        assertEquals(1, viewModel.videos.value.size)
        assertEquals("clip2.mp4", viewModel.currentVideo?.name)
    }

    @Test
    fun deleteLastVideo_invokesOnAllDeleted() = runTest {
        val vid = File(rootDir, "only.mp4").apply { writeBytes(ByteArray(100)) }
        viewModel.initialize(initialPath = vid.absolutePath, directoryPath = rootDir.absolutePath)
        advanceUntilIdle()

        var allDeleted = false
        viewModel.deleteCurrentVideo(onAllDeleted = { allDeleted = true })
        advanceUntilIdle()

        assertTrue(allDeleted)
        assertEquals(0, viewModel.videos.value.size)
        assertTrue(viewModel.listState.value is VideoViewerListState.Empty)
    }
}
