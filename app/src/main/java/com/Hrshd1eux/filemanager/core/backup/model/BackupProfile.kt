package com.Hrshd1eux.filemanager.core.backup.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class BackupMode(val displayName: String, val description: String) {
    INCREMENTAL("Copy (Incremental)", "Copies only new and modified files safely without deleting destination files"),
    MIRROR("Mirror Sync", "Synchronizes destination to match source, deleting destination files missing from source"),
    FULL("Full Backup", "Copies all selected files unconditionally");

    companion object {
        val COPY get() = INCREMENTAL
    }
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
