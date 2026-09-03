package com.Hrshd1eux.filemanager.core.archive.model

data class ArchiveEntry(
    val path: String,
    val name: String,
    val parentPath: String = "",
    val isDirectory: Boolean = false,
    val uncompressedSize: Long = 0L,
    val compressedSize: Long = 0L,
    val lastModified: Long = 0L,
    val crc: Long = 0L
) {
    val compressionRatio: Float
        get() = if (uncompressedSize > 0 && compressedSize >= 0) {
            (1f - (compressedSize.toFloat() / uncompressedSize.toFloat())).coerceIn(0f, 1f)
        } else 0f

    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "")
}
