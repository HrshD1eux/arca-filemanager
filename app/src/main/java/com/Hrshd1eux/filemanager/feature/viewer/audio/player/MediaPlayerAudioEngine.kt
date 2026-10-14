package com.Hrshd1eux.filemanager.feature.viewer.audio.player

import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Concrete implementation of [AudioPlayerEngine] backed by Android platform [MediaPlayer].
 */
class MediaPlayerAudioEngine : AudioPlayerEngine {

    private var mediaPlayer: MediaPlayer? = null
    private var onPreparedCallback: (() -> Unit)? = null
    private var onCompletionCallback: (() -> Unit)? = null
    private var onErrorCallback: ((Int, Int) -> Boolean)? = null

    private fun ensurePlayer(): MediaPlayer {
        return mediaPlayer ?: MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { onPreparedCallback?.invoke() }
            setOnCompletionListener { onCompletionCallback?.invoke() }
            setOnErrorListener { _, what, extra ->
                onErrorCallback?.invoke(what, extra) ?: false
            }
        }.also { mediaPlayer = it }
    }

    override val isPlaying: Boolean
        get() = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    override val currentPositionMs: Long
        get() = runCatching { mediaPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: 0L }.getOrDefault(0L)

    override val durationMs: Long
        get() = runCatching { mediaPlayer?.duration?.toLong()?.coerceAtLeast(0L) ?: 0L }.getOrDefault(0L)

    override fun setDataSource(path: String) {
        val player = ensurePlayer()
        player.reset()
        player.setDataSource(path)
    }

    override fun prepareAsync() {
        runCatching {
            mediaPlayer?.prepareAsync()
        }
    }

    override fun start() {
        runCatching {
            mediaPlayer?.start()
        }
    }

    override fun pause() {
        runCatching {
            if (isPlaying) {
                mediaPlayer?.pause()
            }
        }
    }

    override fun stop() {
        runCatching {
            mediaPlayer?.stop()
        }
    }

    override fun seekTo(positionMs: Long) {
        runCatching {
            mediaPlayer?.seekTo(positionMs.toInt())
        }
    }

    override fun reset() {
        runCatching {
            mediaPlayer?.reset()
        }
    }

    override fun release() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        runCatching {
            mediaPlayer?.setVolume(clamped, clamped)
        }
    }

    override fun setOnPreparedListener(listener: () -> Unit) {
        this.onPreparedCallback = listener
    }

    override fun setOnCompletionListener(listener: () -> Unit) {
        this.onCompletionCallback = listener
    }

    override fun setOnErrorListener(listener: (what: Int, extra: Int) -> Boolean) {
        this.onErrorCallback = listener
    }
}
