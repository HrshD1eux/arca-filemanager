package com.Hrshd1eux.filemanager.core.cleaner.model

import com.Hrshd1eux.filemanager.core.storage.model.StorageCategory
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

enum class OldFileThreshold(
    val ageMs: Long,
    val displayName: String
) {
    MONTHS_3(90L * 24 * 3600 * 1000L, "> 3 Months"),
    MONTHS_6(180L * 24 * 3600 * 1000L, "> 6 Months"),
    YEAR_1(365L * 24 * 3600 * 1000L, "> 1 Year"),
    YEAR_2(730L * 24 * 3600 * 1000L, "> 2 Years")
}

enum class DuplicateMatchLevel(val displayName: String) {
    CONFIRMED_HASH_MATCH("Confirmed Match (SHA-256)"),
    POSSIBLE_METADATA_CANDIDATE("Possible Candidate (Same size & name)")
}

enum class AnalyzerSortBy(val displayName: String) {
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A–Z)")
}

data class EmptyFolderItem(
    val id: String,
    val path: String,
    val name: String,
    val lastModified: Long
)

data class ScanDiagnostics(
    val scannedFilesCount: Int = 0,
    val scannedDirectoriesCount: Int = 0,
    val inaccessibleFoldersCount: Int = 0,
    val inaccessiblePaths: List<String> = emptyList(),
    val isEstimate: Boolean = false,
    val scanDurationMs: Long = 0L
) {
    val hasInaccessiblePaths: Boolean
        get() = inaccessibleFoldersCount > 0 || inaccessiblePaths.isNotEmpty()
}

data class CleanupCandidate(
    val id: String,
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val category: StorageCategory,
    val flagReason: String,
    val duplicateReferencePath: String? = null,
    val matchLevel: DuplicateMatchLevel? = null
) {
    fun toStorageNode(): StorageNode {
        return StorageNode(
            id = id,
            name = name,
            path = path,
            isDirectory = isDirectory,
            size = size,
            lastModified = lastModified,
            mimeType = null
        )
    }
}

enum class RevalidationStatus {
    VALID,
    STALE_MODIFIED,
    TARGET_NOT_FOUND,
    PERMISSION_RESTRICTED;

    companion object {
        val NOT_FOUND get() = TARGET_NOT_FOUND
        val RESTRICTED_ACCESS get() = PERMISSION_RESTRICTED
        val DIRECTORY_NOT_EMPTY get() = STALE_MODIFIED
    }
}

data class RevalidationResult(
    val candidate: CleanupCandidate,
    val status: RevalidationStatus,
    val detailMessage: String? = null
)

data class CleanupExecutionReport(
    val requestedCount: Int,
    val deletedCount: Int,
    val freedBytes: Long,
    val skippedStaleCount: Int,
    val restrictedCount: Int,
    val failedCount: Int = 0,
    val failedItems: List<Pair<CleanupCandidate, String>> = emptyList()
)

/**
 * Truthful disclosure regarding storage deletion mechanics.
 */
object DeletionTruthfulness {
    const val MODIFICATION_DATE_DISCLAIMER =
        "Note: Modification time indicates when a file was last written, but does not prove the file is unnecessary. " +
        "Essential reference documents, photo libraries, and system files legitimately preserve their original timestamps."

    const val DUPLICATE_CERTAINTY_NOTE =
        "Matching names or sizes alone does not prove that files are duplicates. " +
        "Only files with verified matching cryptographic hashes (SHA-256) are confirmed duplicate content."
    const val DUPLICATE_CERTAINTY_DISCLAIMER = DUPLICATE_CERTAINTY_NOTE

    const val FLASH_STORAGE_WEAR_LEVELING_NOTE =
        "Standard deletion unlinks file directory entries and marks storage blocks as reusable. " +
        "Wear-leveling on flash memory prevents ordinary filesystem deletion from physical block erasure and does not cryptographically overwrite physical storage blocks."
    const val FLASH_STORAGE_WEAR_DISCLAIMER = FLASH_STORAGE_WEAR_LEVELING_NOTE
}
