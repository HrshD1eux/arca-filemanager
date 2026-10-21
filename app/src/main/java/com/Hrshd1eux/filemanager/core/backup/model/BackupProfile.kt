package com.Hrshd1eux.filemanager.core.backup.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class BackupMode(val displayName: String, val description: String) {
    COPY("Safe Copy (Default)", "Copies source files to destination safely without modifying or deleting existing destination files"),
    INCREMENTAL("Incremental Copy", "Copies new and modified files after verifying change detection (size, timestamp, hash)"),
    MIRROR("Mirror Sync (Caution)", "Copies and verifies files first, then deletes destination files missing from source with explicit confirmation"),
    FULL("Full Backup", "Copies all selected files unconditionally");

    companion object {
        val DEFAULT get() = COPY
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
    val mode: BackupMode = BackupMode.COPY,
    val includeHiddenFiles: Boolean = false,
    val exclusionPatterns: List<String> = listOf("*.tmp", "*.part", ".cache", ".thumbnails"),
    val verifyIntegrity: Boolean = false,
    val lastRunTimestamp: Long? = null,
    val lastRunStatus: BackupStatus? = null,
    val lastRunSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
