package com.Hrshd1eux.filemanager.core.storage.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standardized formatters for file sizes, byte counts, and timestamps.
 */
object FileFormatter {

    private const val KB = 1024L
    private const val MB = KB * 1024L
    private const val GB = MB * 1024L
    private const val TB = GB * 1024L

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return when {
            bytes < KB -> "$bytes B"
            bytes < MB -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / KB)
            bytes < GB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / MB)
            bytes < TB -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / GB)
            else -> String.format(Locale.US, "%.1f TB", bytes.toDouble() / TB)
        }
    }

    fun formatExactBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 bytes"
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(bytes)
        return "$formatted bytes"
    }

    fun formatDateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Unknown"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    fun formatDateShort(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Unknown"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    fun getCategoryDescription(mimeType: String?, isDirectory: Boolean): String {
        if (isDirectory) return "Folder"
        if (mimeType == null) return "Binary File"

        return when {
            mimeType.startsWith("image/") -> "Image (${mimeType.substringAfter('/')})"
            mimeType.startsWith("video/") -> "Video (${mimeType.substringAfter('/')})"
            mimeType.startsWith("audio/") -> "Audio (${mimeType.substringAfter('/')})"
            mimeType == "application/pdf" -> "PDF Document"
            mimeType.startsWith("text/") -> "Text Document"
            mimeType.contains("zip") || mimeType.contains("7z") || mimeType.contains("tar") || mimeType.contains("rar") -> "Compressed Archive"
            mimeType.contains("android.package-archive") -> "Android App Package (APK)"
            else -> mimeType
        }
    }
}
