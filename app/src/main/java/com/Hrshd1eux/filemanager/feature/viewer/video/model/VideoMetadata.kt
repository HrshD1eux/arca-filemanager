package com.Hrshd1eux.filemanager.feature.viewer.video.model

import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoTimeFormatter

/**
 * Encapsulates video track metadata including visual dimensions, orientation, duration, and bitrate.
 */
data class VideoMetadata(
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val rotationDegrees: Int = 0,
    val mimeType: String? = null,
    val bitrate: Long = 0L
) {
    /**
     * Display width factoring in track rotation tags (e.g. 90° or 270° from smartphone cameras).
     */
    val visualWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    /**
     * Display height factoring in track rotation tags.
     */
    val visualHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

    /**
     * Aspect ratio for video rendering (width / height).
     */
    val aspectRatio: Float
        get() = if (visualWidth > 0 && visualHeight > 0) {
            visualWidth.toFloat() / visualHeight.toFloat()
        } else {
            16f / 9f
        }

    /**
     * Formatted string such as "1920 × 1080".
     */
    val formattedDimensions: String
        get() = if (visualWidth > 0 && visualHeight > 0) "${visualWidth} × ${visualHeight}" else "Unknown"

    /**
     * Formatted string such as "04:15".
     */
    val formattedDuration: String
        get() = VideoTimeFormatter.formatDuration(durationMs)
}
