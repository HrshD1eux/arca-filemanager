package com.Hrshd1eux.filemanager.core.storage.model

import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter

/**
 * Information about a mounted platform storage partition (e.g. System, Vendor, Data).
 */
data class SystemStoragePartition(
    val name: String,
    val mountPoint: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isReadOnly: Boolean,
    val description: String
) {
    val usedBytes: Long
        get() = (totalBytes - freeBytes).coerceAtLeast(0L)

    val formattedTotalSize: String
        get() = FileFormatter.formatFileSize(totalBytes)

    val formattedUsedSize: String
        get() = FileFormatter.formatFileSize(usedBytes)

    val formattedFreeSize: String
        get() = FileFormatter.formatFileSize(freeBytes)
}

/**
 * Explicitly identifies an inaccessible system-private storage zone and documents
 * the platform security boundary preventing inspection.
 *
 * Adheres strictly to the architectural constraints:
 * 1. Identify inaccessible system-private storage explicitly.
 * 2. Explain why some values cannot be inspected.
 * 3. Never imply that ARCA can browse arbitrary system-private directories.
 * 4. Avoid recommending deletion of unknown system files.
 */
data class InaccessibleStorageZone(
    val name: String,
    val path: String,
    val restrictionReason: String,
    val securityBoundary: String
)

/**
 * Comprehensive breakdown of operating system storage, pre-installed system apps,
 * user-installed apps, shared media, and system-private partitions.
 *
 * Key principle: Distinguish system app package size from Android OS storage!
 */
data class SystemStorageBreakdown(
    val androidOsBytes: Long = 0L,
    val androidOsPartitions: List<SystemStoragePartition> = emptyList(),
    val systemAppsBytes: Long = 0L,
    val systemAppsCount: Int = 0,
    val userAppsBytes: Long = 0L,
    val userAppsCount: Int = 0,
    val sharedMediaBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val dataPartitionTotalBytes: Long = 0L,
    val accessibleCacheBytes: Long? = null,
    val accessibleCacheAccuracy: StorageAccuracy = StorageAccuracy.UNAVAILABLE,
    val accessibleCacheSource: String = "Not exposed by platform",
    val inaccessibleZones: List<InaccessibleStorageZone> = defaultInaccessibleZones(),
    val isAnalyzing: Boolean = false,
    val measuredAtMillis: Long = System.currentTimeMillis()
) {
    val formattedAndroidOsSize: String
        get() = if (androidOsBytes > 0L) FileFormatter.formatFileSize(androidOsBytes) else "Unavailable"

    val formattedSystemAppsSize: String
        get() = if (systemAppsBytes > 0L) FileFormatter.formatFileSize(systemAppsBytes) else "Unavailable"

    val formattedUserAppsSize: String
        get() = if (userAppsBytes > 0L) FileFormatter.formatFileSize(userAppsBytes) else "Unavailable"

    val formattedSharedMediaSize: String
        get() = FileFormatter.formatFileSize(sharedMediaBytes)

    val formattedFreeSize: String
        get() = FileFormatter.formatFileSize(freeStorageBytes)

    val formattedAccessibleCacheSize: String
        get() = if (accessibleCacheBytes != null && accessibleCacheAccuracy != StorageAccuracy.UNAVAILABLE) {
            FileFormatter.formatFileSize(accessibleCacheBytes)
        } else {
            "Unavailable"
        }

    companion object {
        fun defaultInaccessibleZones(): List<InaccessibleStorageZone> = listOf(
            InaccessibleStorageZone(
                name = "Private Application Sandboxes",
                path = "/data/data/ and /data/user/",
                restrictionReason = "Android assigns every app a distinct Linux User ID (UID). Linux kernel permissions and SELinux prevent any app from reading another app's databases, credentials, or internal files.",
                securityBoundary = "Linux UID Separation & SELinux App Sandbox"
            ),
            InaccessibleStorageZone(
                name = "System Configuration & Telemetry",
                path = "/data/system/ and /data/misc/",
                restrictionReason = "Restricted to UID 1000 (system) and UID 0 (root). Houses Android package registries, Wi-Fi credentials, battery stats, and OS runtime state.",
                securityBoundary = "System UID 1000 Ownership"
            ),
            InaccessibleStorageZone(
                name = "Hardware Keystore & Security Keys",
                path = "/data/misc/keystore/ & TEE",
                restrictionReason = "Cryptographic master keys are guarded in isolated hardware security enclaves (Trusted Execution Environment or StrongBox) and never exposed to userspace filesystems.",
                securityBoundary = "Hardware Enclave (TEE / StrongBox)"
            ),
            InaccessibleStorageZone(
                name = "Read-Only Firmware Partitions",
                path = "/system, /vendor, /product, /odm",
                restrictionReason = "System operating system code is mounted strictly read-only and cryptographically verified at boot time via dm-verity / Android Verified Boot (AVB).",
                securityBoundary = "dm-verity Cryptographic Hash Tree"
            )
        )
    }
}
