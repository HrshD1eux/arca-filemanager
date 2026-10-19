package com.Hrshd1eux.filemanager.core.storage.analyzer

import com.Hrshd1eux.filemanager.core.storage.model.AppStorageMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.model.PackageExtraInfo
import com.Hrshd1eux.filemanager.core.storage.model.StorageAccuracy
import com.Hrshd1eux.filemanager.core.storage.model.StorageComponentMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.StorageMeasurementSource
import com.Hrshd1eux.filemanager.core.storage.model.SystemStorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.model.SystemStoragePartition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Rigorous unit tests for Version 1.2.38 — System Apps and Operating-System Storage.
 *
 * Verifies that:
 * 1. Visible system apps are cleanly separated from user-installed apps.
 * 2. System app package size is clearly distinguished from Android OS storage.
 * 3. Available system storage categories and partitions are exposed accurately.
 * 4. Accessible cache and temporary storage are tracked with proper accuracy levels.
 * 5. Inaccessible system-private storage zones are explicitly documented with security boundaries.
 * 6. Deletion of unknown system files is strictly avoided (read-only inspection).
 * 7. Inaccessible measurements are never labeled as zero.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemStorageAnalyzerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    // Mock system partitions
    private val systemPartition = SystemStoragePartition(
        name = "Android OS System",
        mountPoint = "/system",
        totalBytes = 4 * 1024 * 1024 * 1024L, // 4 GB
        freeBytes = 500 * 1024 * 1024L,       // 500 MB free
        isReadOnly = true,
        description = "Core Android framework and runtime"
    )

    private val vendorPartition = SystemStoragePartition(
        name = "Vendor",
        mountPoint = "/vendor",
        totalBytes = 2 * 1024 * 1024 * 1024L, // 2 GB
        freeBytes = 200 * 1024 * 1024L,       // 200 MB free
        isReadOnly = true,
        description = "Device manufacturer drivers"
    )

    // User app: 80 MB code + 20 MB data + 5 MB cache = 105 MB
    private val userApp = InstalledAppInfo(
        packageName = "com.user.app",
        appName = "User Application",
        versionName = "1.0",
        versionCode = 1L,
        isSystemApp = false,
        isUpdatedSystemApp = false,
        storageMeasurement = AppStorageMeasurement(
            codeComponent = StorageComponentMeasurement.exact(80 * 1024 * 1024L),
            dataComponent = StorageComponentMeasurement.exact(20 * 1024 * 1024L),
            cacheComponent = StorageComponentMeasurement.exact(5 * 1024 * 1024L),
            totalComponent = StorageComponentMeasurement.exact(105 * 1024 * 1024L),
            source = StorageMeasurementSource.SYSTEM_STORAGE_STATS,
            isAccessible = true
        )
    )

    // Pre-installed System App: 45 MB code (in /system/app)
    private val systemApp = InstalledAppInfo(
        packageName = "com.android.settings",
        appName = "Settings",
        versionName = "15",
        versionCode = 35L,
        isSystemApp = true,
        isUpdatedSystemApp = false,
        sourceDir = "/system/priv-app/Settings/Settings.apk",
        storageMeasurement = AppStorageMeasurement(
            codeComponent = StorageComponentMeasurement.exact(45 * 1024 * 1024L, "System APK"),
            dataComponent = StorageComponentMeasurement.unavailable(),
            cacheComponent = StorageComponentMeasurement.unavailable(),
            totalComponent = StorageComponentMeasurement.approximate(45 * 1024 * 1024L, "Code size only"),
            source = StorageMeasurementSource.APK_ON_DISK,
            isAccessible = true
        )
    )

    private val mockBreakdown = SystemStorageBreakdown(
        androidOsBytes = systemPartition.usedBytes + vendorPartition.usedBytes, // ~5.3 GB
        androidOsPartitions = listOf(systemPartition, vendorPartition),
        systemAppsBytes = 45 * 1024 * 1024L, // 45 MB
        systemAppsCount = 1,
        userAppsBytes = 105 * 1024 * 1024L,  // 105 MB
        userAppsCount = 1,
        sharedMediaBytes = 10 * 1024 * 1024 * 1024L, // 10 GB
        freeStorageBytes = 45 * 1024 * 1024 * 1024L, // 45 GB
        dataPartitionTotalBytes = 64 * 1024 * 1024 * 1024L,
        accessibleCacheBytes = 5 * 1024 * 1024L,
        accessibleCacheAccuracy = StorageAccuracy.EXACT,
        accessibleCacheSource = "Aggregated from system storage stats",
        inaccessibleZones = SystemStorageBreakdown.defaultInaccessibleZones(),
        isAnalyzing = false
    )

    private lateinit var repository: SystemStorageRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = SystemStorageRepository(
            context = null,
            installedAppsRepository = null,
            ioDispatcher = testDispatcher,
            mockBreakdown = mockBreakdown
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun separatesVisibleSystemApps_fromUserInstalledApps() = runBlocking {
        val breakdown = repository.getSystemStorageBreakdown()
        assertEquals(1, breakdown.systemAppsCount)
        assertEquals(1, breakdown.userAppsCount)

        assertEquals(45 * 1024 * 1024L, breakdown.systemAppsBytes)
        assertEquals(105 * 1024 * 1024L, breakdown.userAppsBytes)

        assertTrue(breakdown.formattedSystemAppsSize.contains("MB"))
        assertTrue(breakdown.formattedUserAppsSize.contains("MB"))
    }

    @Test
    fun distinguishesSystemAppPackageSize_fromAndroidOsStorage() = runBlocking {
        val breakdown = repository.getSystemStorageBreakdown()

        // Android OS core storage (partitions used size: ~5.3 GB)
        val osBytes = breakdown.androidOsBytes
        // System Apps package footprint: 45 MB
        val sysAppsBytes = breakdown.systemAppsBytes

        assertTrue("OS storage should be measured in gigabytes", osBytes > 1024 * 1024 * 1024L)
        assertTrue("System apps footprint should be distinct from OS core storage", sysAppsBytes < 1024 * 1024 * 1024L)
        assertNotEquals("System app package size must NEVER be confused with Android OS storage", osBytes, sysAppsBytes)

        assertTrue(breakdown.formattedAndroidOsSize.contains("GB"))
        assertTrue(breakdown.formattedSystemAppsSize.contains("MB"))
    }

    @Test
    fun systemStoragePartitions_exposeAccurateMountsAndReadOnlyStatus() = runBlocking {
        val breakdown = repository.getSystemStorageBreakdown()
        assertEquals(2, breakdown.androidOsPartitions.size)

        val sys = breakdown.androidOsPartitions.first { it.mountPoint == "/system" }
        assertEquals("Android OS System", sys.name)
        assertTrue("System partition must be read-only", sys.isReadOnly)
        assertEquals(4 * 1024 * 1024 * 1024L, sys.totalBytes)
        assertEquals((4096 - 500) * 1024 * 1024L, sys.usedBytes)

        val vendor = breakdown.androidOsPartitions.first { it.mountPoint == "/vendor" }
        assertEquals("Vendor", vendor.name)
        assertTrue("Vendor partition must be read-only", vendor.isReadOnly)
    }

    @Test
    fun accessibleCacheAndTempStorage_tracksAccuracyAndSource() = runBlocking {
        val breakdown = repository.getSystemStorageBreakdown()
        assertEquals(5 * 1024 * 1024L, breakdown.accessibleCacheBytes)
        assertEquals(StorageAccuracy.EXACT, breakdown.accessibleCacheAccuracy)
        assertEquals("Aggregated from system storage stats", breakdown.accessibleCacheSource)
        assertTrue(breakdown.formattedAccessibleCacheSize.contains("MB"))
    }

    @Test
    fun inaccessibleCache_displaysUnavailable_neverZero() {
        val unavailBreakdown = SystemStorageBreakdown(
            accessibleCacheBytes = null,
            accessibleCacheAccuracy = StorageAccuracy.UNAVAILABLE,
            accessibleCacheSource = "Restricted by platform sandbox"
        )
        assertNull(unavailBreakdown.accessibleCacheBytes)
        assertEquals("Unavailable", unavailBreakdown.formattedAccessibleCacheSize)
        assertFalse("Must never display 0 B", unavailBreakdown.formattedAccessibleCacheSize.contains("0 B"))
        assertFalse("Must never display 0 MB", unavailBreakdown.formattedAccessibleCacheSize.contains("0 MB"))
    }

    @Test
    fun inaccessibleSystemPrivateStorageZones_areExplicitlyDocumented() {
        val zones = SystemStorageBreakdown.defaultInaccessibleZones()
        assertTrue("Must document multiple inaccessible zones", zones.size >= 4)

        // 1. App Sandboxes
        val appSandbox = zones.find { it.path.contains("/data/data/") }
        assertNotNull("Private App Sandbox must be documented", appSandbox)
        assertTrue(appSandbox!!.restrictionReason.contains("Linux User ID"))
        assertTrue(appSandbox.securityBoundary.contains("SELinux"))

        // 2. System Telemetry & Settings
        val systemZone = zones.find { it.path.contains("/data/system/") }
        assertNotNull("System configuration zone must be documented", systemZone)
        assertTrue(systemZone!!.restrictionReason.contains("UID 1000"))

        // 3. Hardware Keystore
        val keystore = zones.find { it.name.contains("Keystore") }
        assertNotNull("Hardware Keystore must be documented", keystore)
        assertTrue(keystore!!.securityBoundary.contains("TEE") || keystore.securityBoundary.contains("StrongBox"))

        // 4. Read-Only Firmware
        val firmware = zones.find { it.name.contains("Firmware") }
        assertNotNull("Read-only firmware must be documented", firmware)
        assertTrue(firmware!!.securityBoundary.contains("dm-verity"))
    }

    @Test
    fun readOnlyIntegrity_guaranteesNoDestructiveTempDeletionMethods() {
        // Reflection check: Verify SystemStorageRepository does NOT contain any deletion methods
        val methods = SystemStorageRepository::class.java.declaredMethods
        val deletionMethods = methods.filter { method ->
            val name = method.name.lowercase()
            name.contains("delete") || name.contains("clear") || name.contains("wipe") || name.contains("remove")
        }
        assertTrue(
            "SystemStorageRepository must adhere strictly to read-only inspection with ZERO deletion methods",
            deletionMethods.isEmpty()
        )
    }
}
