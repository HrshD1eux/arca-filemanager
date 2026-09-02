package com.Hrshd1eux.filemanager.core.storage.model

/**
 * Disk capacity and usage statistics for a storage volume.
 */
data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long
) {
    val usedBytes: Long = (totalBytes - freeBytes).coerceAtLeast(0L)

    val usedRatio: Float = if (totalBytes > 0L) {
        (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
}
