package com.Hrshd1eux.filemanager.feature.viewer.video.util

import android.media.MediaMetadataRetriever
import com.Hrshd1eux.filemanager.feature.viewer.video.model.VideoMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lightweight metadata extractor leveraging Android's built-in [MediaMetadataRetriever].
 */
object VideoMetadataExtractor {

    suspend fun extractMetadata(
        filePath: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): VideoMetadata? = withContext(dispatcher) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return@withContext null
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L

            VideoMetadata(
                width = width,
                height = height,
                durationMs = duration,
                rotationDegrees = rotation,
                mimeType = mimeType,
                bitrate = bitrate
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
