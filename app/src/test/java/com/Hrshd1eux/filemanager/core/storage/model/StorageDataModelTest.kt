package com.Hrshd1eux.filemanager.core.storage.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying the ARCA Storage Data Model (Version 1.3.11).
 *
 * Verifies:
 * 1. Complete separation of the 5 storage measurement tiers:
 *    - Package Footprint (code, dex, native libs)
 *    - APK Binary (base + split APK files on disk)
 *    - App-Data (private sandboxed data)
 *    - Cache (temporary files & code caches)
 *    - System Storage (Android OS partitions vs system apps)
 * 2. Proper provenance, accuracy levels (Exact, Approximate, Stale, Unavailable), and formatting.
 * 3. Inaccessible measurements display "Unavailable" and are NEVER labeled as zero.
 * 4. Backward compatibility for `codeComponent` and legacy constructor invocations.
 */
class StorageDataModelTest {

    @Test
    fun storageMeasurementTiers_definesAllFiveDistinctTiers() {
        val tiers = StorageMeasurementTier.entries
        assertEquals(5, tiers.size)

        val expected = setOf(
            StorageMeasurementTier.PACKAGE,
            StorageMeasurementTier.APK,
            StorageMeasurementTier.APP_DATA,
            StorageMeasurementTier.CACHE,
            StorageMeasurementTier.SYSTEM_STORAGE
        )
        assertEquals(expected, tiers.toSet())

        // System storage tier must be marked as system level
        assertTrue(StorageMeasurementTier.SYSTEM_STORAGE.isSystemLevel)
        assertFalse(StorageMeasurementTier.PACKAGE.isSystemLevel)
        assertFalse(StorageMeasurementTier.APK.isSystemLevel)
        assertFalse(StorageMeasurementTier.APP_DATA.isSystemLevel)
        assertFalse(StorageMeasurementTier.CACHE.isSystemLevel)
    }

    @Test
    fun fromSystemStats_separatesPackageFromApkAndAppDataAndCache() {
        // App with 95 MB package runtime size (including dex/native libs) and 70 MB raw APK on disk
        val measurement = AppStorageMeasurement.fromSystemStats(
            appBytes = 95 * 1024 * 1024L,
            dataBytes = 30 * 1024 * 1024L,
            cacheBytes = 15 * 1024 * 1024L,
            rawApkBytes = 70 * 1024 * 1024L,
            splitCount = 2,
            isSelf = false
        )

        // 1. Package measurement (Installed package runtime footprint)
        assertEquals(95 * 1024 * 1024L, measurement.packageBytes)
        assertEquals(StorageAccuracy.EXACT, measurement.packageComponent.accuracy)
        assertTrue(measurement.packageComponent.note?.contains("native libs") == true)

        // 2. APK measurement (Raw on-disk binaries)
        assertEquals(70 * 1024 * 1024L, measurement.apkBytes)
        assertEquals(StorageAccuracy.EXACT, measurement.apkComponent.accuracy)
        assertTrue(measurement.apkComponent.note?.contains("splits") == true)

        // Package footprint differs from raw APK size on disk
        assertNotEquals(measurement.packageBytes, measurement.apkBytes)

        // 3. App-Data measurement
        assertEquals(30 * 1024 * 1024L, measurement.dataBytes)
        assertEquals(StorageAccuracy.EXACT, measurement.dataComponent.accuracy)

        // 4. Cache measurement
        assertEquals(15 * 1024 * 1024L, measurement.cacheBytes)
        assertEquals(StorageAccuracy.EXACT, measurement.cacheComponent.accuracy)

        // Total
        val expectedTotal = (95 + 30 + 15) * 1024 * 1024L
        assertEquals(expectedTotal, measurement.totalBytes)
        assertEquals(StorageAccuracy.EXACT, measurement.totalAccuracy)

        // Backward compatibility
        assertEquals(measurement.packageComponent, measurement.codeComponent)
        assertEquals(measurement.packageBytes, measurement.codeBytes)
    }

    @Test
    fun fromApkFiles_setsApkExact_packageApproximate_dataAndCacheUnavailable() {
        val measurement = AppStorageMeasurement.fromApkFiles(
            totalSize = 45 * 1024 * 1024L,
            splitCount = 1
        )

        // APK is exact from disk
        assertEquals(45 * 1024 * 1024L, measurement.apkBytes)
        assertEquals(StorageAccuracy.EXACT, measurement.apkComponent.accuracy)

        // Package footprint is approximate when Usage Access is not granted
        assertEquals(45 * 1024 * 1024L, measurement.packageBytes)
        assertEquals(StorageAccuracy.APPROXIMATE, measurement.packageComponent.accuracy)

        // App-data and cache are unavailable
        assertNull(measurement.dataBytes)
        assertNull(measurement.cacheBytes)
        assertEquals(StorageAccuracy.UNAVAILABLE, measurement.dataComponent.accuracy)
        assertEquals(StorageAccuracy.UNAVAILABLE, measurement.cacheComponent.accuracy)
        assertEquals("Unavailable", measurement.dataComponent.formattedSize)
        assertEquals("Unavailable", measurement.cacheComponent.formattedSize)

        // Total is approximate
        assertEquals(45 * 1024 * 1024L, measurement.totalBytes)
        assertEquals(StorageAccuracy.APPROXIMATE, measurement.totalAccuracy)
    }

    @Test
    fun fromSelfInspection_recordsAllComponentsExact() {
        val measurement = AppStorageMeasurement.fromSelfInspection(
            apkBytes = 14 * 1024 * 1024L,
            packageBytes = 14 * 1024 * 1024L,
            dataBytes = 5 * 1024 * 1024L,
            cacheBytes = 2 * 1024 * 1024L
        )

        assertEquals(14 * 1024 * 1024L, measurement.apkBytes)
        assertEquals(14 * 1024 * 1024L, measurement.packageBytes)
        assertEquals(5 * 1024 * 1024L, measurement.dataBytes)
        assertEquals(2 * 1024 * 1024L, measurement.cacheBytes)
        assertEquals(21 * 1024 * 1024L, measurement.totalBytes)

        assertEquals(StorageAccuracy.EXACT, measurement.packageComponent.accuracy)
        assertEquals(StorageAccuracy.EXACT, measurement.apkComponent.accuracy)
        assertEquals(StorageAccuracy.EXACT, measurement.dataComponent.accuracy)
        assertEquals(StorageAccuracy.EXACT, measurement.cacheComponent.accuracy)
        assertEquals(StorageAccuracy.EXACT, measurement.totalAccuracy)
        assertEquals(StorageMeasurementSource.SELF_INSPECTION, measurement.source)
    }

    @Test
    fun unavailableMeasurement_neverDisplaysZero() {
        val measurement = AppStorageMeasurement.unavailable("Inspection restricted by platform")

        assertFalse(measurement.isAccessible)
        assertNull(measurement.packageBytes)
        assertNull(measurement.apkBytes)
        assertNull(measurement.dataBytes)
        assertNull(measurement.cacheBytes)
        assertNull(measurement.totalBytes)

        assertEquals("Unavailable", measurement.formattedTotalSize)
        assertEquals("Unavailable", measurement.packageComponent.formattedSize)
        assertEquals("Unavailable", measurement.apkComponent.formattedSize)
        assertEquals("Unavailable", measurement.dataComponent.formattedSize)
        assertEquals("Unavailable", measurement.cacheComponent.formattedSize)

        assertFalse(measurement.formattedTotalSize.contains("0 B"))
        assertFalse(measurement.formattedTotalSize.contains("0 MB"))
    }

    @Test
    fun systemStorageMeasurement_distinguishesFirmwareFromAppStorage() {
        val partition = SystemStoragePartition(
            name = "System OS",
            mountPoint = "/system",
            totalBytes = 6 * 1024 * 1024 * 1024L,
            freeBytes = 800 * 1024 * 1024L,
            isReadOnly = true,
            description = "Android OS firmware"
        )

        val breakdown = SystemStorageBreakdown(
            androidOsBytes = partition.usedBytes,
            androidOsPartitions = listOf(partition),
            systemAppsBytes = 120 * 1024 * 1024L,
            systemAppsCount = 8,
            userAppsBytes = 500 * 1024 * 1024L,
            userAppsCount = 12,
            sharedMediaBytes = 2 * 1024 * 1024 * 1024L
        )

        // Tier 5: System storage is distinct from per-app package storage
        assertTrue("Android OS must be > 1 GB", breakdown.androidOsBytes > 1024 * 1024 * 1024L)
        assertTrue("System apps package storage is ~120 MB", breakdown.systemAppsBytes < 1024 * 1024 * 1024L)
        assertNotEquals(breakdown.androidOsBytes, breakdown.systemAppsBytes)
        assertNotNull(breakdown.inaccessibleZones)
        assertTrue(breakdown.inaccessibleZones.isNotEmpty())
    }
}
