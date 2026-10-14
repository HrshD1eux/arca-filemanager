package com.Hrshd1eux.filemanager.feature.viewer.audio.player

/**
 * Abstraction over the underlying audio playback engine.
 * Allows pure unit testing without Android platform framework classes.
 */
interface AudioPlayerEngine {
    val isPlaying: Boolean
    val currentPositionMs: Long
    val durationMs: Long

    fun setDataSource(path: String)
    fun prepareAsync()
    fun start()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun reset()
    fun release()
    fun setVolume(volume: Float)

    fun setOnPreparedListener(listener: () -> Unit)
    fun setOnCompletionListener(listener: () -> Unit)
    fun setOnErrorListener(listener: (what: Int, extra: Int) -> Boolean)
}
