package com.Hrshd1eux.filemanager.core.thumbnail

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * High-performance, dual-layer (RAM + 100MB Disk) asynchronous thumbnail generator.
 *
 * Supported formats:
 * - Images (JPG, PNG, WebP, BMP, HEIC, etc.) via downsampled BitmapFactory
 * - Videos (MP4, MKV, AVI, etc.) via MediaMetadataRetriever frame extraction
 * - APK packages via PackageManager application icon extraction
 * - Audio with embedded album art via MediaMetadataRetriever
 * - PDF documents via Android's built-in PdfRenderer
 *
 * Employs a strict LRU Memory Cache to guarantee low RAM overhead and a persistent
 * 100 MB WebP disk cache to eliminate frame drops and CPU spikes on repeated scrolls.
 */
object ThumbnailLoader {

    private const val MAX_DISK_CACHE_BYTES = 100L * 1024L * 1024L // 100 MB disk cache limit
    private val diskCacheLock = Any()

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    // Allocate up to 1/8th of heap memory, capped between 8MB and 32MB
    private val cacheSize = (maxMemory / 8).coerceIn(8 * 1024, 32 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun loadThumbnail(
        context: Context,
        filePath: String,
        targetSize: Int = 160,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Bitmap? = withContext(dispatcher) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return@withContext null
        }

        val cacheKey = "$filePath@$targetSize"

        // 1. Layer 1: In-Memory LRU Cache lookup (fastest, zero I/O)
        synchronized(memoryCache) {
            val cached = memoryCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        // 2. Layer 2: Persistent Disk Cache lookup (avoids slow video decoding / re-read from flash)
        val diskCacheKey = hashKey("$filePath@$targetSize@${file.lastModified()}@${file.length()}")
        val diskCacheDir = File(context.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val diskCacheFile = File(diskCacheDir, "$diskCacheKey.webp")

        if (diskCacheFile.exists()) {
            val diskBitmap = runCatching {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(diskCacheFile.absolutePath, opts)
            }.getOrNull()

            if (diskBitmap != null) {
                synchronized(memoryCache) {
                    memoryCache.put(cacheKey, diskBitmap)
                }
                return@withContext diskBitmap
            }
        }

        // 3. Fallback to format-specific decoder
        val bitmap = runCatching {
            when {
                FileTypeResolver.isImage(file.name) -> {
                    decodeImageThumbnail(file, targetSize)
                }
                FileTypeResolver.isVideo(file.name) -> {
                    decodeVideoThumbnail(file, targetSize)
                }
                FileTypeResolver.isPackage(file.name) -> {
                    decodeApkIcon(context, file, targetSize)
                }
                FileTypeResolver.isAudio(file.name) -> {
                    decodeAudioAlbumArt(file, targetSize)
                }
                file.extension.equals("pdf", ignoreCase = true) -> {
                    decodePdfThumbnail(file, targetSize)
                }
                else -> null
            }
        }.getOrNull()

        if (bitmap != null) {
            synchronized(memoryCache) {
                memoryCache.put(cacheKey, bitmap)
            }
            saveToDiskCache(diskCacheFile, bitmap, diskCacheDir)
        }

        bitmap
    }

    private fun hashKey(input: String): String {
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrDefault(input.hashCode().toString())
    }

    private fun saveToDiskCache(cacheFile: File, bitmap: Bitmap, cacheDir: File) {
        synchronized(diskCacheLock) {
            runCatching {
                FileOutputStream(cacheFile).use { out ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
                    } else {
                        @Suppress("DEPRECATION")
                        bitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
                    }
                }
                pruneDiskCacheIfNeeded(cacheDir)
            }
        }
    }

    private fun pruneDiskCacheIfNeeded(cacheDir: File) {
        runCatching {
            val files = cacheDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize > MAX_DISK_CACHE_BYTES) {
                val sorted = files.sortedBy { it.lastModified() }
                for (oldFile in sorted) {
                    val len = oldFile.length()
                    if (oldFile.delete()) {
                        totalSize -= len
                        if (totalSize <= (MAX_DISK_CACHE_BYTES * 0.8).toLong()) break
                    }
                }
            }
        }
    }

    private fun decodeImageThumbnail(file: File, targetSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, targetSize, targetSize)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeVideoThumbnail(file: File, targetSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val rawBitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            rawBitmap?.let { scaleBitmap(it, targetSize) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeAudioAlbumArt(file: File, targetSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, targetSize, targetSize)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeApkIcon(context: Context, file: File, targetSize: Int): Bitmap? {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_ACTIVITIES) ?: return null
            val appInfo = info.applicationInfo ?: return null
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            val iconDrawable = appInfo.loadIcon(pm)
            drawableToBitmap(iconDrawable, targetSize)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodePdfThumbnail(file: File, targetSize: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                page = renderer.openPage(0)
                val width = targetSize
                val height = (targetSize * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { page?.close() }
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = maxOf(width, height)
        if (maxDim <= targetSize) return bitmap

        val scale = targetSize.toFloat() / maxDim
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun drawableToBitmap(drawable: Drawable, targetSize: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return scaleBitmap(drawable.bitmap, targetSize)
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else targetSize
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else targetSize
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return scaleBitmap(bitmap, targetSize)
    }

    fun clearCache() {
        synchronized(memoryCache) {
            memoryCache.evictAll()
        }
    }
}
