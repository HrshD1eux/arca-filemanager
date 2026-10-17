package com.Hrshd1eux.filemanager.core.vault.search

import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry

/**
 * High-Security Search Engine for Private Vault.
 *
 * Architectural Guarantees:
 * 1. LOCAL ONLY: All search indexing and queries are performed strictly within the local device process.
 *    No search terms, filenames, or metadata are ever transmitted off-device, logged, or shared with
 *    analytics, cloud services, or system search providers.
 * 2. ZERO PLAINTEXT DISK FOOTPRINT: No plaintext or persistent index files are ever written to disk.
 *    The vault directory on disk contains exclusively AES-256-GCM encrypted payloads (manifest.enc, meta.enc, *.enc).
 * 3. GENERATED ON DEMAND IN MEMORY: The search index is built dynamically in volatile RAM directly from the
 *    decrypted [VaultManifest] only while the vault is actively unlocked.
 * 4. EPHEMERAL WIPE ON LOCK: As soon as the vault locks (via timeout, screen off, app background, or manual lock),
 *    the active manifest, search queries, and candidate results are immediately dereferenced and purged from memory.
 * 5. ZERO LOGGING POLICY: Search queries, candidate filenames, and matches are strictly prohibited from being
 *    written to Android Logcat, diagnostic logs, or persistent notifications.
 */
object VaultSearchEngine {

    const val INDEX_STRATEGY = "ON_DEMAND_IN_MEMORY"
    const val IS_LOCAL_ONLY = true
    const val PERSISTS_PLAINTEXT_INDEX = false

    /**
     * Executes an on-demand, multi-token, case-insensitive search across in-memory vault entries.
     * Searches filenames, parent virtual paths, extensions, MIME types, categories, formatted sizes, and dates.
     *
     * @param entries Active decrypted vault entries in volatile memory.
     * @param query User search query (processed in-memory without persistent logging).
     * @return Filtered and sorted list of matching vault entries.
     */
    fun search(entries: List<VaultFileEntry>, query: String): List<VaultFileEntry> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val tokens = cleanQuery.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        return entries.filter { entry ->
            val ext = entry.originalName.substringAfterLast('.', "")
            val category = FileFormatter.getCategoryDescription(entry.mimeType, entry.isDirectory)
            val sizeStr = FileFormatter.formatFileSize(entry.size)
            val dateStr = FileFormatter.formatDateShort(entry.lastModified)
            val fullPath = if (entry.parentVirtualPath.isEmpty()) entry.originalName else "${entry.parentVirtualPath}/${entry.originalName}"

            val searchableText = buildString {
                append(entry.originalName.lowercase())
                append(" ")
                append(fullPath.lowercase())
                append(" ")
                append(ext.lowercase())
                append(" ")
                append(entry.mimeType.lowercase())
                append(" ")
                append(category.lowercase())
                append(" ")
                append(sizeStr.lowercase())
                append(" ")
                append(dateStr.lowercase())
            }

            tokens.all { token -> searchableText.contains(token) }
        }.sortedWith(
            compareBy<VaultFileEntry> { !it.isDirectory }
                .thenByDescending { it.originalName.lowercase().startsWith(tokens.first()) }
                .thenBy { it.originalName.lowercase() }
        )
    }

    /**
     * Explicitly wipes any transient references or cached query representations.
     */
    fun clear() {
        // In-memory on-demand architecture does not hold persistent references
    }
}
