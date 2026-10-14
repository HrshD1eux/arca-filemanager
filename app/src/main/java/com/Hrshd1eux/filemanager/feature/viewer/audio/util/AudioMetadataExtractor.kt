package com.Hrshd1eux.filemanager.feature.viewer.audio.util

import android.media.MediaMetadataRetriever
import android.os.Build
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioMetadata
import java.io.File

/**
 * Safely extracts ID3/tag metadata and artwork from audio files on Android.
 */
object AudioMetadataExtractor {

    data class ExtractionResult(
        val metadata: AudioMetadata,
        val artworkBytes: ByteArray?
    )

    fun extract(filePath: String): ExtractionResult {
        val file = File(filePath)
        val defaultTitle = file.nameWithoutExtension.ifBlank { file.name }
        val fallback = ExtractionResult(
            metadata = AudioMetadata(
                title = defaultTitle,
                artist = "Unknown Artist",
                durationMs = 0L
            ),
            artworkBytes = null
        )

        if (!file.exists() || !file.canRead()) {
            return fallback
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)

            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val title = if (!rawTitle.isNullOrBlank()) rawTitle.trim() else defaultTitle

            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val artist = if (!rawArtist.isNullOrBlank()) rawArtist.trim() else "Unknown Artist"

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
            val bitrateKbps = if (rawBitrate != null && rawBitrate > 0) rawBitrate / 1000 else null

            val sampleRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            } else {
                null
            }

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val artworkBytes = retriever.embeddedPicture

            ExtractionResult(
                metadata = AudioMetadata(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs.coerceAtLeast(0L),
                    bitrateKbps = bitrateKbps,
                    sampleRateHz = sampleRateHz,
                    mimeType = mimeType,
                    hasEmbeddedPicture = artworkBytes != null && artworkBytes.isNotEmpty()
                ),
                artworkBytes = artworkBytes
            )
        } catch (e: Exception) {
            fallback
        } finally {
            runCatching { retriever.release() }
        }
    }
}
