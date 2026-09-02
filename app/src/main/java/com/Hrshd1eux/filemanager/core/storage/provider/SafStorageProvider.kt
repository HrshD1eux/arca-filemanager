package com.Hrshd1eux.filemanager.core.storage.provider

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * High-performance Storage Access Framework (SAF) implementation of StorageProvider.
 * Queries DocumentsContract projections directly to avoid DocumentFile.listFiles() overhead.
 */
class SafStorageProvider(
    private val context: Context,
    private val rootTreeUri: Uri,
    override val displayName: String = "External / SAF Folder"
) : StorageProvider {

    override val providerId: String = "saf_tree_${rootTreeUri.hashCode()}"

    private val contentResolver = context.contentResolver

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS
    )

    override fun getRootNode(): StorageNode {
        val docId = DocumentsContract.getTreeDocumentId(rootTreeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
        return StorageNode(
            id = docId,
            name = displayName,
            path = docUri.toString(),
            uriString = docUri.toString(),
            size = 0L,
            lastModified = 0L,
            isDirectory = true,
            isHidden = false,
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR
        )
    }

    override suspend fun listChildren(targetId: String): Result<List<StorageNode>> = withContext(Dispatchers.IO) {
        runCatching {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, targetId)
            val nodes = mutableListOf<StorageNode>()

            contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val flagsIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: childDocId
                    val mime = cursor.getString(mimeIdx)
                    val size = if (cursor.isNull(sizeIdx)) 0L else cursor.getLong(sizeIdx)
                    val modified = if (cursor.isNull(modifiedIdx)) 0L else cursor.getLong(modifiedIdx)
                    val flags = if (cursor.isNull(flagsIdx)) 0 else cursor.getInt(flagsIdx)

                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val childDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, childDocId)
                    val isWritable = (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0

                    nodes.add(
                        StorageNode(
                            id = childDocId,
                            name = name,
                            path = childDocUri.toString(),
                            uriString = childDocUri.toString(),
                            size = if (isDir) 0L else size,
                            lastModified = modified,
                            isDirectory = isDir,
                            isHidden = name.startsWith("."),
                            mimeType = mime,
                            isReadable = true,
                            isWritable = isWritable
                        )
                    )
                }
            } ?: throw IOException("Failed to query SAF cursor for targetId: $targetId")

            nodes
        }
    }

    override suspend fun getNode(targetId: String): Result<StorageNode> = withContext(Dispatchers.IO) {
        runCatching {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, targetId)
            contentResolver.query(
                docUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val flagsIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)

                    val childDocId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: childDocId
                    val mime = cursor.getString(mimeIdx)
                    val size = if (cursor.isNull(sizeIdx)) 0L else cursor.getLong(sizeIdx)
                    val modified = if (cursor.isNull(modifiedIdx)) 0L else cursor.getLong(modifiedIdx)
                    val flags = if (cursor.isNull(flagsIdx)) 0 else cursor.getInt(flagsIdx)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val isWritable = (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0

                    StorageNode(
                        id = childDocId,
                        name = name,
                        path = docUri.toString(),
                        uriString = docUri.toString(),
                        size = if (isDir) 0L else size,
                        lastModified = modified,
                        isDirectory = isDir,
                        isHidden = name.startsWith("."),
                        mimeType = mime,
                        isReadable = true,
                        isWritable = isWritable
                    )
                } else {
                    throw IOException("Node not found for docId: $targetId")
                }
            } ?: throw IOException("Failed to query node for docId: $targetId")
        }
    }

    override suspend fun getStorageStats(): Result<StorageStats> = withContext(Dispatchers.IO) {
        runCatching {
            // SAF does not always provide overall disk capacity for arbitrary provider trees.
            // Fallback to zeroed stats when not directly queryable via DocumentsContract.
            StorageStats(totalBytes = 0L, freeBytes = 0L)
        }
    }

    override suspend fun exists(targetId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, targetId)
            contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use {
                it.count > 0
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    override fun hasPermission(targetId: String): Boolean {
        val persistedUriPermissions = contentResolver.persistedUriPermissions
        return persistedUriPermissions.any {
            it.uri == rootTreeUri && (it.isReadPermission || it.isWritePermission)
        }
    }
}
