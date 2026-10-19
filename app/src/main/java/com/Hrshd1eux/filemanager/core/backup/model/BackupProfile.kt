package com.Hrshd1eux.filemanager.core.backup.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class BackupMode(val displayName: String, val description: String) {
    INCREMENTAL("Incremental", "Copies only new and modified files"),
    MIRROR("Mirror Sync", "Safely syncs all source files to destination non-destructively"),
    FULL("Full Backup", "Copies all selected files unconditionally")
}

@Serializable
enum class BackupStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED
}

@Serializable
data class BackupProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourcePaths: List<String>,
    val destinationPath: String,
    val mode: BackupMode = BackupMode.INCREMENTAL,
    val includeHiddenFiles: Boolean = false,
    val exclusionPatterns: List<String> = listOf("*.tmp", "*.part", ".cache", ".thumbnails"),
    val verifyIntegrity: Boolean = false,
    val lastRunTimestamp: Long? = null,
    val lastRunStatus: BackupStatus? = null,
    val lastRunSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
