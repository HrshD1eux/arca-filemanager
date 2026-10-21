package com.Hrshd1eux.filemanager.core.operations.recovery

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Operation types tracked by the recovery journal (Milestone 1.4.31).
 */
@Serializable
enum class JournalOperationType(val displayName: String) {
    COPY("Copy"),
    MOVE("Move"),
    ARCHIVE_EXTRACT("Archive Extraction"),
    ARCHIVE_COMPRESS("Archive Compression"),
    BACKUP("Backup")
}

/**
 * State of an operation in the journal.
 */
@Serializable
enum class JournalOperationState {
    IN_PROGRESS,
    INTERRUPTED,
    COMPLETED,
    DISCARDED
}

/**
 * State of an individual file within an operation.
 */
@Serializable
enum class JournalItemState {
    PENDING,
    IN_FLIGHT,
    COMPLETED,
    VERIFIED,
    FAILED
}

/**
 * Record of an individual file/item in the operation journal (Milestone 1.4.31).
 */
@Serializable
data class JournalItemRecord(
    val relativePath: String,
    val sourcePath: String,
    val destinationPath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val state: JournalItemState = JournalItemState.PENDING,
    val checksumSha256: String? = null,
    val isSourceDeleted: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Journal entry representing a persistent operation state (Milestone 1.4.31).
 *
 * Privacy Invariant:
 * When [isVaultOperation] is true or paths target secure vault locations,
 * filenames and vault metadata are scrubbed, and passwords/keys are strictly forbidden.
 */
@Serializable
data class OperationJournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: JournalOperationType,
    val title: String,
    val sourceRoots: List<String>,
    val destinationRoot: String,
    val state: JournalOperationState = JournalOperationState.IN_PROGRESS,
    val startTime: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val totalFilesExpected: Int = 0,
    val totalBytesExpected: Long = 0L,
    val completedFilesCount: Int = 0,
    val completedBytesCount: Long = 0L,
    val verifiedBytesCount: Long = 0L,
    val items: List<JournalItemRecord> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val isVaultOperation: Boolean = false,
    val interruptionReason: String? = null
) {
    val pendingItems: List<JournalItemRecord>
        get() = items.filter { it.state == JournalItemState.PENDING || it.state == JournalItemState.IN_FLIGHT }

    val completedItems: List<JournalItemRecord>
        get() = items.filter { it.state == JournalItemState.COMPLETED || it.state == JournalItemState.VERIFIED }

    val isFinished: Boolean
        get() = state == JournalOperationState.COMPLETED || state == JournalOperationState.DISCARDED
}

/**
 * Repository interface for managing persistent operation journals across process crashes.
 */
interface OperationJournalRepository {
    suspend fun recordOperationStarted(entry: OperationJournalEntry)
    suspend fun updateItemProgress(operationId: String, relativePath: String, state: JournalItemState, sha256: String? = null)
    suspend fun markItemSourceDeleted(operationId: String, relativePath: String)
    suspend fun recordOperationInterrupted(operationId: String, reason: String)
    suspend fun recordOperationCompleted(operationId: String)
    suspend fun recordOperationDiscarded(operationId: String)
    suspend fun getInterruptedOperations(): List<OperationJournalEntry>
    suspend fun getActiveOperations(): List<OperationJournalEntry>
    suspend fun getEntry(operationId: String): OperationJournalEntry?
    suspend fun deleteEntry(operationId: String)
    suspend fun clearHistory()
}

/**
 * Persistent atomic file-based implementation of [OperationJournalRepository].
 *
 * Features:
 * - Writes atomic temporary files (`.tmp` -> rename) to avoid journal corruption during crashes.
 * - Enforces zero sensitive vault content / credential persistence.
 * - Thread-safe via coroutine Mutex.
 */
class FileOperationJournalRepository(
    private val journalDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : OperationJournalRepository {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val activeDir = File(journalDir, "active")
    private val historyDir = File(journalDir, "history")

    init {
        if (!activeDir.exists()) activeDir.mkdirs()
        if (!historyDir.exists()) historyDir.mkdirs()
    }

    override suspend fun recordOperationStarted(entry: OperationJournalEntry): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val sanitized = sanitizeVaultEntry(entry)
            writeEntryFile(File(activeDir, "${sanitized.id}.json"), sanitized)
        }
    }

    override suspend fun updateItemProgress(
        operationId: String,
        relativePath: String,
        state: JournalItemState,
        sha256: String?
    ): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val file = File(activeDir, "$operationId.json")
            if (!file.exists()) return@withContext

            val current = readEntryFile(file) ?: return@withContext
            var completedBytes = 0L
            var completedCount = 0
            var verifiedBytes = 0L

            val updatedItems = current.items.map { item ->
                if (item.relativePath == relativePath) {
                    val updated = item.copy(
                        state = state,
                        checksumSha256 = sha256 ?: item.checksumSha256
                    )
                    if (state == JournalItemState.COMPLETED || state == JournalItemState.VERIFIED) {
                        completedBytes += updated.sizeBytes
                        completedCount++
                        if (state == JournalItemState.VERIFIED) {
                            verifiedBytes += updated.sizeBytes
                        }
                    }
                    updated
                } else {
                    if (item.state == JournalItemState.COMPLETED || item.state == JournalItemState.VERIFIED) {
                        completedBytes += item.sizeBytes
                        completedCount++
                        if (item.state == JournalItemState.VERIFIED) {
                            verifiedBytes += item.sizeBytes
                        }
                    }
                    item
                }
            }

            val updatedEntry = current.copy(
                lastUpdated = System.currentTimeMillis(),
                completedFilesCount = completedCount,
                completedBytesCount = completedBytes,
                verifiedBytesCount = verifiedBytes,
                items = updatedItems
            )
            writeEntryFile(file, updatedEntry)
        }
    }

    override suspend fun markItemSourceDeleted(
        operationId: String,
        relativePath: String
    ): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val file = File(activeDir, "$operationId.json")
            if (!file.exists()) return@withContext

            val current = readEntryFile(file) ?: return@withContext
            val updatedItems = current.items.map { item ->
                if (item.relativePath == relativePath) {
                    item.copy(isSourceDeleted = true)
                } else item
            }

            val updatedEntry = current.copy(
                lastUpdated = System.currentTimeMillis(),
                items = updatedItems
            )
            writeEntryFile(file, updatedEntry)
        }
    }

    override suspend fun recordOperationInterrupted(
        operationId: String,
        reason: String
    ): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val file = File(activeDir, "$operationId.json")
            if (!file.exists()) return@withContext

            val current = readEntryFile(file) ?: return@withContext
            val updated = current.copy(
                state = JournalOperationState.INTERRUPTED,
                lastUpdated = System.currentTimeMillis(),
                interruptionReason = reason
            )
            writeEntryFile(file, updated)
        }
    }

    override suspend fun recordOperationCompleted(operationId: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val activeFile = File(activeDir, "$operationId.json")
            if (!activeFile.exists()) return@withContext

            val current = readEntryFile(activeFile) ?: return@withContext
            val completed = current.copy(
                state = JournalOperationState.COMPLETED,
                lastUpdated = System.currentTimeMillis()
            )

            // Move to history
            val historyFile = File(historyDir, "$operationId.json")
            writeEntryFile(historyFile, completed)
            activeFile.delete()
        }
    }

    override suspend fun recordOperationDiscarded(operationId: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val activeFile = File(activeDir, "$operationId.json")
            if (!activeFile.exists()) return@withContext

            val current = readEntryFile(activeFile) ?: return@withContext
            val discarded = current.copy(
                state = JournalOperationState.DISCARDED,
                lastUpdated = System.currentTimeMillis()
            )

            val historyFile = File(historyDir, "$operationId.json")
            writeEntryFile(historyFile, discarded)
            activeFile.delete()
        }
    }

    override suspend fun getInterruptedOperations(): List<OperationJournalEntry> = withContext(ioDispatcher) {
        mutex.withLock {
            val activeFiles = activeDir.listFiles() ?: return@withContext emptyList()
            val list = mutableListOf<OperationJournalEntry>()
            for (f in activeFiles) {
                if (f.isFile && f.name.endsWith(".json")) {
                    val entry = readEntryFile(f)
                    if (entry != null) {
                        // Any active operation found on disk was interrupted by process death/crash
                        if (entry.state == JournalOperationState.IN_PROGRESS || entry.state == JournalOperationState.INTERRUPTED) {
                            list.add(entry.copy(state = JournalOperationState.INTERRUPTED))
                        }
                    }
                }
            }
            list.sortedByDescending { it.lastUpdated }
        }
    }

    override suspend fun getActiveOperations(): List<OperationJournalEntry> = withContext(ioDispatcher) {
        mutex.withLock {
            val activeFiles = activeDir.listFiles() ?: return@withContext emptyList()
            activeFiles.mapNotNull { if (it.isFile && it.name.endsWith(".json")) readEntryFile(it) else null }
        }
    }

    override suspend fun getEntry(operationId: String): OperationJournalEntry? = withContext(ioDispatcher) {
        mutex.withLock {
            val activeFile = File(activeDir, "$operationId.json")
            if (activeFile.exists()) return@withContext readEntryFile(activeFile)
            val historyFile = File(historyDir, "$operationId.json")
            if (historyFile.exists()) return@withContext readEntryFile(historyFile)
            null
        }
    }

    override suspend fun deleteEntry(operationId: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            File(activeDir, "$operationId.json").delete()
            File(historyDir, "$operationId.json").delete()
        }
    }

    override suspend fun clearHistory(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            historyDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Atomically writes entry to disk using a temporary file and rename.
     */
    private fun writeEntryFile(targetFile: File, entry: OperationJournalEntry) {
        val parent = targetFile.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()

        val tmpFile = File(parent, ".${targetFile.name}.tmp")
        try {
            val serialized = json.encodeToString(entry)
            tmpFile.writeText(serialized)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            if (tmpFile.exists()) tmpFile.delete()
            throw IOException("Failed to atomically write journal entry: ${e.localizedMessage}", e)
        }
    }

    private fun readEntryFile(file: File): OperationJournalEntry? {
        return try {
            val text = file.readText()
            json.decodeFromString<OperationJournalEntry>(text)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Milestone 1.4.31 Privacy Protection:
     * Scrubs plaintext contents and credentials from vault operations.
     */
    private fun sanitizeVaultEntry(entry: OperationJournalEntry): OperationJournalEntry {
        val isVault = entry.isVaultOperation ||
            entry.destinationRoot.contains(".arca_vault") ||
            entry.sourceRoots.any { it.contains(".arca_vault") }

        if (!isVault) return entry

        val sanitizedMetadata = entry.metadata.filterKeys { key ->
            !key.contains("password", ignoreCase = true) &&
                !key.contains("secret", ignoreCase = true) &&
                !key.contains("key", ignoreCase = true) &&
                !key.contains("pin", ignoreCase = true)
        }

        // Anonymize items to prevent leaking private filenames stored in vault
        val sanitizedItems = entry.items.mapIndexed { index, item ->
            item.copy(
                relativePath = "vault_item_$index.enc",
                sourcePath = if (item.sourcePath.contains(".arca_vault")) "[VAULT_ENCRYPTED_PAYLOAD]" else item.sourcePath,
                destinationPath = if (item.destinationPath.contains(".arca_vault")) "[VAULT_ENCRYPTED_CONTAINER]" else item.destinationPath
            )
        }

        return entry.copy(
            isVaultOperation = true,
            title = "Vault Secure Operation",
            metadata = sanitizedMetadata,
            items = sanitizedItems
        )
    }
}
