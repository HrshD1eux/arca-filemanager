package com.Hrshd1eux.filemanager.core.cleaner.model

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

enum class ScanStage(val displayName: String) {
    INDEXING_SIZES("Indexing file sizes..."),
    HASHING_CANDIDATES("Computing cryptographic digests..."),
    VERIFYING("Verifying duplicate groups..."),
    COMPLETED("Scan completed")
}

data class DuplicateGroup(
    val hash: String,
    val sizePerFile: Long,
    val files: List<StorageNode>,
    val matchLevel: DuplicateMatchLevel = DuplicateMatchLevel.CONFIRMED_HASH_MATCH
) {
    val wastedBytes: Long
        get() = if (files.size > 1) sizePerFile * (files.size - 1) else 0L
}

data class DuplicateScanResult(
    val groups: List<DuplicateGroup> = emptyList(),
    val totalDuplicateFilesCount: Int = 0,
    val totalWastedBytes: Long = 0L
)
