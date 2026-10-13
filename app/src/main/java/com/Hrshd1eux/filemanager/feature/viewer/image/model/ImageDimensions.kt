package com.Hrshd1eux.filemanager.feature.viewer.image.model

/**
 * Encapsulates raw and visual dimensions of an image, taking EXIF orientation into account.
 */
data class ImageDimensions(
    val width: Int,
    val height: Int,
    val orientationDegrees: Int = 0
) {
    /**
     * Display width after applying EXIF rotation (swapped if 90° or 270°).
     */
    val visualWidth: Int
        get() = if (orientationDegrees == 90 || orientationDegrees == 270) height else width

    /**
     * Display height after applying EXIF rotation (swapped if 90° or 270°).
     */
    val visualHeight: Int
        get() = if (orientationDegrees == 90 || orientationDegrees == 270) width else height

    /**
     * Formatted string such as "3840 × 2160".
     */
    val formatted: String
        get() = if (width > 0 && height > 0) "${visualWidth} × ${visualHeight}" else "Unknown"

    val isValid: Boolean
        get() = width > 0 && height > 0
}
