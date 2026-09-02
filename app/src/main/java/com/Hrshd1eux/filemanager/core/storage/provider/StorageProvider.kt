package com.Hrshd1eux.filemanager.core.storage.provider

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.model.StorageStats

/**
 * Common contract for all filesystem and document storage sources.
 */
interface StorageProvider {
    val providerId: String
    val displayName: String

    fun getRootNode(): StorageNode

    suspend fun listChildren(targetId: String): Result<List<StorageNode>>

    suspend fun getNode(targetId: String): Result<StorageNode>

    suspend fun getStorageStats(): Result<StorageStats>

    suspend fun exists(targetId: String): Boolean

    fun hasPermission(targetId: String): Boolean
}
