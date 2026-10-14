package com.Hrshd1eux.filemanager.feature.viewer.audio.model

/**
 * Metadata extracted from an audio file.
 */
data class AudioMetadata(
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String? = null,
    val durationMs: Long = 0L,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val mimeType: String? = null,
    val hasEmbeddedPicture: Boolean = false
)
