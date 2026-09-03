package com.Hrshd1eux.filemanager.core.archive.model

enum class ArchiveFormatOption(
    val id: String,
    val displayName: String,
    val defaultExtension: String
) {
    ZIP("zip", "ZIP", ".zip"),
    SEVEN_Z("7z", "7Z", ".7z"),
    TAR("tar", "TAR", ".tar"),
    TAR_GZ("tar_gz", "TAR.GZ", ".tar.gz"),
    TAR_XZ("tar_xz", "TAR.XZ", ".tar.xz"),
    TAR_BZ2("tar_bz2", "TAR.BZ2", ".tar.bz2");

    val isTarFamily: Boolean get() = this in setOf(TAR, TAR_GZ, TAR_XZ, TAR_BZ2)
    val is7z: Boolean get() = this == SEVEN_Z
    val isZip: Boolean get() = this == ZIP
}

enum class CompressionLevelPreset(val displayName: String, val level: Int) {
    STORE("Store", 0),
    FAST("Fast", 1),
    NORMAL("Normal", 6),
    MAXIMUM("Maximum", 9),
    ULTRA("Ultra", 9)
}

enum class CompressionMethodOption(val displayName: String) {
    DEFAULT("Default"),
    DEFLATE("Deflate"),
    LZMA2("LZMA2"),
    LZMA("LZMA"),
    BZIP2("BZip2"),
    COPY("Copy / Store")
}

data class CompressionProfile(
    val format: ArchiveFormatOption = ArchiveFormatOption.ZIP,
    val levelPreset: CompressionLevelPreset = CompressionLevelPreset.NORMAL,
    val compressionLevel: Int = 6,
    val method: CompressionMethodOption = CompressionMethodOption.DEFAULT,
    val threads: Int = 0, // 0 = Auto
    val isSolid: Boolean = true,
    val dictionarySizeMb: Int = 32
) {
    companion object {
        fun defaultFor(format: ArchiveFormatOption): CompressionProfile {
            return when (format) {
                ArchiveFormatOption.ZIP -> CompressionProfile(
                    format = format,
                    levelPreset = CompressionLevelPreset.NORMAL,
                    compressionLevel = 6,
                    method = CompressionMethodOption.DEFLATE
                )
                ArchiveFormatOption.SEVEN_Z -> CompressionProfile(
                    format = format,
                    levelPreset = CompressionLevelPreset.NORMAL,
                    compressionLevel = 6,
                    method = CompressionMethodOption.LZMA2,
                    isSolid = true,
                    dictionarySizeMb = 32
                )
                ArchiveFormatOption.TAR -> CompressionProfile(
                    format = format,
                    levelPreset = CompressionLevelPreset.STORE,
                    compressionLevel = 0,
                    method = CompressionMethodOption.COPY
                )
                ArchiveFormatOption.TAR_GZ -> CompressionProfile(
                    format = format,
                    levelPreset = CompressionLevelPreset.NORMAL,
                    compressionLevel = 6,
                    method = CompressionMethodOption.DEFLATE
                )
                ArchiveFormatOption.TAR_XZ -> CompressionProfile(
                    format = format,
                    levelPreset = CompressionLevelPreset.NORMAL,
                    compressionLevel = 6,
                    method = CompressionMethodOption.LZMA2
                )
                ArchiveFormatOption.TAR_BZ2 -> CompressionProfile(
                    format = format,
                    levelPreset = CompressionLevelPreset.NORMAL,
                    compressionLevel = 9,
                    method = CompressionMethodOption.BZIP2
                )
            }
        }
    }
}
