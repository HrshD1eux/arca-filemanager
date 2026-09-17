package com.Hrshd1eux.filemanager.core.dragdrop

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import java.io.File

sealed interface DragDropPayload {
    data class StorageNodes(val paths: List<String>) : DragDropPayload
    data class ArchiveEntry(val archivePath: String, val entryPath: String) : DragDropPayload
    data class ExternalUris(val uris: List<Uri>) : DragDropPayload
}

object DragDropManager {

    const val MIME_ARCA_STORAGE_NODES = "application/vnd.arca.nodes"
    const val MIME_ARCA_ARCHIVE_ENTRY = "application/vnd.arca.archive-entry"

    private const val PREFIX_NODE = "ARCA_NODE:"
    private const val PREFIX_ARCHIVE = "ARCA_ARCHIVE_ENTRY:"

    /**
     * Creates a ClipData representing one or more StorageNodes.
     */
    fun createClipData(nodes: List<StorageNode>): ClipData {
        require(nodes.isNotEmpty()) { "Cannot create ClipData for empty nodes" }

        val firstNode = nodes.first()
        val label = if (nodes.size == 1) firstNode.name else "${nodes.size} items"
        val payloadText = nodes.joinToString(";") { "$PREFIX_NODE${it.path}" }

        val clipData = ClipData.newPlainText(label, payloadText)
        // Add individual file URIs so standard Android drop targets and cross-app drops work
        for (node in nodes) {
            val fileUri = Uri.fromFile(File(node.path))
            clipData.addItem(ClipData.Item(fileUri))
        }
        return clipData
    }

    /**
     * Creates a ClipData representing an entry inside an archive file.
     */
    fun createArchiveEntryClipData(archivePath: String, entryPath: String): ClipData {
        val entryName = entryPath.trimEnd('/').substringAfterLast('/')
        val payloadText = "$PREFIX_ARCHIVE$archivePath|$entryPath"
        return ClipData.newPlainText(entryName, payloadText)
    }

    /**
     * Parses a ClipData object received during a drop operation into a recognized payload.
     */
    fun parseClipData(clipData: ClipData?): DragDropPayload? {
        if (clipData == null || clipData.itemCount == 0) return null

        val rawTexts = mutableListOf<String>()
        val uris = mutableListOf<Uri>()

        for (i in 0 until clipData.itemCount) {
            val item = clipData.getItemAt(i)
            item.text?.toString()?.let { rawTexts.add(it) }
            item.uri?.let { uris.add(it) }
        }

        val allText = rawTexts.joinToString(";")

        // 1. Check for Arca Archive Entry
        if (allText.contains(PREFIX_ARCHIVE)) {
            val archiveText = allText.split(";").firstOrNull { it.startsWith(PREFIX_ARCHIVE) }
            if (archiveText != null) {
                val data = archiveText.removePrefix(PREFIX_ARCHIVE)
                val parts = data.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return DragDropPayload.ArchiveEntry(
                        archivePath = parts[0],
                        entryPath = parts[1]
                    )
                }
            }
        }

        // 2. Check for Arca Storage Nodes
        if (allText.contains(PREFIX_NODE)) {
            val nodePaths = allText.split(";")
                .filter { it.startsWith(PREFIX_NODE) }
                .map { it.removePrefix(PREFIX_NODE) }
                .filter { it.isNotBlank() }
            if (nodePaths.isNotEmpty()) {
                return DragDropPayload.StorageNodes(nodePaths)
            }
        }

        // 3. Check for File URIs in ClipData items
        val filePaths = uris
            .filter { it.scheme == "file" }
            .mapNotNull { it.path }
            .filter { it.isNotBlank() }

        if (filePaths.isNotEmpty()) {
            return DragDropPayload.StorageNodes(filePaths)
        }

        // 4. Fallback to external content URIs (e.g. from external apps)
        if (uris.isNotEmpty()) {
            return DragDropPayload.ExternalUris(uris)
        }

        return null
    }

    /**
     * Validates whether the given payload can safely be dropped into targetDirectory.
     * Prevents moving a directory into itself or into one of its children,
     * or dropping an item into the exact directory it already resides in.
     */
    fun canDrop(payload: DragDropPayload, targetDirectory: StorageNode): Boolean {
        if (!targetDirectory.isDirectory) return false
        val normTarget = targetDirectory.path.replace('\\', '/').trimEnd('/')

        when (payload) {
            is DragDropPayload.StorageNodes -> {
                for (sourcePath in payload.paths) {
                    val cleanSource = sourcePath.replace('\\', '/').trimEnd('/')
                    // Cannot drop into itself
                    if (cleanSource == normTarget) return false
                    // Cannot drop parent into its child (prevent recursive nesting)
                    if (normTarget.startsWith("$cleanSource/")) return false
                    // Cannot drop into same parent folder (already there)
                    val normParent = File(sourcePath).parentFile?.absolutePath?.replace('\\', '/')?.trimEnd('/')
                    if (normParent == normTarget) return false
                }
                return true
            }
            is DragDropPayload.ArchiveEntry -> {
                // Archive entry can be extracted into any valid folder
                return true
            }
            is DragDropPayload.ExternalUris -> {
                return true
            }
        }
    }
}
