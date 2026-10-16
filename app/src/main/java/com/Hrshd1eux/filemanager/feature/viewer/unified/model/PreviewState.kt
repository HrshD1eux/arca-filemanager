package com.Hrshd1eux.filemanager.feature.viewer.unified.model

import android.graphics.Bitmap
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioMetadata
import com.Hrshd1eux.filemanager.feature.viewer.video.model.VideoMetadata
import java.io.File

/**
 * State representation for the unified preview content.
 */
sealed interface PreviewState {
    val file: File

    data class Loading(
        override val file: File
    ) : PreviewState

    data class ImageReady(
        override val file: File,
        val dimensions: String,
        val sizeBytes: Long
    ) : PreviewState

    data class VideoReady(
        override val file: File,
        val metadata: VideoMetadata?
    ) : PreviewState

    data class AudioReady(
        override val file: File,
        val metadata: AudioMetadata?
    ) : PreviewState

    data class TextReady(
        override val file: File,
        val lines: List<String>,
        val isTruncated: Boolean,
        val totalLines: Int,
        val totalBytes: Long
    ) : PreviewState

    data class PdfReady(
        override val file: File,
        val pageCount: Int,
        val currentPageIndex: Int,
        val currentPageBitmap: Bitmap?
    ) : PreviewState

    data class Unsupported(
        override val file: File,
        val mimeType: String,
        val reason: String = "No in-app preview available for this format."
    ) : PreviewState

    data class Error(
        override val file: File,
        val message: String
    ) : PreviewState
}
