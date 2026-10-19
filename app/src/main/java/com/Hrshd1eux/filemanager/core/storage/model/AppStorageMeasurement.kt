package com.Hrshd1eux.filemanager.core.storage.model

import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Declares the provenance of an app storage measurement.
 */
enum class StorageMeasurementSource(val displayName: String) {
    APK_ON_DISK("APK Binary (Disk)"),
    SYSTEM_STORAGE_STATS("System Storage Stats"),
    SELF_INSPECTION("Self-App Storage Inspection"),
    UNAVAILABLE("Unavailable")
}

/**
 * Declares the accuracy and confidence level of a storage metric.
 *
 * Adheres strictly to the architectural rule:
 * Make the interface clear about whether a value is exact, approximate, stale or unavailable.
 * There is no single universally available Android API that lets an ordinary third-party app
 * accurately inspect every byte of every other app's private files and caches.
 */
enum class StorageAccuracy(
    val displayName: String,
    val description: String
) {
    EXACT(
        displayName = "Exact",
        description = "Directly measured from on-disk binary or verified by system StorageStatsManager."
    ),
    APPROXIMATE(
        displayName = "Approximate",
        description = "Inferred, partial measurement, or estimate based on accessible components."
    ),
    STALE(
        displayName = "Stale",
        description = "Measurement from an earlier scan; may not reflect current storage usage."
    ),
    UNAVAILABLE(
        displayName = "Unavailable",
        description = "Restricted by Android platform sandbox or missing Usage Access permission. Never reported as zero."
    )
}

/**
 * Represents an individual storage dimension (Package, APK, Data, or Cache) with explicit accuracy and explanation.
 */
data class StorageComponentMeasurement(
    val bytes: Long?,
    val accuracy: StorageAccuracy,
    val note: String? = null
) {
    val formattedSize: String
        get() = if (bytes != null && accuracy != StorageAccuracy.UNAVAILABLE) {
            FileFormatter.formatFileSize(bytes)
        } else {
            "Unavailable"
        }

    val isAccessible: Boolean
        get() = bytes != null && accuracy != StorageAccuracy.UNAVAILABLE

    companion object {
        fun unavailable(reason: String = "Restricted by Android sandbox"): StorageComponentMeasurement =
            StorageComponentMeasurement(
                bytes = null,
                accuracy = StorageAccuracy.UNAVAILABLE,
                note = reason
            )

        fun exact(bytes: Long, note: String? = null): StorageComponentMeasurement =
            StorageComponentMeasurement(
                bytes = bytes,
                accuracy = StorageAccuracy.EXACT,
                note = note
            )

        fun approximate(bytes: Long, note: String? = null): StorageComponentMeasurement =
            StorageComponentMeasurement(
                bytes = bytes,
                accuracy = StorageAccuracy.APPROXIMATE,
                note = note
            )

        fun stale(bytes: Long, note: String? = null): StorageComponentMeasurement =
            StorageComponentMeasurement(
                bytes = bytes,
                accuracy = StorageAccuracy.STALE,
                note = note
            )
    }
}

/**
 * Storage metrics for an installed application, cleanly separating package, APK, app-data, and cache measurements.
 *
 * Adheres strictly to the Version 1.3.11 architectural constraints:
 * 1. Separate package (runtime footprint, dex, native libraries), APK (raw binaries on disk), app-data, and cache.
 * 2. Total of the displayed components, when the underlying values are available and comparable.
 * 3. Make the interface clear about whether each value is exact, approximate, stale, or unavailable.
 * 4. Never label an inaccessible measurement as zero. Show Unavailable when that is what the system tells you.
 */
data class AppStorageMeasurement(
    val packageComponent: StorageComponentMeasurement,
    val apkComponent: StorageComponentMeasurement = packageComponent,
    val dataComponent: StorageComponentMeasurement = StorageComponentMeasurement.unavailable(),
    val cacheComponent: StorageComponentMeasurement = StorageComponentMeasurement.unavailable(),
    val totalComponent: StorageComponentMeasurement,
    val source: StorageMeasurementSource,
    val measuredAtMillis: Long = System.currentTimeMillis(),
    val isAccessible: Boolean = totalComponent.isAccessible,
    val inaccessibilityReason: String? = null
) {
    /**
     * Secondary constructor for backward compatibility with existing tests and calls passing `codeComponent`.
     */
    constructor(
        codeComponent: StorageComponentMeasurement,
        dataComponent: StorageComponentMeasurement = StorageComponentMeasurement.unavailable(),
        cacheComponent: StorageComponentMeasurement = StorageComponentMeasurement.unavailable(),
        totalComponent: StorageComponentMeasurement,
        source: StorageMeasurementSource,
        measuredAtMillis: Long = System.currentTimeMillis(),
        isAccessible: Boolean = totalComponent.isAccessible,
        inaccessibilityReason: String? = null,
        apkComponent: StorageComponentMeasurement = codeComponent
    ) : this(
        packageComponent = codeComponent,
        apkComponent = apkComponent,
        dataComponent = dataComponent,
        cacheComponent = cacheComponent,
        totalComponent = totalComponent,
        source = source,
        measuredAtMillis = measuredAtMillis,
        isAccessible = isAccessible,
        inaccessibilityReason = inaccessibilityReason
    )

    /**
     * Backward-compatible property mapping to packageComponent if accessible, else apkComponent.
     */
    val codeComponent: StorageComponentMeasurement
        get() = if (packageComponent.isAccessible) packageComponent else apkComponent

    // Convenience / backward-compatible properties
    val totalBytes: Long? get() = totalComponent.bytes
    val codeBytes: Long? get() = codeComponent.bytes
    val packageBytes: Long? get() = packageComponent.bytes
    val apkBytes: Long? get() = apkComponent.bytes
    val dataBytes: Long? get() = dataComponent.bytes
    val cacheBytes: Long? get() = cacheComponent.bytes

    val formattedTotalSize: String get() = totalComponent.formattedSize
    val totalAccuracy: StorageAccuracy get() = totalComponent.accuracy

    fun getFreshnessLabel(currentTimeMillis: Long = System.currentTimeMillis()): String {
        val diff = (currentTimeMillis - measuredAtMillis).coerceAtLeast(0L)
        return when {
            diff < 30_000L -> "Measured just now"
            diff < 60_000L -> "Measured less than a min ago"
            diff < 3600_000L -> "Measured ${diff / 60_000L}m ago"
            diff < 86400_000L -> "Measured ${diff / 3600_000L}h ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                "Measured on ${sdf.format(Date(measuredAtMillis))}"
            }
        }
    }

    fun isStale(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        // Cached measurements older than 30 minutes are considered stale
        return (currentTimeMillis - measuredAtMillis) > 30 * 60 * 1000L
    }

    /**
     * Produces a copy marked as stale when loaded from older caches.
     */
    fun asStale(): AppStorageMeasurement {
        return copy(
            packageComponent = if (packageComponent.isAccessible) packageComponent.copy(accuracy = StorageAccuracy.STALE) else packageComponent,
            apkComponent = if (apkComponent.isAccessible) apkComponent.copy(accuracy = StorageAccuracy.STALE) else apkComponent,
            dataComponent = if (dataComponent.isAccessible) dataComponent.copy(accuracy = StorageAccuracy.STALE) else dataComponent,
            cacheComponent = if (cacheComponent.isAccessible) cacheComponent.copy(accuracy = StorageAccuracy.STALE) else cacheComponent,
            totalComponent = if (totalComponent.isAccessible) totalComponent.copy(accuracy = StorageAccuracy.STALE) else totalComponent
        )
    }

    companion object {
        fun unavailable(reason: String = "Storage measurement restricted by system"): AppStorageMeasurement {
            val unavail = StorageComponentMeasurement.unavailable(reason)
            return AppStorageMeasurement(
                packageComponent = unavail,
                apkComponent = unavail,
                dataComponent = unavail,
                cacheComponent = unavail,
                totalComponent = unavail,
                source = StorageMeasurementSource.UNAVAILABLE,
                measuredAtMillis = System.currentTimeMillis(),
                isAccessible = false,
                inaccessibilityReason = reason
            )
        }

        fun fromApkFiles(
            totalSize: Long,
            timestampMillis: Long = System.currentTimeMillis(),
            splitCount: Int = 0
        ): AppStorageMeasurement {
            return if (totalSize > 0L) {
                val splitNote = if (splitCount > 0) "Base APK + $splitCount splits on disk" else "Base APK binary on disk"
                val apk = StorageComponentMeasurement.exact(totalSize, splitNote)
                val pkg = StorageComponentMeasurement.approximate(
                    totalSize,
                    "Installed package footprint (Code only; dex and runtime restricted without Usage Access)"
                )
                val data = StorageComponentMeasurement.unavailable("Requires Usage Access permission to inspect app data")
                val cache = StorageComponentMeasurement.unavailable("Requires Usage Access permission to inspect app cache")
                // Total of displayed components: only code is accessible, so total is approximate with respect to the app's full storage
                val total = StorageComponentMeasurement.approximate(
                    totalSize,
                    "Total of accessible components (APK files only; data and cache restricted)"
                )
                AppStorageMeasurement(
                    packageComponent = pkg,
                    apkComponent = apk,
                    dataComponent = data,
                    cacheComponent = cache,
                    totalComponent = total,
                    source = StorageMeasurementSource.APK_ON_DISK,
                    measuredAtMillis = timestampMillis,
                    isAccessible = true
                )
            } else {
                unavailable("Package binary not accessible or 0 bytes")
            }
        }

        fun fromSystemStats(
            appBytes: Long,
            dataBytes: Long,
            cacheBytes: Long,
            rawApkBytes: Long? = null,
            splitCount: Int = 0,
            timestampMillis: Long = System.currentTimeMillis(),
            isSelf: Boolean = false
        ): AppStorageMeasurement {
            val pkg = StorageComponentMeasurement.exact(appBytes, "Installed package footprint (code, dex, native libs)")
            val apk = if (rawApkBytes != null && rawApkBytes > 0L) {
                val splitNote = if (splitCount > 0) "Base APK + $splitCount splits on disk" else "Base APK binary on disk"
                StorageComponentMeasurement.exact(rawApkBytes, splitNote)
            } else {
                StorageComponentMeasurement.approximate(appBytes, "Reported package code size")
            }
            val data = StorageComponentMeasurement.exact(dataBytes, "App-reported private data")
            val cache = StorageComponentMeasurement.exact(cacheBytes, "App-reported cache")
            val totalBytes = appBytes + dataBytes + cacheBytes
            val total = StorageComponentMeasurement.exact(
                totalBytes,
                "Exact total of displayed components (Package + Data + Cache)"
            )
            return AppStorageMeasurement(
                packageComponent = pkg,
                apkComponent = apk,
                dataComponent = data,
                cacheComponent = cache,
                totalComponent = total,
                source = if (isSelf) StorageMeasurementSource.SELF_INSPECTION else StorageMeasurementSource.SYSTEM_STORAGE_STATS,
                measuredAtMillis = timestampMillis,
                isAccessible = true
            )
        }

        fun fromSelfInspection(
            apkBytes: Long,
            packageBytes: Long,
            dataBytes: Long,
            cacheBytes: Long,
            timestampMillis: Long = System.currentTimeMillis()
        ): AppStorageMeasurement {
            val apk = StorageComponentMeasurement.exact(apkBytes, "ARCA on-disk APK binary")
            val pkg = StorageComponentMeasurement.exact(packageBytes, "ARCA package runtime code")
            val data = StorageComponentMeasurement.exact(dataBytes, "ARCA private data directory")
            val cache = StorageComponentMeasurement.exact(cacheBytes, "ARCA internal cache directory")
            val totalBytes = packageBytes + dataBytes + cacheBytes
            val total = StorageComponentMeasurement.exact(totalBytes, "Exact total of ARCA components")
            return AppStorageMeasurement(
                packageComponent = pkg,
                apkComponent = apk,
                dataComponent = data,
                cacheComponent = cache,
                totalComponent = total,
                source = StorageMeasurementSource.SELF_INSPECTION,
                measuredAtMillis = timestampMillis,
                isAccessible = true
            )
        }

        /**
         * Secondary constructor for backward compatibility with existing tests and calls.
         */
        operator fun invoke(
            totalBytes: Long?,
            codeBytes: Long? = null,
            dataBytes: Long? = null,
            cacheBytes: Long? = null,
            source: StorageMeasurementSource,
            measuredAtMillis: Long = System.currentTimeMillis(),
            isAccessible: Boolean = totalBytes != null,
            inaccessibilityReason: String? = null
        ): AppStorageMeasurement {
            if (totalBytes == null || !isAccessible) {
                return unavailable(inaccessibilityReason ?: "Storage measurement restricted by system")
            }

            val code = if (codeBytes != null) {
                StorageComponentMeasurement.exact(codeBytes)
            } else {
                StorageComponentMeasurement.exact(totalBytes)
            }

            val data = if (dataBytes != null) {
                StorageComponentMeasurement.exact(dataBytes)
            } else {
                StorageComponentMeasurement.unavailable("Data size not exposed")
            }

            val cache = if (cacheBytes != null) {
                StorageComponentMeasurement.exact(cacheBytes)
            } else {
                StorageComponentMeasurement.unavailable("Cache size not exposed")
            }

            val totalAccuracy = if (dataBytes != null && cacheBytes != null) {
                StorageAccuracy.EXACT
            } else {
                StorageAccuracy.APPROXIMATE
            }

            val total = StorageComponentMeasurement(
                bytes = totalBytes,
                accuracy = totalAccuracy,
                note = if (totalAccuracy == StorageAccuracy.EXACT) "Exact total of displayed components" else "Total of accessible components"
            )

            return AppStorageMeasurement(
                packageComponent = code,
                apkComponent = code,
                dataComponent = data,
                cacheComponent = cache,
                totalComponent = total,
                source = source,
                measuredAtMillis = measuredAtMillis,
                isAccessible = true,
                inaccessibilityReason = inaccessibilityReason
            )
        }
    }
}
