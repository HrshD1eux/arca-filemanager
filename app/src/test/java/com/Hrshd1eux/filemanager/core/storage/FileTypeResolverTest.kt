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
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("legacy.arj"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("rootfs.cpio"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("libfoo.ar"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("data.tar.z"))

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
        assertEquals(ArchiveFormat.ARJ, FileTypeResolver.resolveArchiveFormat("legacy.arj"))
        assertEquals(ArchiveFormat.CPIO, FileTypeResolver.resolveArchiveFormat("rootfs.cpio"))
        assertEquals(ArchiveFormat.DEB, FileTypeResolver.resolveArchiveFormat("package.deb"))
        assertEquals(ArchiveFormat.AR, FileTypeResolver.resolveArchiveFormat("libfoo.a"))
        assertEquals(ArchiveFormat.AR, FileTypeResolver.resolveArchiveFormat("archive.ar"))
        assertEquals(ArchiveFormat.TAR_Z, FileTypeResolver.resolveArchiveFormat("data.tar.z"))
        assertEquals(ArchiveFormat.TAR_Z, FileTypeResolver.resolveArchiveFormat("data.taz"))

        assertTrue(FileTypeResolver.canPreviewArchive("project.zip"))
        assertTrue(FileTypeResolver.canPreviewArchive("source.tar.gz"))
        assertTrue(FileTypeResolver.canPreviewArchive("data.7z"))
        assertTrue(FileTypeResolver.canPreviewArchive("archive.rar"))
        assertTrue(FileTypeResolver.canPreviewArchive("ubuntu.iso"))
        assertTrue(FileTypeResolver.canPreviewArchive("legacy.arj"))
        assertTrue(FileTypeResolver.canPreviewArchive("rootfs.cpio"))
        assertTrue(FileTypeResolver.canPreviewArchive("package.deb"))
        assertTrue(FileTypeResolver.canPreviewArchive("libfoo.a"))
        assertTrue(FileTypeResolver.canPreviewArchive("archive.ar"))
        assertTrue(FileTypeResolver.canPreviewArchive("data.tar.z"))
        assertTrue(FileTypeResolver.canPreviewArchive("data.taz"))
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
        assertTrue(FileTypeResolver.isArchive("disc.iso"))
        assertTrue(FileTypeResolver.isArchive("legacy.arj"))
        assertTrue(FileTypeResolver.isArchive("rootfs.cpio"))
        assertTrue(FileTypeResolver.isArchive("package.deb"))
        assertTrue(FileTypeResolver.isArchive("archive.ar"))
        assertTrue(FileTypeResolver.isArchive("data.tar.z"))
        assertTrue(FileTypeResolver.isArchive("data.taz"))

        assertTrue(FileTypeResolver.isRar("archive.rar"))
        assertTrue(FileTypeResolver.isRar("FILE.RAR"))
        assertFalse(FileTypeResolver.isRar("archive.zip"))

        assertTrue(FileTypeResolver.isIso("disc.iso"))
        assertTrue(FileTypeResolver.isIso("IMAGE.ISO"))
        assertFalse(FileTypeResolver.isIso("archive.zip"))

        assertTrue(FileTypeResolver.isArj("legacy.arj"))
        assertTrue(FileTypeResolver.isArj("ARCHIVE.ARJ"))
        assertFalse(FileTypeResolver.isArj("archive.zip"))

        assertTrue(FileTypeResolver.isCpio("initramfs.cpio"))
        assertTrue(FileTypeResolver.isCpio("ROOTFS.CPIO"))
        assertFalse(FileTypeResolver.isCpio("archive.zip"))

        assertTrue(FileTypeResolver.isDeb("package.deb"))
        assertTrue(FileTypeResolver.isDeb("INSTALL.DEB"))
        assertFalse(FileTypeResolver.isDeb("archive.zip"))

        assertTrue(FileTypeResolver.isAr("lib.a"))
        assertTrue(FileTypeResolver.isAr("archive.ar"))
        assertTrue(FileTypeResolver.isAr("STATIC.A"))
        assertFalse(FileTypeResolver.isAr("archive.zip"))

        assertTrue(FileTypeResolver.isTarZ("data.tar.z"))
        assertTrue(FileTypeResolver.isTarZ("DATA.TAR.Z"))
        assertTrue(FileTypeResolver.isTarZ("data.taz"))
        assertTrue(FileTypeResolver.isTarZ("DATA.TAZ"))
        assertFalse(FileTypeResolver.isTarZ("archive.tar.gz"))

        // Split archives
        assertTrue(FileTypeResolver.isArchive("backup.7z.001"))
        assertTrue(FileTypeResolver.isArchive("backup.7z.002"))
        assertTrue(FileTypeResolver.isArchive("archive.001"))
        assertTrue(FileTypeResolver.isArchive("split.z01"))
        assertTrue(FileTypeResolver.isSplitArchive("backup.7z.001"))
        assertTrue(FileTypeResolver.isSplitArchive("archive.001"))
        assertTrue(FileTypeResolver.isSplitArchive("split.z01"))
        assertFalse(FileTypeResolver.isSplitArchive("backup.7z"))
        assertFalse(FileTypeResolver.isSplitArchive("archive.zip"))

        assertTrue(FileTypeResolver.is7z("backup.7z.001"))
        assertTrue(FileTypeResolver.is7z("archive.7z"))
        assertFalse(FileTypeResolver.is7z("backup.zip"))

        assertEquals(ArchiveFormat.SEVEN_ZIP, FileTypeResolver.resolveArchiveFormat("backup.7z.001"))
        assertEquals(ArchiveFormat.ZIP, FileTypeResolver.resolveArchiveFormat("backup.z01"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("backup.7z.001"))
        assertEquals(FileType.ARCHIVE, FileTypeResolver.resolveType("archive.001"))

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
