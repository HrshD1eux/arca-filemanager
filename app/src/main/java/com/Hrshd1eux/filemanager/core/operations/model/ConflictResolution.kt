package com.Hrshd1eux.filemanager.core.operations.model

/**
 * Policies for resolving file naming conflicts at the destination.
 */
enum class ConflictResolution {
    /** Overwrite the destination file if it already exists. */
    OVERWRITE,

    /** Skip the conflicting file and continue with remaining items. */
    SKIP,

    /** Automatically append an incremented numeric suffix: name (1).ext. */
    RENAME_AUTO,

    /** Abort the entire operation upon encountering a conflict. */
    CANCEL
}
