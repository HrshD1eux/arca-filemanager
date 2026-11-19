package com.Hrshd1eux.filemanager.core.storage.volume

/**
 * Represents a physical or emulated storage volume (Internal, SD Card, USB OTG drive).
 */
data class PhysicalVolume(
    val id: String,
    val name: String,
    val path: String,
    val isPrimary: Boolean = false,
    val isRemovable: Boolean = false,
    val isUsb: Boolean = false,
    val isAvailable: Boolean = true,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L
) {
    val usedRatio: Float = if (totalBytes > 0L) {
        (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else 0f
}
