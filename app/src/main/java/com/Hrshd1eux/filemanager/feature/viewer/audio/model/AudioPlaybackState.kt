package com.Hrshd1eux.filemanager.feature.viewer.audio.model

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

/**
 * Predictable playback states for the in-app Audio Player.
 */
sealed interface AudioPlaybackState {
    /** Player is stopped, dismissed, or not yet initialized. */
    data object Idle : AudioPlaybackState

    /** Preparing track for playback (data source configured, waiting for async prepare). */
    data class Preparing(val track: StorageNode) : AudioPlaybackState

    /** Actively playing audio. */
    data class Playing(
        val track: StorageNode,
        val positionMs: Long,
        val durationMs: Long
    ) : AudioPlaybackState

    /** Paused by user, audio focus interruption, or becoming noisy event. */
    data class Paused(
        val track: StorageNode,
        val positionMs: Long,
        val durationMs: Long
    ) : AudioPlaybackState

    /** Reached end of track. */
    data class Completed(
        val track: StorageNode,
        val durationMs: Long
    ) : AudioPlaybackState

    /** Playback error (e.g. damaged audio file or unsupported codec). */
    data class Error(
        val track: StorageNode,
        val message: String
    ) : AudioPlaybackState
}
