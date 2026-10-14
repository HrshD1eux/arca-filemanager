package com.Hrshd1eux.filemanager.feature.viewer.audio.player

import android.content.Context
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioMetadata
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioPlaybackState
import com.Hrshd1eux.filemanager.feature.viewer.audio.util.AudioMetadataExtractor
import com.Hrshd1eux.filemanager.feature.viewer.audio.util.AudioTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Predictable in-app Audio Playback Controller.
 * Manages playback state, playlist queue, timeline polling, audio focus, headphone disconnects,
 * and lifecycle events. Keeps background playback out of scope.
 */
class AudioPlaybackController(
    private val context: Context? = null,
    private val engine: AudioPlayerEngine = MediaPlayerAudioEngine(),
    customAudioFocusHelper: AudioFocusHelper? = null,
    customBecomingNoisyReceiver: BecomingNoisyReceiver? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    private val audioFocusHelper: AudioFocusHelper? = customAudioFocusHelper ?: context?.let {
        AudioFocusHelper(
            context = it,
            onFocusLost = { isTransient, canDuck ->
                onAudioFocusLost(isTransient, canDuck)
            },
            onFocusGained = {
                onAudioFocusGained()
            }
        )
    }

    private val becomingNoisyReceiver: BecomingNoisyReceiver? = customBecomingNoisyReceiver ?: context?.let {
        BecomingNoisyReceiver(
            context = it,
            onBecomingNoisy = {
                onBecomingNoisy()
            }
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var tickerJob: Job? = null

    private val _currentTrack = MutableStateFlow<StorageNode?>(null)
    val currentTrack: StateFlow<StorageNode?> = _currentTrack.asStateFlow()

    private val _playlist = MutableStateFlow<List<StorageNode>>(emptyList())
    val playlist: StateFlow<List<StorageNode>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _playbackState = MutableStateFlow<AudioPlaybackState>(AudioPlaybackState.Idle)
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _metadata = MutableStateFlow<AudioMetadata?>(null)
    val metadata: StateFlow<AudioMetadata?> = _metadata.asStateFlow()

    private val _artworkBytes = MutableStateFlow<ByteArray?>(null)
    val artworkBytes: StateFlow<ByteArray?> = _artworkBytes.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    init {
        engine.setOnPreparedListener {
            val track = _currentTrack.value ?: return@setOnPreparedListener
            val duration = engine.durationMs.coerceAtLeast(0L)
            _durationMs.value = duration
            engine.start()
            _playbackState.value = AudioPlaybackState.Playing(track, 0L, duration)
            startTicker()
        }

        engine.setOnCompletionListener {
            val track = _currentTrack.value ?: return@setOnCompletionListener
            val duration = _durationMs.value
            stopTicker()
            _playbackState.value = AudioPlaybackState.Completed(track, duration)
            _currentPositionMs.value = duration

            // Auto-advance to next track in playlist if available
            val nextIdx = _currentIndex.value + 1
            if (nextIdx in _playlist.value.indices) {
                playTrackAt(nextIdx)
            } else {
                becomingNoisyReceiver?.unregister()
                audioFocusHelper?.abandonFocus()
            }
        }

        engine.setOnErrorListener { what, extra ->
            val track = _currentTrack.value ?: StorageNode(id = "unknown", name = "audio", path = "", isDirectory = false)
            stopTicker()
            becomingNoisyReceiver?.unregister()
            audioFocusHelper?.abandonFocus()
            val errorMsg = when (what) {
                -1004 -> "Unsupported audio format"
                -1007 -> "Damaged or corrupted audio stream"
                -110 -> "Playback timed out"
                else -> "Playback error ($what, $extra)"
            }
            _playbackState.value = AudioPlaybackState.Error(track, errorMsg)
            true
        }
    }

    /**
     * Start playback of a selected track within a playlist.
     */
    fun playTrack(track: StorageNode, playlist: List<StorageNode> = listOf(track)) {
        val sanitizedPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
        val index = sanitizedPlaylist.indexOfFirst { it.path == track.path }.let {
            if (it >= 0) it else 0
        }

        _playlist.value = sanitizedPlaylist
        _currentIndex.value = index
        playTrackAt(index)
    }

    /**
     * Plays track at specific index in the current playlist.
     */
    fun playTrackAt(index: Int) {
        val list = _playlist.value
        if (index !in list.indices) return

        val track = list[index]
        _currentIndex.value = index
        _currentTrack.value = track
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _playbackState.value = AudioPlaybackState.Preparing(track)

        stopTicker()

        // Extract metadata and artwork
        val extraction = AudioMetadataExtractor.extract(track.path)
        _metadata.value = extraction.metadata
        _artworkBytes.value = extraction.artworkBytes
        if (extraction.metadata.durationMs > 0L) {
            _durationMs.value = extraction.metadata.durationMs
        }

        // Request audio focus
        val focusGranted = audioFocusHelper?.requestFocus() ?: true
        if (!focusGranted) {
            _playbackState.value = AudioPlaybackState.Paused(track, 0L, _durationMs.value)
            return
        }

        becomingNoisyReceiver?.register()

        try {
            engine.reset()
            engine.setDataSource(track.path)
            engine.prepareAsync()
        } catch (e: Exception) {
            _playbackState.value = AudioPlaybackState.Error(track, "Cannot open file: ${e.localizedMessage ?: "Unknown error"}")
            becomingNoisyReceiver?.unregister()
            audioFocusHelper?.abandonFocus()
        }
    }

    fun play() {
        val track = _currentTrack.value ?: return
        val duration = _durationMs.value

        val focusGranted = audioFocusHelper?.requestFocus() ?: true
        if (!focusGranted) return

        becomingNoisyReceiver?.register()
        engine.start()
        _playbackState.value = AudioPlaybackState.Playing(track, _currentPositionMs.value, duration)
        startTicker()
    }

    fun pause() {
        val track = _currentTrack.value ?: return
        stopTicker()
        engine.pause()
        _playbackState.value = AudioPlaybackState.Paused(track, _currentPositionMs.value, _durationMs.value)
        becomingNoisyReceiver?.unregister()
        audioFocusHelper?.abandonFocus()
    }

    fun togglePlayPause() {
        when (_playbackState.value) {
            is AudioPlaybackState.Playing -> pause()
            is AudioPlaybackState.Paused -> play()
            is AudioPlaybackState.Completed -> {
                seekTo(0L)
                play()
            }
            else -> Unit
        }
    }

    fun seekTo(positionMs: Long) {
        val duration = _durationMs.value
        val clamped = AudioTimeFormatter.clampPosition(positionMs, duration)
        _currentPositionMs.value = clamped
        engine.seekTo(clamped)

        val track = _currentTrack.value
        if (track != null) {
            when (val state = _playbackState.value) {
                is AudioPlaybackState.Playing -> {
                    _playbackState.value = AudioPlaybackState.Playing(track, clamped, duration)
                }
                is AudioPlaybackState.Paused -> {
                    _playbackState.value = AudioPlaybackState.Paused(track, clamped, duration)
                }
                is AudioPlaybackState.Completed -> {
                    _playbackState.value = AudioPlaybackState.Paused(track, clamped, duration)
                }
                else -> Unit
            }
        }
    }

    fun skipForward(seconds: Int = 10) {
        val target = _currentPositionMs.value + (seconds * 1000L)
        seekTo(target)
    }

    fun skipBackward(seconds: Int = 10) {
        val target = _currentPositionMs.value - (seconds * 1000L)
        seekTo(target)
    }

    fun playNext() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val nextIndex = _currentIndex.value + 1
        if (nextIndex in list.indices) {
            playTrackAt(nextIndex)
        }
    }

    fun playPrevious() {
        val list = _playlist.value
        if (list.isEmpty()) return

        // If played more than 3 seconds, rewind to start of current track first
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = _currentIndex.value - 1
        if (prevIndex in list.indices) {
            playTrackAt(prevIndex)
        } else {
            seekTo(0L)
        }
    }

    /**
     * Stops playback and dismisses player completely.
     */
    fun stopAndDismiss() {
        stopTicker()
        becomingNoisyReceiver?.unregister()
        audioFocusHelper?.abandonFocus()
        engine.stop()
        engine.reset()

        _currentTrack.value = null
        _playlist.value = emptyList()
        _currentIndex.value = -1
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _metadata.value = null
        _artworkBytes.value = null
        _isExpanded.value = false
        _playbackState.value = AudioPlaybackState.Idle
    }

    fun expandPlayer() {
        _isExpanded.value = true
    }

    fun collapsePlayer() {
        _isExpanded.value = false
    }

    /**
     * Removes track from playlist (e.g. file deleted).
     */
    fun removeTrack(track: StorageNode) {
        val currentList = _playlist.value
        val index = currentList.indexOfFirst { it.path == track.path }
        if (index < 0) return

        val newList = currentList.toMutableList().apply { removeAt(index) }
        _playlist.value = newList

        if (_currentTrack.value?.path == track.path) {
            if (newList.isNotEmpty()) {
                val nextIdx = index.coerceAtMost(newList.size - 1)
                playTrackAt(nextIdx)
            } else {
                stopAndDismiss()
            }
        } else {
            // Adjust current index
            val cur = _currentTrack.value
            _currentIndex.value = if (cur != null) newList.indexOfFirst { it.path == cur.path } else -1
        }
    }

    /**
     * Invoked when headphones / Bluetooth disconnects.
     */
    fun onBecomingNoisy() {
        if (_playbackState.value is AudioPlaybackState.Playing) {
            pause()
        }
    }

    /**
     * Invoked on audio focus loss or gain.
     */
    fun onAudioFocusLost(isTransient: Boolean, canDuck: Boolean) {
        if (_playbackState.value is AudioPlaybackState.Playing) {
            if (canDuck) {
                engine.setVolume(0.2f)
            } else {
                pause()
            }
        }
    }

    fun onAudioFocusGained() {
        engine.setVolume(1.0f)
    }

    /**
     * Stop playback when required by lifecycle.
     * Keep background playback out of scope.
     */
    fun onAppBackgrounded() {
        if (_playbackState.value is AudioPlaybackState.Playing) {
            pause()
        }
    }

    fun release() {
        stopTicker()
        becomingNoisyReceiver?.unregister()
        audioFocusHelper?.abandonFocus()
        engine.release()
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                if (engine.isPlaying) {
                    val pos = engine.currentPositionMs
                    _currentPositionMs.value = pos
                    val track = _currentTrack.value
                    if (track != null) {
                        _playbackState.value = AudioPlaybackState.Playing(track, pos, _durationMs.value)
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    companion object {
        @Volatile
        private var instance: AudioPlaybackController? = null

        fun getInstance(context: Context): AudioPlaybackController {
            return instance ?: synchronized(this) {
                instance ?: AudioPlaybackController(
                    context = context.applicationContext
                ).also { instance = it }
            }
        }

        /**
         * For unit tests only.
         */
        fun setInstanceForTesting(controller: AudioPlaybackController?) {
            instance = controller
        }
    }
}
