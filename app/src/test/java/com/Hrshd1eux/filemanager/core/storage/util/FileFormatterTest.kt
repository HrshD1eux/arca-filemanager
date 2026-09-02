package com.Hrshd1eux.filemanager.core.storage.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileFormatterTest {

    @Test
    fun formatFileSize_formatsCorrectUnits() {
        assertEquals("0 B", FileFormatter.formatFileSize(0L))
        assertEquals("0 B", FileFormatter.formatFileSize(-10L))
        assertEquals("500 B", FileFormatter.formatFileSize(500L))
        assertEquals("1.0 KB", FileFormatter.formatFileSize(1024L))
        assertEquals("1.5 KB", FileFormatter.formatFileSize(1536L))
        assertEquals("12.4 MB", FileFormatter.formatFileSize(13002342L))
        assertEquals("1.5 GB", FileFormatter.formatFileSize(1610612736L))
        assertEquals("2.0 TB", FileFormatter.formatFileSize(2199023255552L))
    }

    @Test
    fun formatExactBytes_formatsWithCommas() {
        assertEquals("0 bytes", FileFormatter.formatExactBytes(0L))
        assertEquals("1,024 bytes", FileFormatter.formatExactBytes(1024L))
        assertEquals("13,002,342 bytes", FileFormatter.formatExactBytes(13002342L))
    }

    @Test
    fun getCategoryDescription_mapsCategoriesAccurately() {
        assertEquals("Folder", FileFormatter.getCategoryDescription("anything", isDirectory = true))
        assertEquals("PDF Document", FileFormatter.getCategoryDescription("application/pdf", isDirectory = false))
        assertEquals("Image (jpeg)", FileFormatter.getCategoryDescription("image/jpeg", isDirectory = false))
        assertEquals("Video (mp4)", FileFormatter.getCategoryDescription("video/mp4", isDirectory = false))
        assertEquals("Audio (mpeg)", FileFormatter.getCategoryDescription("audio/mpeg", isDirectory = false))
        assertEquals("Compressed Archive", FileFormatter.getCategoryDescription("application/zip", isDirectory = false))
        assertEquals("Android App Package (APK)", FileFormatter.getCategoryDescription("application/vnd.android.package-archive", isDirectory = false))
        assertEquals("Binary File", FileFormatter.getCategoryDescription(null, isDirectory = false))
    }
}
