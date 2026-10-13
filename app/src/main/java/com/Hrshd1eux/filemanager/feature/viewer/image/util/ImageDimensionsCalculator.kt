package com.Hrshd1eux.filemanager.feature.viewer.image.util

import com.Hrshd1eux.filemanager.feature.viewer.image.model.ImageDimensions

/**
 * Pure Kotlin mathematical helper for memory-safe image dimension calculations,
 * subsampling factors, and orientation rotations.
 */
object ImageDimensionsCalculator {

    /**
     * Calculates the largest power-of-two [inSampleSize] value to ensure that
     * the decoded image dimensions do not exceed [reqWidth] and [reqHeight].
     */
    fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) {
            return 1
        }

        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Calculates the power-of-two [inSampleSize] so that the maximum dimension
     * does not exceed [maxDimension].
     */
    fun calculateInSampleSizeForMaxDimension(rawWidth: Int, rawHeight: Int, maxDimension: Int): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || maxDimension <= 0) {
            return 1
        }

        var inSampleSize = 1
        val largestDim = maxOf(rawWidth, rawHeight)
        while ((largestDim / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Estimates RAM footprint in bytes of the decoded bitmap.
     * Default [bytesPerPixel] is 2 for [android.graphics.Bitmap.Config.RGB_565], or 4 for ARGB_8888.
     */
    fun estimateMemoryFootprintBytes(
        width: Int,
        height: Int,
        inSampleSize: Int = 1,
        bytesPerPixel: Int = 2
    ): Long {
        if (width <= 0 || height <= 0 || inSampleSize <= 0) return 0L
        val decodedW = (width / inSampleSize).coerceAtLeast(1)
        val decodedH = (height / inSampleSize).coerceAtLeast(1)
        return decodedW.toLong() * decodedH.toLong() * bytesPerPixel.toLong()
    }

    /**
     * Swaps dimensions if rotated by 90° or 270°.
     */
    fun calculateEffectiveDimensions(width: Int, height: Int, orientationDegrees: Int): Pair<Int, Int> {
        return if (orientationDegrees == 90 || orientationDegrees == 270) {
            Pair(height, width)
        } else {
            Pair(width, height)
        }
    }

    /**
     * Converts raw EXIF orientation tag value into degrees (0, 90, 180, 270).
     */
    fun exifOrientationToDegrees(exifOrientation: Int): Int {
        return when (exifOrientation) {
            6 -> 90   // ExifInterface.ORIENTATION_ROTATE_90
            3 -> 180  // ExifInterface.ORIENTATION_ROTATE_180
            8 -> 270  // ExifInterface.ORIENTATION_ROTATE_270
            else -> 0
        }
    }
}
