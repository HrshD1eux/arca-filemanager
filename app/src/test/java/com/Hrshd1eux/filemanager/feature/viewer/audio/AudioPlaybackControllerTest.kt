package com.Hrshd1eux.filemanager.feature.viewer.audio

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioPlaybackState
import com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController
import com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlayerEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlaybackControllerTest {

    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var controller: AudioPlaybackController
    private val testDispatcher = UnconfinedTestDispatcher()

    private val track1 = StorageNode(id = "1", name = "track1.mp3", path = "/music/track1.mp3", isDirectory = false, size = 5_000_000L)
    private val track2 = StorageNode(id = "2", name = "track2.flac", path = "/music/track2.flac", isDirectory = false, size = 25_000_000L)
    private val track3 = StorageNode(id = "3", name = "track3.m4a", path = "/music/track3.m4a", isDirectory = false, size = 8_000_000L)

    @Before
    fun setup() {
        fakeEngine = FakeAudioPlayerEngine()
        controller = AudioPlaybackController(
            context = null,
            engine = fakeEngine,
            customAudioFocusHelper = null,
            customBecomingNoisyReceiver = null,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(AudioPlaybackState.Idle, controller.playbackState.value)
        assertNull(controller.currentTrack.value)
        assertEquals(emptyList<StorageNode>(), controller.playlist.value)
        assertEquals(-1, controller.currentIndex.value)
        assertFalse(controller.isExpanded.value)
    }

    @Test
    fun `playTrack transitions through Preparing to Playing`() = runTest {
        controller.playTrack(track1, listOf(track1, track2, track3))

        assertEquals(track1, controller.currentTrack.value)
        assertEquals(0, controller.currentIndex.value)
        assertEquals(3, controller.playlist.value.size)
        assertTrue(controller.playbackState.value is AudioPlaybackState.Preparing)
        assertEquals("/music/track1.mp3", fakeEngine.lastSetDataSource)

        // Engine prepares
        fakeEngine.triggerPrepared()

        assertTrue(controller.playbackState.value is AudioPlaybackState.Playing)
        assertEquals(1, fakeEngine.startCount)
    }

    @Test
    fun `pause and play toggles playback states`() = runTest {
        controller.playTrack(track1, listOf(track1))
        fakeEngine.triggerPrepared()
        assertTrue(controller.playbackState.value is AudioPlaybackState.Playing)

        controller.pause()
        assertTrue(controller.playbackState.value is AudioPlaybackState.Paused)
        assertEquals(1, fakeEngine.pauseCount)

        controller.play()
        assertTrue(controller.playbackState.value is AudioPlaybackState.Playing)
        assertEquals(2, fakeEngine.startCount)

        controller.togglePlayPause()
        assertTrue(controller.playbackState.value is AudioPlaybackState.Paused)

        controller.togglePlayPause()
        assertTrue(controller.playbackState.value is AudioPlaybackState.Playing)
    }

    @Test
    fun `seekTo and skipForward skipBackward update position`() = runTest {
        controller.playTrack(track1, listOf(track1))
        fakeEngine.triggerPrepared()

        controller.seekTo(30_000L)
        assertEquals(30_000L, controller.currentPositionMs.value)
        assertEquals(30_000L, fakeEngine.currentPositionMs)

        controller.skipForward(10)
        assertEquals(40_000L, controller.currentPositionMs.value)

        controller.skipBackward(15)
        assertEquals(25_000L, controller.currentPositionMs.value)
    }

    @Test
    fun `playNext and playPrevious navigate through playlist`() = runTest {
        controller.playTrack(track1, listOf(track1, track2, track3))
        fakeEngine.triggerPrepared()
        assertEquals(0, controller.currentIndex.value)

        // Next track
        controller.playNext()
        assertEquals(1, controller.currentIndex.value)
        assertEquals(track2, controller.currentTrack.value)

        // Next track
        controller.playNext()
        assertEquals(2, controller.currentIndex.value)
        assertEquals(track3, controller.currentTrack.value)

        // Beyond end of playlist: stays on track 3
        controller.playNext()
        assertEquals(2, controller.currentIndex.value)

        // Play previous when at position 0: moves to track 2
        controller.playPrevious()
        assertEquals(1, controller.currentIndex.value)
        assertEquals(track2, controller.currentTrack.value)
    }

    @Test
    fun `playPrevious rewinds to 0 if played more than 3 seconds`() = runTest {
        controller.playTrack(track2, listOf(track1, track2, track3))
        fakeEngine.triggerPrepared()
        controller.seekTo(10_000L)

        // Position > 3s: rewinds current track instead of previous index
        controller.playPrevious()
        assertEquals(1, controller.currentIndex.value)
        assertEquals(0L, controller.currentPositionMs.value)
    }

    @Test
    fun `auto-advances to next track upon track completion`() = runTest {
        controller.playTrack(track1, listOf(track1, track2))
        fakeEngine.triggerPrepared()

        fakeEngine.triggerCompletion()

        // Should advance to track2
        assertEquals(1, controller.currentIndex.value)
        assertEquals(track2, controller.currentTrack.value)
    }

    @Test
    fun `transitions to Completed on last track completion`() = runTest {
        controller.playTrack(track2, listOf(track2))
        fakeEngine.triggerPrepared()

        fakeEngine.triggerCompletion()

        assertTrue(controller.playbackState.value is AudioPlaybackState.Completed)
    }

    @Test
    fun `transitions to Error state when engine reports failure`() = runTest {
        controller.playTrack(track1, listOf(track1))

        fakeEngine.triggerError(-1004, 0)

        val state = controller.playbackState.value
        assertTrue(state is AudioPlaybackState.Error)
        assertEquals("Unsupported audio format", (state as AudioPlaybackState.Error).message)
    }

    @Test
    fun `becoming noisy pauses active playback`() = runTest {
        controller.playTrack(track1, listOf(track1))
        fakeEngine.triggerPrepared()
        assertTrue(controller.playbackState.value is AudioPlaybackState.Playing)

        controller.onBecomingNoisy()
        assertTrue(controller.playbackState.value is AudioPlaybackState.Paused)
    }

    @Test
    fun `audio focus ducking lowers volume and gain restores volume`() = runTest {
        controller.playTrack(track1, listOf(track1))
        fakeEngine.triggerPrepared()

        // Transient loss with ducking
        controller.onAudioFocusLost(isTransient = true, canDuck = true)
        assertEquals(0.2f, fakeEngine.lastVolume, 0.01f)
        assertTrue(controller.playbackState.value is AudioPlaybackState.Playing)

        // Focus restored
        controller.onAudioFocusGained()
        assertEquals(1.0f, fakeEngine.lastVolume, 0.01f)
    }

    @Test
    fun `audio focus loss without ducking pauses playback`() = runTest {
        controller.playTrack(track1, listOf(track1))
        fakeEngine.triggerPrepared()

        controller.onAudioFocusLost(isTransient = false, canDuck = false)
        assertTrue(controller.playbackState.value is AudioPlaybackState.Paused)
    }

    @Test
    fun `onAppBackgrounded pauses playback according to lifecycle requirements`() = runTest {
        controller.playTrack(track1, listOf(track1))
        fakeEngine.triggerPrepared()

        controller.onAppBackgrounded()
        assertTrue(controller.playbackState.value is AudioPlaybackState.Paused)
    }

    @Test
    fun `removeTrack switches to next track or dismisses player`() = runTest {
        controller.playTrack(track1, listOf(track1, track2))
        fakeEngine.triggerPrepared()

        // Remove track 1: switches to track 2
        controller.removeTrack(track1)
        assertEquals(1, controller.playlist.value.size)
        assertEquals(track2, controller.currentTrack.value)

        // Remove track 2: playlist empty -> dismisses to Idle
        controller.removeTrack(track2)
        assertEquals(AudioPlaybackState.Idle, controller.playbackState.value)
        assertNull(controller.currentTrack.value)
    }

    @Test
    fun `stopAndDismiss resets player to Idle state`() = runTest {
        controller.playTrack(track1, listOf(track1, track2))
        fakeEngine.triggerPrepared()
        controller.expandPlayer()
        assertTrue(controller.isExpanded.value)

        controller.stopAndDismiss()

        assertEquals(AudioPlaybackState.Idle, controller.playbackState.value)
        assertNull(controller.currentTrack.value)
        assertEquals(0, controller.playlist.value.size)
        assertFalse(controller.isExpanded.value)
    }
}

class FakeAudioPlayerEngine : AudioPlayerEngine {
    override var isPlaying: Boolean = false
    override var currentPositionMs: Long = 0L
    override var durationMs: Long = 180_000L

    var lastSetDataSource: String? = null
    var startCount: Int = 0
    var pauseCount: Int = 0
    var stopCount: Int = 0
    var resetCount: Int = 0
    var releaseCount: Int = 0
    var lastVolume: Float = 1.0f

    private var onPreparedListener: (() -> Unit)? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((Int, Int) -> Boolean)? = null

    fun triggerPrepared() {
        onPreparedListener?.invoke()
    }

    fun triggerCompletion() {
        onCompletionListener?.invoke()
    }

    fun triggerError(what: Int, extra: Int) {
        onErrorListener?.invoke(what, extra)
    }

    override fun setDataSource(path: String) {
        lastSetDataSource = path
    }

    override fun prepareAsync() {}

    override fun start() {
        isPlaying = true
        startCount++
    }

    override fun pause() {
        isPlaying = false
        pauseCount++
    }

    override fun stop() {
        isPlaying = false
        stopCount++
    }

    override fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs
    }

    override fun reset() {
        resetCount++
        isPlaying = false
    }

    override fun release() {
        releaseCount++
        isPlaying = false
    }

    override fun setVolume(volume: Float) {
        lastVolume = volume
    }

    override fun setOnPreparedListener(listener: () -> Unit) {
        onPreparedListener = listener
    }

    override fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }

    override fun setOnErrorListener(listener: (what: Int, extra: Int) -> Boolean) {
        onErrorListener = listener
    }
}
