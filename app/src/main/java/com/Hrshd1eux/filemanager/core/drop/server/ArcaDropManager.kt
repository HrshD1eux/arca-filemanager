package com.Hrshd1eux.filemanager.core.drop.server

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.Hrshd1eux.filemanager.core.drop.model.ArcaDropState
import com.Hrshd1eux.filemanager.core.drop.model.DropLogEntry
import com.Hrshd1eux.filemanager.core.drop.model.DropReceivedFile
import com.Hrshd1eux.filemanager.core.drop.model.DropReceivedText
import com.Hrshd1eux.filemanager.core.drop.model.DropTransferItem
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.core.storage.util.PathJumpResolver
import com.Hrshd1eux.filemanager.core.storage.util.SafeFileWalker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Singleton state coordinator for ARCA Drop sessions, folder expansion,
 * external Share URI staging, and server lifecycle callbacks.
 */
class ArcaDropManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: ArcaDropManager? = null

        fun getInstance(): ArcaDropManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArcaDropManager().also { INSTANCE = it }
            }
        }

        fun defaultReceiveDirectoryPath(): String {
            val downloads = runCatching {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            }.getOrNull()
            val base = downloads ?: "${PathJumpResolver.defaultExternalStorageRoot()}/Download"
            return File(base, "ARCA Drop").absolutePath
        }
    }

    private val _state = MutableStateFlow(
        ArcaDropState(receiveDirectoryPath = defaultReceiveDirectoryPath())
    )
    val state: StateFlow<ArcaDropState> = _state.asStateFlow()

    @Volatile
    private var stageRootDir: File? = null

    fun setReceiveDirectory(path: String) {
        if (path.isBlank() || PathJumpResolver.isProtectedVaultPath(path)) return
        _state.update { it.copy(receiveDirectoryPath = path) }
    }

    fun getReceiveDirectoryFile(): File {
        val path = _state.value.receiveDirectoryPath.ifBlank { defaultReceiveDirectoryPath() }
        return File(path)
    }

    fun setSharedText(text: String?) {
        val cleaned = text?.takeIf { it.isNotBlank() }
        _state.update { it.copy(sharedText = cleaned) }
    }

    fun addStorageNodes(nodes: List<StorageNode>) {
        val newItems = ArrayList<DropTransferItem>()
        for (node in nodes) {
            if (PathJumpResolver.isProtectedVaultPath(node.path)) continue
            val rootFile = File(node.path)
            if (!rootFile.exists()) continue

            if (rootFile.isFile) {
                newItems.add(fileToTransferItem(rootFile, rootFile.name))
            } else if (rootFile.isDirectory) {
                val baseParent = rootFile.parentFile?.absolutePath ?: ""
                SafeFileWalker.walkSafe(rootFile, includeHidden = false, maxDepth = 16)
                    .filter { it.isFile && !PathJumpResolver.isProtectedVaultPath(it.path) }
                    .forEach { file ->
                        val rel = if (baseParent.isNotEmpty() && file.absolutePath.startsWith(baseParent)) {
                            file.absolutePath.removePrefix(baseParent).trimStart('/', '\\').replace('\\', '/')
                        } else {
                            "${rootFile.name}/${file.name}"
                        }
                        newItems.add(fileToTransferItem(file, rel))
                    }
            }
        }

        if (newItems.isEmpty()) return
        _state.update { current ->
            val existingPaths = current.sharedItems.mapNotNull { it.file?.absolutePath }.toSet()
            val uniqueAdded = newItems.filter { it.file?.absolutePath !in existingPaths }
            current.copy(sharedItems = current.sharedItems + uniqueAdded)
        }
        appendLog("Queued ${newItems.size} item(s) for transfer", false)
    }

    fun stageExternalUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val stageDir = File(context.cacheDir, "arca_drop_stage").apply { mkdirs() }
        stageRootDir = stageDir

        val stagedItems = ArrayList<DropTransferItem>()
        val resolver = context.contentResolver
        for (uri in uris) {
            var displayName = "shared_${System.currentTimeMillis()}"
            var reportedSize = -1L

            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                            }
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                                reportedSize = cursor.getLong(sizeIdx)
                            }
                        }
                    }
            }

            val cleanName = ArcaDropServer.sanitizeUploadFilename(displayName)
            val stagedFile = File(stageDir, "${UUID.randomUUID()}_$cleanName")

            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(stagedFile).use { output ->
                        val buf = ByteArray(65_536)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                        }
                    }
                    true
                } ?: false
            }.getOrDefault(false)

            if (copied && stagedFile.exists()) {
                val actualSize = if (stagedFile.length() > 0L) stagedFile.length() else reportedSize.coerceAtLeast(0L)
                val mime = resolver.getType(uri) ?: resolveMimeType(cleanName)
                stagedItems.add(
                    DropTransferItem(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        relativePath = cleanName,
                        mimeType = mime,
                        size = actualSize,
                        file = stagedFile,
                        staged = true
                    )
                )
            } else {
                stagedFile.delete()
            }
        }

        if (stagedItems.isNotEmpty()) {
            _state.update { current ->
                current.copy(sharedItems = current.sharedItems + stagedItems)
            }
            appendLog("Staged ${stagedItems.size} shared item(s)", false)
        }
    }

    fun removeSharedItem(id: String) {
        _state.update { current ->
            val removed = current.sharedItems.firstOrNull { it.id == id }
            if (removed?.staged == true) {
                removed.file?.delete()
            }
            current.copy(sharedItems = current.sharedItems.filterNot { it.id == id })
        }
    }

    fun clearSharedQueue() {
        _state.update { current ->
            current.sharedItems.filter { it.staged }.forEach { it.file?.delete() }
            current.copy(sharedItems = emptyList(), sharedText = null)
        }
    }

    internal fun onServerStarted(port: Int, pin: String, ipAddress: String?) {
        _state.update {
            it.copy(
                isRunning = true,
                port = port,
                pin = pin,
                ipAddress = ipAddress
            )
        }
    }

    internal fun onIpUpdated(ipAddress: String?) {
        _state.update { it.copy(ipAddress = ipAddress) }
    }

    internal fun onFileReceived(name: String, savedPath: String, sizeBytes: Long) {
        _state.update { current ->
            val entry = DropReceivedFile(name = name, savedPath = savedPath, sizeBytes = sizeBytes)
            current.copy(receivedFiles = listOf(entry) + current.receivedFiles)
        }
    }

    internal fun onTextReceived(text: String) {
        _state.update { current ->
            val entry = DropReceivedText(id = UUID.randomUUID().toString(), text = text)
            current.copy(receivedTexts = listOf(entry) + current.receivedTexts)
        }
    }

    internal fun onTransferProgress(sentDelta: Long, receivedDelta: Long) {
        if (sentDelta <= 0L && receivedDelta <= 0L) return
        _state.update {
            it.copy(
                bytesSent = it.bytesSent + sentDelta,
                bytesReceived = it.bytesReceived + receivedDelta
            )
        }
    }

    internal fun onActiveConnectionsChanged(count: Int) {
        _state.update { it.copy(activeConnections = count) }
    }

    internal fun appendLog(message: String, isError: Boolean) {
        _state.update { current ->
            val updated = (listOf(DropLogEntry(message = message, isError = isError)) + current.logs).take(60)
            current.copy(logs = updated)
        }
    }

    internal fun onServerStopped(context: Context? = null) {
        cleanupStagedCache(context)
        _state.update {
            it.copy(
                isRunning = false,
                port = 0,
                pin = "",
                activeConnections = 0
            )
        }
    }

    fun cleanupStagedCache(context: Context? = null) {
        val dir = stageRootDir ?: context?.let { File(it.cacheDir, "arca_drop_stage") }
        runCatching {
            if (dir != null && dir.exists()) {
                dir.deleteRecursively()
            }
        }
        stageRootDir = null
    }

    private fun fileToTransferItem(file: File, relativePath: String): DropTransferItem {
        return DropTransferItem(
            id = UUID.randomUUID().toString(),
            name = file.name,
            relativePath = relativePath,
            mimeType = resolveMimeType(file.name),
            size = file.length(),
            file = file,
            staged = false
        )
    }

    private fun resolveMimeType(fileName: String): String {
        val ext = File(fileName).extension
        return MimeTypeResolver.getMimeType(ext)
            ?: FileTypeResolver.resolveMimeType(fileName).ifBlank { "application/octet-stream" }
    }
}
