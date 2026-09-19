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
    val supportsEncryption: Boolean get() = this == SEVEN_Z
    val supportsEncryptedFileNamesReading: Boolean get() = this == SEVEN_Z
    val supportsEncryptedFileNamesCreation: Boolean get() = false
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

enum class SplitSizePreset(val displayName: String, val sizeBytes: Long?) {
    NO_SPLIT("No split", null),
    MB_10("10 MB", 10L * 1024 * 1024),
    MB_100("100 MB", 100L * 1024 * 1024),
    MB_700("700 MB (CD)", 700L * 1024 * 1024),
    GB_2("2 GB", 2L * 1024 * 1024 * 1024),
    GB_4("4 GB (FAT32)", 4L * 1024 * 1024 * 1024),
    CUSTOM("Custom", -1L)
}

enum class CompressionDialogMode(val displayName: String) {
    SIMPLE("Simple"),
    ADVANCED("Advanced")
}

enum class DictionarySizePreset(val displayName: String, val sizeMb: Int, val memoryEstimateMb: Int) {
    MB_4("4 MB", 4, 40),
    MB_16("16 MB", 16, 160),
    MB_32("32 MB", 32, 340),
    MB_64("64 MB", 64, 660),
    MB_128("128 MB", 128, 1300);

    companion object {
        fun fromSizeMb(sizeMb: Int): DictionarySizePreset {
            return entries.firstOrNull { it.sizeMb == sizeMb } ?: MB_32
        }
    }
}

enum class ThreadCountPreset(val displayName: String, val count: Int) {
    AUTO("Auto", 0),
    THREADS_1("1", 1),
    THREADS_2("2", 2),
    THREADS_4("4", 4),
    THREADS_8("8", 8);

    companion object {
        fun fromCount(count: Int): ThreadCountPreset {
            return entries.firstOrNull { it.count == count } ?: AUTO
        }
    }
}

data class CompressionProfile(
    val format: ArchiveFormatOption = ArchiveFormatOption.ZIP,
    val levelPreset: CompressionLevelPreset = CompressionLevelPreset.NORMAL,
    val compressionLevel: Int = 6,
    val method: CompressionMethodOption = CompressionMethodOption.DEFAULT,
    val threads: Int = 0, // 0 = Auto
    val isSolid: Boolean = true,
    val dictionarySizeMb: Int = 32,
    val splitSize: SplitSizePreset = SplitSizePreset.NO_SPLIT,
    val customSplitSizeBytes: Long? = null,
    val encryptFileNames: Boolean = false
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
