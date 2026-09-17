package com.Hrshd1eux.filemanager.core.storage

import com.Hrshd1eux.filemanager.core.storage.model.ArchiveFormat
import com.Hrshd1eux.filemanager.core.storage.model.FileType
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypeResolverTest {

    @Test
    fun resolveType_standardExtensions_mapsAccurately() {
        // Images (.jpg -> image)
        assertEquals(FileType.IMAGE, FileTypeResolver.resolveType("photo.jpg"))
        assertEquals(FileType.IMAGE, FileTypeResolver.resolveType("vacation.jpeg"))
        assertEquals(FileType.IMAGE, FileTypeResolver.resolveType("screenshot.png"))
        assertEquals(FileType.IMAGE, FileTypeResolver.resolveType("banner.webp"))
        assertEquals(FileType.IMAGE, FileTypeResolver.resolveType("raw_shot.heic"))

        // Documents (.pdf -> document)
        assertEquals(FileType.DOCUMENT, FileTypeResolver.resolveType("contract.pdf"))
        assertEquals(FileType.DOCUMENT, FileTypeResolver.resolveType("report.docx"))
        assertEquals(FileType.DOCUMENT, FileTypeResolver.resolveType("sheet.xlsx"))
        assertEquals(FileType.DOCUMENT, FileTypeResolver.resolveType("book.epub"))

        // Archives (.zip -> archive)
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("backup.zip"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("data.rar"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("bundle.7z"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("archive.tar.gz"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("kernel.tar.xz"))

        // Packages (.apk -> package)
        assertEquals(FileType.PACKAGE, FileTypeResolver.resolveType("app-release.apk"))
        assertEquals(FileType.PACKAGE, FileTypeResolver.resolveType("bundle.aab"))
        assertEquals(FileType.PACKAGE, FileTypeResolver.resolveType("game.xapk"))

        // Videos (.mp4 -> video)
        assertEquals(FileType.VIDEO, FileTypeResolver.resolveType("clip.mp4"))
        assertEquals(FileType.VIDEO, FileTypeResolver.resolveType("movie.mkv"))
        assertEquals(FileType.VIDEO, FileTypeResolver.resolveType("sample.webm"))

        // Audio
        assertEquals(FileType.AUDIO, FileTypeResolver.resolveType("song.mp3"))
        assertEquals(FileType.AUDIO, FileTypeResolver.resolveType("track.flac"))
        assertEquals(FileType.AUDIO, FileTypeResolver.resolveType("voice.m4a"))

        // Code
        assertEquals(FileType.CODE, FileTypeResolver.resolveType("MainActivity.kt"))
        assertEquals(FileType.CODE, FileTypeResolver.resolveType("script.py"))
        assertEquals(FileType.CODE, FileTypeResolver.resolveType("index.html"))

        // Text
        assertEquals(FileType.TEXT, FileTypeResolver.resolveType("notes.txt"))
        assertEquals(FileType.TEXT, FileTypeResolver.resolveType("README.md"))
    }

    @Test
    fun resolveArchiveFormat_detectsFormatsForUpcomingArchiveEngine() {
        assertEquals(ArchiveFormat.ZIP, FileTypeResolver.resolveArchiveFormat("project.zip"))
        assertEquals(ArchiveFormat.TAR, FileTypeResolver.resolveArchiveFormat("backup.tar"))
        assertEquals(ArchiveFormat.TAR_GZ, FileTypeResolver.resolveArchiveFormat("source.tar.gz"))
        assertEquals(ArchiveFormat.TAR_GZ, FileTypeResolver.resolveArchiveFormat("source.tgz"))
        assertEquals(ArchiveFormat.TAR_BZ2, FileTypeResolver.resolveArchiveFormat("kernel.tar.bz2"))
        assertEquals(ArchiveFormat.TAR_XZ, FileTypeResolver.resolveArchiveFormat("dist.tar.xz"))
        assertEquals(ArchiveFormat.SEVEN_ZIP, FileTypeResolver.resolveArchiveFormat("data.7z"))
        assertEquals(ArchiveFormat.RAR, FileTypeResolver.resolveArchiveFormat("archive.rar"))
        assertEquals(ArchiveFormat.ISO, FileTypeResolver.resolveArchiveFormat("ubuntu.iso"))

        assertTrue(FileTypeResolver.canPreviewArchive("project.zip"))
        assertTrue(FileTypeResolver.canPreviewArchive("source.tar.gz"))
        assertTrue(FileTypeResolver.canPreviewArchive("data.7z"))
        assertTrue(FileTypeResolver.canPreviewArchive("archive.rar"))
        assertFalse(FileTypeResolver.canPreviewArchive("image.jpg"))
    }

    @Test
    fun isArchive_accuratelyDetectsArchives() {
        assertTrue(FileTypeResolver.isArchive("archive.zip"))
        assertTrue(FileTypeResolver.isArchive("archive.rar"))
        assertTrue(FileTypeResolver.isArchive("archive.7z"))
        assertTrue(FileTypeResolver.isArchive("archive.tar.gz"))
        assertTrue(FileTypeResolver.isArchive("archive.tar.bz2"))
        assertTrue(FileTypeResolver.isArchive("archive.tar.xz"))
        assertTrue(FileTypeResolver.isArchive("archive.tgz"))

        assertTrue(FileTypeResolver.isRar("archive.rar"))
        assertTrue(FileTypeResolver.isRar("FILE.RAR"))
        assertFalse(FileTypeResolver.isRar("archive.zip"))

        assertFalse(FileTypeResolver.isArchive("document.pdf"))
        assertFalse(FileTypeResolver.isArchive("image.png"))
        assertFalse(FileTypeResolver.isArchive("video.mp4"))
    }

    @Test
    fun isPackage_accuratelyDetectsPackages() {
        assertTrue(FileTypeResolver.isPackage("arca.apk"))
        assertTrue(FileTypeResolver.isPackage("release.aab"))
        assertTrue(FileTypeResolver.isPackage("game.xapk"))

        assertFalse(FileTypeResolver.isPackage("notes.txt"))
    }

    @Test
    fun isMedia_accuratelyDetectsMedia() {
        assertTrue(FileTypeResolver.isMedia("photo.jpg"))
        assertTrue(FileTypeResolver.isMedia("movie.mkv"))
        assertTrue(FileTypeResolver.isMedia("song.mp3"))

        assertFalse(FileTypeResolver.isMedia("contract.pdf"))
        assertFalse(FileTypeResolver.isMedia("backup.zip"))
    }

    @Test
    fun resolveMimeType_compoundArchives_resolvesAccurately() {
        assertEquals("application/gzip", FileTypeResolver.resolveMimeType("archive.tar.gz"))
        assertEquals("application/x-bzip2", FileTypeResolver.resolveMimeType("archive.tar.bz2"))
        assertEquals("application/x-xz", FileTypeResolver.resolveMimeType("archive.tar.xz"))
        assertEquals("application/pdf", FileTypeResolver.resolveMimeType("document.pdf"))
        assertEquals("application/zip", FileTypeResolver.resolveMimeType("files.zip"))
    }

    @Test
    fun resolveFromMimeFallback_mapsCorrectly() {
        assertEquals(FileType.IMAGE, FileTypeResolver.resolveType("unknown_file", "image/png"))
        assertEquals(FileType.DOCUMENT, FileTypeResolver.resolveType("unknown_file", "application/pdf"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("unknown_file", "application/zip"))
    }
}
