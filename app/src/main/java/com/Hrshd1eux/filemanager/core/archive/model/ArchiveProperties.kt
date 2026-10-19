package com.Hrshd1eux.filemanager.core.archive.model

data class ArchiveProperties(
    val archiveName: String,
    val archivePath: String,
    val totalFiles: Int,
    val totalFolders: Int,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val lastModified: Long,
    val formatDisplayName: String = "Archive",
    val compressionMethod: String = "Standard",
    val isEncrypted: Boolean = false,
    val encryptionDetails: String? = null,
    val isSolid: Boolean = false,
    val isSplit: Boolean = false,
    val splitVolumeCount: Int = 1,
    val comment: String? = null
) {
    val compressionRatio: Float
        get() = if (uncompressedSize > 0 && compressedSize >= 0) {
            (1f - (compressedSize.toFloat() / uncompressedSize.toFloat())).coerceIn(0f, 1f)
        } else 0f

    val spaceSaved: Long
        get() = (uncompressedSize - compressedSize).coerceAtLeast(0L)
}

data class ArchiveIntegrityReport(
    val archiveName: String,
    val totalEntries: Int,
    val verifiedEntries: Int,
    val corruptedEntries: List<String>,
    val elapsedMs: Long
) {
    val isPassed: Boolean
        get() = corruptedEntries.isEmpty() && verifiedEntries == totalEntries
}
