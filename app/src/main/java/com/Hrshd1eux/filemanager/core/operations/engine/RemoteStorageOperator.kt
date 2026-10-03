package com.Hrshd1eux.filemanager.core.operations.engine

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import java.io.InputStream
import java.io.OutputStream

/**
 * Metadata entry representing a flattened node in a remote directory hierarchy.
 */
data class RemoteTreeEntry(
    val node: StorageNode,
    val relativeSubPath: String
)

/**
 * Universal abstraction for remote storage streams and operations.
 *
 * ARCHITECTURAL ISOLATION:
 * The offline operations core has zero dependencies on network protocols or sockets.
 * Concrete network providers implement this interface in the network layer and
 * register it with RemoteStorageOperatorRegistry so FileOperationEngine can transfer
 * data between local and remote storage uniformly.
 */
interface RemoteStorageOperator {
    fun canHandle(node: StorageNode): Boolean
    fun isSameServer(nodeA: StorageNode, nodeB: StorageNode): Boolean
    suspend fun openInputStream(node: StorageNode): Result<InputStream>
    suspend fun openOutputStream(node: StorageNode, append: Boolean = false): Result<OutputStream>
    suspend fun createDirectory(parentDir: StorageNode, dirName: String): Result<StorageNode>
    suspend fun createFile(parentDir: StorageNode, fileName: String): Result<StorageNode>
    suspend fun delete(target: StorageNode): Result<Unit>
    suspend fun deleteRecursive(target: StorageNode): Result<Unit>
    suspend fun rename(target: StorageNode, newName: String): Result<StorageNode>
    suspend fun exists(node: StorageNode): Boolean
    suspend fun listChildren(targetDir: StorageNode): Result<List<StorageNode>>
    fun resolveChild(parentDir: StorageNode, childName: String, isDirectory: Boolean = false): StorageNode
    suspend fun listTree(targetDir: StorageNode): Result<List<RemoteTreeEntry>>
}

/**
 * Global registry for the active RemoteStorageOperator.
 */
object RemoteStorageOperatorRegistry {
    @Volatile
    private var activeOperator: RemoteStorageOperator? = null

    fun register(operator: RemoteStorageOperator) {
        activeOperator = operator
    }

    fun getOperator(): RemoteStorageOperator? = activeOperator

    fun unregister() {
        activeOperator = null
    }
}
