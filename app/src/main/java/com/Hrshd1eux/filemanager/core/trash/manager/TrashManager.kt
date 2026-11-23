package com.Hrshd1eux.filemanager.core.trash.manager

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.Hrshd1eux.filemanager.ArcaApplication
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.trash.model.TrashEntry
import com.Hrshd1eux.filemanager.core.trash.shredder.FileShredder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

class TrashManager(
    context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val baseTrashDir: File? = null
) {
    private val appContext: Context? = context?.applicationContext ?: ArcaApplication.instance
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val trashDir: File by lazy {
        resolveTrashDirectory()
    }

    private fun ensureWritableDir(dir: File): Boolean {
        return runCatching {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if (!dir.exists() || !dir.isDirectory) return@runCatching false
            val probe = File(dir, ".probe_${System.currentTimeMillis()}_${(0..9999).random()}")
            val created = probe.createNewFile()
            if (created) {
                probe.delete()
                val noMedia = File(dir, ".nomedia")
                if (!noMedia.exists()) {
                    runCatching { noMedia.createNewFile() }
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun resolveTrashDirectory(): File {
        // Priority 1: Explicit base trash directory (used in unit tests / explicit override)
        baseTrashDir?.let {
            if (ensureWritableDir(it)) return it
        }

        // Priority 2: External storage root directory /storage/emulated/0/.arca_trash
        // (Works on Android 11+ with MANAGE_EXTERNAL_STORAGE and on unconstrained Android devices)
        runCatching {
            if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                val extDir = File(Environment.getExternalStorageDirectory(), ".arca_trash")
                if (ensureWritableDir(extDir)) return extDir
            }
        }

        // Priority 3: App-specific external files dir
        // (/storage/emulated/0/Android/data/com.Hrshd1eux.filemanager/files/.arca_trash)
        // CRITICAL FOR ANDROID 10 (Scoped Storage):
        // Resides on the EXACT SAME storage volume as user files (/storage/emulated/0),
        // completely bypassing Android 10 root-folder restrictions without requiring permissions,
        // and enabling fast local rename/move operations.
        runCatching {
            val appExtDir = appContext?.getExternalFilesDir(null)
            if (appExtDir != null) {
                val appTrashDir = File(appExtDir, ".arca_trash")
                if (ensureWritableDir(appTrashDir)) return appTrashDir
            }
        }

        // Priority 4: App internal files directory fallback
        val internalTrashDir = File(appContext?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "."), ".arca_trash")
        ensureWritableDir(internalTrashDir)
        return internalTrashDir
    }

    private val indexFile: File
        get() = File(trashDir, "trash_index.json")

    private val _entries = MutableStateFlow<List<TrashEntry>>(emptyList())
    val entries: StateFlow<List<TrashEntry>> = _entries.asStateFlow()

    init {
        loadIndex()
    }

    private fun loadIndex() {
        runCatching {
            if (indexFile.exists()) {
                val text = indexFile.readText()
                val loaded = json.decodeFromString<List<TrashEntry>>(text)
                _entries.value = loaded.sortedByDescending { it.trashedTimestampMillis }
            } else {
                _entries.value = emptyList()
            }
        }.onFailure {
            _entries.value = emptyList()
        }
    }

    private fun saveIndexLocked() {
        runCatching {
            val encoded = json.encodeToString(_entries.value)
            val tmp = File(trashDir, "trash_index.json.tmp")
            tmp.writeText(encoded)
            tmp.renameTo(indexFile)
        }
    }

    suspend fun moveToTrash(node: StorageNode): Result<TrashEntry> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val sourceFile = File(node.path)
                if (!sourceFile.exists()) {
                    throw IOException("Source file does not exist: ${node.path}")
                }

                if (!trashDir.exists()) {
                    trashDir.mkdirs()
                }

                val entryId = UUID.randomUUID().toString()
                val destinationFile = File(trashDir, "$entryId.trash")

                val moved = sourceFile.renameTo(destinationFile)
                if (!moved) {
                    // Fallback to copy then safe delete (e.g. cross-filesystem or Android 10 mount point boundaries)
                    if (sourceFile.isDirectory) {
                        sourceFile.copyRecursively(destinationFile, overwrite = true)
                    } else {
                        sourceFile.copyTo(destinationFile, overwrite = true)
                    }

                    val deleted = if (sourceFile.isDirectory) {
                        sourceFile.deleteRecursively()
                    } else {
                        sourceFile.delete()
                    }

                    if (!deleted && sourceFile.exists()) {
                        // Attempt shredder as secondary deletion attempt
                        val shredResult = FileShredder.shred(sourceFile, ioDispatcher)
                        if (shredResult.isFailure && sourceFile.exists()) {
                            destinationFile.deleteRecursively()
                            throw IOException("Failed to remove original file during move to trash: ${sourceFile.absolutePath}")
                        }
                    }
                }

                // Scan original path on Android so MediaStore clears cache of the deleted/moved item immediately
                appContext?.let { ctx ->
                    runCatching {
                        MediaScannerConnection.scanFile(ctx, arrayOf(sourceFile.absolutePath), null, null)
                    }
                }

                val entry = TrashEntry(
                    id = entryId,
                    originalPath = node.path,
                    originalName = node.name.ifEmpty { sourceFile.name },
                    trashedPath = destinationFile.absolutePath,
                    sizeBytes = if (node.isDirectory) destinationFile.walk().filter { it.isFile }.map { it.length() }.sum() else destinationFile.length(),
                    trashedTimestampMillis = System.currentTimeMillis(),
                    isDirectory = node.isDirectory,
                    mimeType = node.mimeType
                )

                _entries.value = listOf(entry) + _entries.value
                saveIndexLocked()
                entry
            }
        }
    }

    suspend fun moveToTrash(nodes: List<StorageNode>): Result<Int> = withContext(ioDispatcher) {
        var count = 0
        var firstError: Throwable? = null
        for (node in nodes) {
            val res = moveToTrash(node)
            if (res.isSuccess) {
                count++
            } else if (firstError == null) {
                firstError = res.exceptionOrNull()
            }
        }
        if (nodes.isNotEmpty() && count == 0 && firstError != null) {
            Result.failure(firstError)
        } else {
            Result.success(count)
        }
    }

    suspend fun restore(entry: TrashEntry): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val trashedFile = File(entry.trashedPath)
                if (!trashedFile.exists()) {
                    throw IOException("Trashed file not found on disk: ${entry.trashedPath}")
                }

                var targetFile = File(entry.originalPath)
                val parentDir = targetFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }

                // If target already exists, resolve collision with suffix
                if (targetFile.exists()) {
                    val nameWithoutExt = targetFile.nameWithoutExtension
                    val ext = targetFile.extension
                    val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
                    var suffix = 1
                    do {
                        targetFile = File(parentDir, "$nameWithoutExt (Restored $suffix)$dotExt")
                        suffix++
                    } while (targetFile.exists())
                }

                val restored = trashedFile.renameTo(targetFile)
                if (!restored) {
                    if (trashedFile.isDirectory) {
                        trashedFile.copyRecursively(targetFile, overwrite = true)
                    } else {
                        trashedFile.copyTo(targetFile, overwrite = true)
                    }
                    trashedFile.deleteRecursively()
                }

                // Notify MediaStore to re-index the restored file
                appContext?.let { ctx ->
                    runCatching {
                        MediaScannerConnection.scanFile(ctx, arrayOf(targetFile.absolutePath), null, null)
                    }
                }

                _entries.value = _entries.value.filter { it.id != entry.id }
                saveIndexLocked()
            }
        }
    }

    suspend fun shred(entry: TrashEntry): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val trashedFile = File(entry.trashedPath)
                if (trashedFile.exists()) {
                    FileShredder.shred(trashedFile, ioDispatcher).getOrThrow()
                }
                _entries.value = _entries.value.filter { it.id != entry.id }
                saveIndexLocked()
            }
        }
    }

    suspend fun emptyTrash(): Result<Int> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                var count = 0
                val current = _entries.value.toList()
                for (entry in current) {
                    val file = File(entry.trashedPath)
                    if (file.exists()) {
                        FileShredder.shred(file, ioDispatcher)
                    }
                    count++
                }
                _entries.value = emptyList()
                saveIndexLocked()
                count
            }
        }
    }

    suspend fun purgeExpired(retentionDays: Int = 30): Result<Int> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                val cutoff = System.currentTimeMillis() - (retentionDays * 86_400_000L)
                val expired = _entries.value.filter { it.trashedTimestampMillis < cutoff }
                var count = 0
                for (entry in expired) {
                    val file = File(entry.trashedPath)
                    if (file.exists()) {
                        FileShredder.shred(file, ioDispatcher)
                    }
                    count++
                }
                if (count > 0) {
                    _entries.value = _entries.value.filter { it.trashedTimestampMillis >= cutoff }
                    saveIndexLocked()
                }
                count
            }
        }
    }

    companion object {
        @Volatile
        private var instance: TrashManager? = null

        fun getInstance(context: Context? = null): TrashManager {
            return instance ?: synchronized(this) {
                instance ?: TrashManager(context).also { instance = it }
            }
        }
    }
}
