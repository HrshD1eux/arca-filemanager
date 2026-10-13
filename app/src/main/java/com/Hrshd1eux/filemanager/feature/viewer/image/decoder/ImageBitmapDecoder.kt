package com.Hrshd1eux.filemanager.feature.viewer.image.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.Hrshd1eux.filemanager.feature.viewer.image.model.ImageDimensions
import com.Hrshd1eux.filemanager.feature.viewer.image.util.ImageDimensionsCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Interface defining image inspection and subsampled bitmap decoding.
 * Facilitates decoupling from Android platform stubs in unit tests.
 */
interface ImageBitmapDecoder {
    suspend fun inspectDimensions(filePath: String): ImageDimensions?
    suspend fun decodeBitmap(filePath: String, maxDimension: Int = 2048): Result<Bitmap>
}

/**
 * Production Android implementation utilizing [BitmapFactory] with subsampling
 * ([BitmapFactory.Options.inSampleSize]), [Bitmap.Config.RGB_565] for low memory footprint,
 * [ExifInterface] orientation correction, and OOM resilience.
 */
class AndroidImageBitmapDecoder(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ImageBitmapDecoder {

    override suspend fun inspectDimensions(filePath: String): ImageDimensions? = withContext(dispatcher) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return@withContext null
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeFile(filePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return@withContext null
        }

        val orientationDegrees = runCatching {
            val exif = ExifInterface(filePath)
            val tag = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ImageDimensionsCalculator.exifOrientationToDegrees(tag)
        }.getOrDefault(0)

        ImageDimensions(
            width = options.outWidth,
            height = options.outHeight,
            orientationDegrees = orientationDegrees
        )
    }

    override suspend fun decodeBitmap(filePath: String, maxDimension: Int): Result<Bitmap> = withContext(dispatcher) {
        runCatching {
            val file = File(filePath)
            if (!file.exists() || !file.canRead() || file.length() == 0L) {
                error("Image file does not exist, cannot be read, or is empty: $filePath")
            }

            val dims = inspectDimensions(filePath)
                ?: error("Corrupted or unsupported image file: ${file.name}")

            var sampleSize = ImageDimensionsCalculator.calculateInSampleSizeForMaxDimension(
                rawWidth = dims.width,
                rawHeight = dims.height,
                maxDimension = maxDimension
            )

            var bitmap: Bitmap? = null

            // Memory-bounded decode with automatic fallback on memory pressure
            for (attempt in 1..2) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inSampleSize = sampleSize
                    // Use RGB_565 for JPEG/camera photos to halve RAM consumption; ARGB_8888 for PNG/WebP transparency
                    inPreferredConfig = if (file.extension.equals("png", ignoreCase = true) ||
                        file.extension.equals("webp", ignoreCase = true)) {
                        Bitmap.Config.ARGB_8888
                    } else {
                        Bitmap.Config.RGB_565
                    }
                }

                try {
                    bitmap = BitmapFactory.decodeFile(filePath, options)
                    if (bitmap != null) break
                } catch (oom: OutOfMemoryError) {
                    System.gc()
                    sampleSize *= 2
                    if (attempt == 2) {
                        throw IllegalStateException("Insufficient memory to decode image ${file.name}", oom)
                    }
                }
            }

            val rawBitmap = bitmap ?: error("Failed to decode image pixels for ${file.name}")

            // Correct EXIF orientation rotation if necessary
            if (dims.orientationDegrees != 0) {
                val matrix = Matrix().apply {
                    postRotate(dims.orientationDegrees.toFloat())
                }
                val rotatedBitmap = Bitmap.createBitmap(
                    rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                )
                if (rotatedBitmap != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotatedBitmap
            } else {
                rawBitmap
            }
        }
    }
}
