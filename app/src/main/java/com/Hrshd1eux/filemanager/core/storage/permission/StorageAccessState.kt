package com.Hrshd1eux.filemanager.core.storage.permission

/**
 * Granular classification of storage access permission levels (Milestone 1.5.1).
 */
enum class StorageAccessLevel(val displayName: String) {
    FULL_ALL_FILES("Full Files Access"),
    SCOPED_FOLDER_TREES("Scoped Folder Access"),
    DENIED_CAN_REQUEST("Access Required (Can Request In-App)"),
    DENIED_SETTINGS_REQUIRED("Access Required (System Settings Required)"),
    REVOKED("Access Previously Granted, Currently Revoked")
}

/**
 * Rich model representing the current storage permission state and recovery capability (Milestones 1.5.1 & 1.5.3).
 */
data class StorageAccessState(
    val level: StorageAccessLevel,
    val hasAllFilesAccess: Boolean,
    val scopedTreeUris: List<String> = emptyList(),
    val wasPreviouslyGranted: Boolean = false,
    val isRevoked: Boolean = false,
    val canRequestDirectly: Boolean = true,
    val statusDescription: String = "",
    val recoveryActionDescription: String = ""
) {
    val isUsable: Boolean
        get() = hasAllFilesAccess || scopedTreeUris.isNotEmpty()

    val isFullAccess: Boolean
        get() = hasAllFilesAccess

    val isScopedAccess: Boolean
        get() = !hasAllFilesAccess && scopedTreeUris.isNotEmpty()

    companion object {
        fun fullAccess(): StorageAccessState {
            return StorageAccessState(
                level = StorageAccessLevel.FULL_ALL_FILES,
                hasAllFilesAccess = true,
                wasPreviouslyGranted = true,
                isRevoked = false,
                statusDescription = "Full shared storage and SD card access is active.",
                recoveryActionDescription = "No action required."
            )
        }

        fun scopedAccess(treeUris: List<String>): StorageAccessState {
            return StorageAccessState(
                level = StorageAccessLevel.SCOPED_FOLDER_TREES,
                hasAllFilesAccess = false,
                scopedTreeUris = treeUris,
                wasPreviouslyGranted = false,
                isRevoked = false,
                statusDescription = "Scoped access to ${treeUris.size} specific folder trees is active.",
                recoveryActionDescription = "You can grant full files access at any time from Settings."
            )
        }

        fun denied(
            canRequestDirectly: Boolean,
            wasPreviouslyGranted: Boolean,
            isRevoked: Boolean
        ): StorageAccessState {
            val level = when {
                isRevoked -> StorageAccessLevel.REVOKED
                canRequestDirectly -> StorageAccessLevel.DENIED_CAN_REQUEST
                else -> StorageAccessLevel.DENIED_SETTINGS_REQUIRED
            }

            val status = if (isRevoked) {
                "Storage permission was previously granted, but has been revoked by the system or in Settings."
            } else {
                "Storage access has not been granted."
            }

            val action = if (isRevoked) {
                "Restore storage access in System Settings to resume browsing your files."
            } else if (canRequestDirectly) {
                "Grant storage access to browse device files."
            } else {
                "Open System Settings to enable storage management permission."
            }

            return StorageAccessState(
                level = level,
                hasAllFilesAccess = false,
                wasPreviouslyGranted = wasPreviouslyGranted,
                isRevoked = isRevoked,
                canRequestDirectly = canRequestDirectly,
                statusDescription = status,
                recoveryActionDescription = action
            )
        }
    }
}

/**
 * Diagnostics snapshot inspecting exact platform permission flags for transparent debugging (Milestone 1.5.5).
 */
data class StoragePermissionDiagnostics(
    val apiLevel: Int,
    val isAllFilesSupported: Boolean,
    val isExternalStorageManager: Boolean,
    val hasLegacyReadPermission: Boolean,
    val hasLegacyWritePermission: Boolean,
    val persistedUriCount: Int,
    val persistedUris: List<String>,
    val wasEverGranted: Boolean,
    val isRevoked: Boolean,
    val diagnosticSummary: String
)
