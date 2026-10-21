package com.Hrshd1eux.filemanager.core.vault.integrity

import com.Hrshd1eux.filemanager.core.vault.VaultManager
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry
import java.io.File

enum class VaultIntegrityStatus(val displayName: String) {
    PRISTINE("All files verified and intact"),
    WARNING_ORPHANS("Extra orphaned files found on disk"),
    ERROR_MISSING_FILES("One or more files missing from disk"),
    UNCONFIGURED("Vault not configured")
}

data class VaultIntegrityReport(
    val status: VaultIntegrityStatus,
    val totalManifestEntries: Int,
    val totalPhysicalFiles: Int,
    val missingEntries: List<VaultFileEntry> = emptyList(),
    val orphanFiles: List<String> = emptyList(),
    val mismatchedSizes: List<Pair<VaultFileEntry, Long>> = emptyList()
) {
    val isHealthy: Boolean
        get() = status == VaultIntegrityStatus.PRISTINE
}

/**
 * Validates vault structure, physical file presence, and manifest consistency.
 */
class VaultIntegrityChecker(
    private val vaultManager: VaultManager
) {

    fun performIntegrityCheck(): VaultIntegrityReport {
        if (!vaultManager.isConfigured()) {
            return VaultIntegrityReport(
                status = VaultIntegrityStatus.UNCONFIGURED,
                totalManifestEntries = 0,
                totalPhysicalFiles = 0
            )
        }

        val dataDir = File(vaultManager.vaultDir, "data")
        val physicalFiles = dataDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val physicalFileNames = physicalFiles.map { it.name }.toSet()

        val manifestEntries = vaultManager.getAllFiles().filter { !it.isDirectory }
        val manifestFileNames = manifestEntries.map { it.encryptedFileName }.toSet()

        val missing = manifestEntries.filter { entry ->
            !physicalFileNames.contains(entry.encryptedFileName)
        }

        val orphans = physicalFiles
            .filter { !manifestFileNames.contains(it.name) }
            .map { it.name }

        val mismatched = manifestEntries.mapNotNull { entry ->
            val file = File(dataDir, entry.encryptedFileName)
            if (file.exists() && file.length() == 0L && entry.size > 0L) {
                entry to file.length()
            } else null
        }

        val status = when {
            missing.isNotEmpty() || mismatched.isNotEmpty() -> VaultIntegrityStatus.ERROR_MISSING_FILES
            orphans.isNotEmpty() -> VaultIntegrityStatus.WARNING_ORPHANS
            else -> VaultIntegrityStatus.PRISTINE
        }

        return VaultIntegrityReport(
            status = status,
            totalManifestEntries = manifestEntries.size,
            totalPhysicalFiles = physicalFiles.size,
            missingEntries = missing,
            orphanFiles = orphans,
            mismatchedSizes = mismatched
        )
    }
}
