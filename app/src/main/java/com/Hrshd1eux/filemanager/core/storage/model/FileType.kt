package com.Hrshd1eux.filemanager.core.storage.model

enum class FileType(val displayName: String) {
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCUMENT("Document"),
    ARCHIVE("Archive"),
    PACKAGE("Package"),
    CODE("Source Code"),
    TEXT("Plain Text"),
    FONT("Font"),
    UNKNOWN("Unknown");

    val isArchive: Boolean get() = this == ARCHIVE
    val isPackage: Boolean get() = this == PACKAGE
    val isMedia: Boolean get() = this in setOf(IMAGE, VIDEO, AUDIO)
    val isImage: Boolean get() = this == IMAGE
    val isVideo: Boolean get() = this == VIDEO
    val isAudio: Boolean get() = this == AUDIO
    val isDocument: Boolean get() = this in setOf(DOCUMENT, TEXT, CODE)
}

enum class ArchiveFormat(val displayName: String, val supportsStreamingPreview: Boolean) {
    ZIP("ZIP Archive", true),
    TAR("TAR Archive", true),
    TAR_GZ("GZ-Compressed TAR", true),
    TAR_BZ2("BZip2-Compressed TAR", true),
    TAR_XZ("XZ-Compressed TAR", true),
    SEVEN_ZIP("7-Zip Archive", true),
    RAR("RAR Archive (Read-Only)", true),
    GZIP("GZip Compressed File", false),
    BZIP2("BZip2 Compressed File", false),
    XZ("XZ Compressed File", false),
    ZSTD("Zstandard Compressed File", false),
    ISO("ISO Disc Image (Read-Only)", true),
    ARJ("ARJ Archive (Read-Only)", true),
    CPIO("CPIO Archive", true),
    AR("AR Archive (Read-Only)", true),
    DEB("Debian Package (Read-Only)", true),
    TAR_Z("Unix Compress TAR (Read-Only)", true),
    OTHER("Archive", false)
}
