package com.Hrshd1eux.filemanager.feature.viewer.unified.loader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance PDF page renderer utilizing Android's platform [PdfRenderer].
 */
class PdfPreviewRenderer(
    private val file: File
) : AutoCloseable {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    val pageCount: Int
        get() = renderer?.pageCount ?: 0

    suspend fun open(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = pfd
            val r = PdfRenderer(pfd)
            renderer = r
            r.pageCount
        }
    }

    suspend fun renderPage(pageIndex: Int, maxDimension: Int = 2048): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val r = renderer ?: throw IllegalStateException("PdfRenderer not initialized")
            if (pageIndex !in 0 until r.pageCount) {
                throw IndexOutOfBoundsException("Page index $pageIndex out of bounds for pageCount ${r.pageCount}")
            }

            val page = r.openPage(pageIndex)
            try {
                val origWidth = page.width
                val origHeight = page.height

                // Calculate scaling maintaining aspect ratio
                val scale = if (origWidth > maxDimension || origHeight > maxDimension) {
                    val scaleX = maxDimension.toFloat() / origWidth
                    val scaleY = maxDimension.toFloat() / origHeight
                    minOf(scaleX, scaleY)
                } else {
                    1.5f // Slight supersampling for crisp reading
                }

                val renderWidth = (origWidth * scale).toInt().coerceAtLeast(1)
                val renderHeight = (origHeight * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        runCatching { renderer?.close() }
        runCatching { fileDescriptor?.close() }
        renderer = null
        fileDescriptor = null
    }
}
