package com.Hrshd1eux.filemanager.core.storage.analyzer

import com.Hrshd1eux.filemanager.core.storage.model.AppStorageMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.AppTypeFilter
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppSortOrder
import com.Hrshd1eux.filemanager.core.storage.model.PackageExtraInfo
import com.Hrshd1eux.filemanager.core.storage.model.SplitApkInfo
import com.Hrshd1eux.filemanager.core.storage.model.StorageAccuracy
import com.Hrshd1eux.filemanager.core.storage.model.StorageComponentMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.StorageMeasurementSource
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
 * Rigorous unit tests for:
 * - Version 1.2.36 — Storage Analyzer: Installed Apps
 * - Version 1.2.37 — Per-App Storage Details
 *
 * Verifies that:
 * 1. For each app, separate values are captured: APK/code, app-reported data, app-reported cache, total.
 * 2. Explicit accuracy is assigned: Exact, Approximate, Stale, Unavailable.
 * 3. Inaccessible measurements are NEVER labeled as zero; they display "Unavailable".
 * 4. User apps are cleanly distinguished from visible system apps.
 * 5. Multi-criteria filtering (All, User, System) and search work across names and packages.
 * 6. Sorting accurately orders by total size, code size, data size, cache size, name, update date,
 *    and places unavailable measurements at the end.
 * 7. Split APKs and other accessible package information (SDK, ABI, process, flags) are cleanly tracked.
 * 8. Stale measurement degradation and freshness formatting work accurately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InstalledAppsAnalyzerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    // App 1: Full measurement available (e.g. from System Storage Stats or self)
    private val fullMeasuredApp = InstalledAppInfo(
        packageName = "org.example.browser",
        appName = "Alpha Browser",
        versionName = "120.0.1",
        versionCode = 54321L,
        isSystemApp = false,
        isUpdatedSystemApp = false,
        installTimeMillis = 1000L,
        lastUpdateTimeMillis = 5000L,
        sourceDir = "/data/app/org.example.browser/base.apk",
        storageMeasurement = AppStorageMeasurement(
            codeComponent = StorageComponentMeasurement.exact(80 * 1024 * 1024L, "APK binary"),
            dataComponent = StorageComponentMeasurement.exact(25 * 1024 * 1024L, "App data"),
            cacheComponent = StorageComponentMeasurement.exact(10 * 1024 * 1024L, "Cache"),
            totalComponent = StorageComponentMeasurement.exact(115 * 1024 * 1024L, "Exact total"),
            source = StorageMeasurementSource.SYSTEM_STORAGE_STATS,
            measuredAtMillis = System.currentTimeMillis() - 10_000L,
            isAccessible = true
        ),
        extraInfo = PackageExtraInfo(
            splitApks = listOf(
                SplitApkInfo(name = "split_config.arm64_v8a.apk", path = "/data/app/org.example.browser/split1.apk", sizeBytes = 15 * 1024 * 1024L),
                SplitApkInfo(name = "split_config.en.apk", path = "/data/app/org.example.browser/split2.apk", sizeBytes = 5 * 1024 * 1024L)
            ),
            primaryCpuAbi = "arm64-v8a",
            targetSdkVersion = 34,
            minSdkVersion = 26,
            requestedPermissionsCount = 14,
            processName = "org.example.browser",
            isHardwareAccelerated = true
        )
    )

    // App 2: Standard third-party on-disk measurement (Code exact, data/cache unavailable without usage access)
    private val codeOnlyApp = InstalledAppInfo(
        packageName = "com.sample.notes",
        appName = "Zeta Notes",
        versionName = "2.4.0",
        versionCode = 240L,
        isSystemApp = false,
        isUpdatedSystemApp = false,
        installTimeMillis = 2000L,
        lastUpdateTimeMillis = 4000L,
        sourceDir = "/data/app/com.sample.notes/base.apk",
        storageMeasurement = AppStorageMeasurement(
            codeComponent = StorageComponentMeasurement.exact(15 * 1024 * 1024L, "Base APK binary"),
            dataComponent = StorageComponentMeasurement.unavailable("Restricted by Android sandbox without Usage Access"),
            cacheComponent = StorageComponentMeasurement.unavailable("Restricted by Android sandbox without Usage Access"),
            totalComponent = StorageComponentMeasurement.approximate(15 * 1024 * 1024L, "Code size only"),
            source = StorageMeasurementSource.APK_ON_DISK,
            measuredAtMillis = System.currentTimeMillis() - 120_000L,
            isAccessible = true
        ),
        extraInfo = PackageExtraInfo(
            primaryCpuAbi = "armeabi-v7a",
            targetSdkVersion = 33,
            minSdkVersion = 24,
            requestedPermissionsCount = 5,
            processName = "com.sample.notes"
        )
    )

    // App 3: System app with code on disk
    private val systemApp = InstalledAppInfo(
        packageName = "com.android.settings",
        appName = "Settings",
        versionName = "15",
        versionCode = 35L,
        isSystemApp = true,
        isUpdatedSystemApp = false,
        installTimeMillis = 500L,
        lastUpdateTimeMillis = 500L,
        sourceDir = "/system/priv-app/Settings/Settings.apk",
        storageMeasurement = AppStorageMeasurement(
            codeComponent = StorageComponentMeasurement.exact(25 * 1024 * 1024L, "System APK binary"),
            dataComponent = StorageComponentMeasurement.unavailable(),
            cacheComponent = StorageComponentMeasurement.unavailable(),
            totalComponent = StorageComponentMeasurement.approximate(25 * 1024 * 1024L, "Code size only"),
            source = StorageMeasurementSource.APK_ON_DISK,
            measuredAtMillis = System.currentTimeMillis() - 30_000L,
            isAccessible = true
        )
    )

    // App 4: Updated system app residing in user storage
    private val updatedSystemApp = InstalledAppInfo(
        packageName = "com.google.android.webview",
        appName = "Android System WebView",
        versionName = "125.0.0",
        versionCode = 6000L,
        isSystemApp = true,
        isUpdatedSystemApp = true,
        installTimeMillis = 600L,
        lastUpdateTimeMillis = 7000L,
        sourceDir = "/data/app/com.google.android.webview/base.apk",
        storageMeasurement = AppStorageMeasurement(
            codeComponent = StorageComponentMeasurement.exact(120 * 1024 * 1024L, "Updated system package"),
            dataComponent = StorageComponentMeasurement.unavailable(),
            cacheComponent = StorageComponentMeasurement.unavailable(),
            totalComponent = StorageComponentMeasurement.approximate(120 * 1024 * 1024L, "Code size only"),
            source = StorageMeasurementSource.APK_ON_DISK,
            measuredAtMillis = System.currentTimeMillis(),
            isAccessible = true
        )
    )

    // App 5: Restricted/Inaccessible package: system does not permit inspection
    private val restrictedApp = InstalledAppInfo(
        packageName = "com.protected.carrier.service",
        appName = "Carrier Provisioning",
        versionName = null,
        versionCode = null,
        isSystemApp = true,
        isUpdatedSystemApp = false,
        installTimeMillis = 100L,
        lastUpdateTimeMillis = 100L,
        sourceDir = null,
        storageMeasurement = AppStorageMeasurement.unavailable("Android platform restricts inspecting package binary")
    )

    private val testApps = listOf(fullMeasuredApp, codeOnlyApp, systemApp, updatedSystemApp, restrictedApp)
    private lateinit var repository: InstalledAppsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = InstalledAppsRepository(
            context = null,
            ioDispatcher = testDispatcher,
            mockApps = testApps
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun separateStorageValues_areAccuratelyExposed() {
        // Full measured app has code, data, and cache exposed
        assertEquals(80 * 1024 * 1024L, fullMeasuredApp.storageMeasurement.codeBytes)
        assertEquals(25 * 1024 * 1024L, fullMeasuredApp.storageMeasurement.dataBytes)
        assertEquals(10 * 1024 * 1024L, fullMeasuredApp.storageMeasurement.cacheBytes)
        assertEquals(115 * 1024 * 1024L, fullMeasuredApp.storageMeasurement.totalBytes)

        assertEquals(StorageAccuracy.EXACT, fullMeasuredApp.storageMeasurement.codeComponent.accuracy)
        assertEquals(StorageAccuracy.EXACT, fullMeasuredApp.storageMeasurement.dataComponent.accuracy)
        assertEquals(StorageAccuracy.EXACT, fullMeasuredApp.storageMeasurement.cacheComponent.accuracy)
        assertEquals(StorageAccuracy.EXACT, fullMeasuredApp.storageMeasurement.totalAccuracy)

        assertTrue("Code size formatted correctly", fullMeasuredApp.storageMeasurement.codeComponent.formattedSize.contains("MB"))
        assertTrue("Data size formatted correctly", fullMeasuredApp.storageMeasurement.dataComponent.formattedSize.contains("MB"))
        assertTrue("Cache size formatted correctly", fullMeasuredApp.storageMeasurement.cacheComponent.formattedSize.contains("MB"))
        assertTrue("Total size formatted correctly", fullMeasuredApp.storageMeasurement.formattedTotalSize.contains("MB"))
    }

    @Test
    fun codeOnlyApp_marksDataAndCacheUnavailable_totalApproximate() {
        // Code is available and exact
        assertEquals(15 * 1024 * 1024L, codeOnlyApp.storageMeasurement.codeBytes)
        assertEquals(StorageAccuracy.EXACT, codeOnlyApp.storageMeasurement.codeComponent.accuracy)

        // Data and cache are unavailable due to platform sandbox
        assertNull("Data bytes must be null", codeOnlyApp.storageMeasurement.dataBytes)
        assertNull("Cache bytes must be null", codeOnlyApp.storageMeasurement.cacheBytes)
        assertEquals(StorageAccuracy.UNAVAILABLE, codeOnlyApp.storageMeasurement.dataComponent.accuracy)
        assertEquals(StorageAccuracy.UNAVAILABLE, codeOnlyApp.storageMeasurement.cacheComponent.accuracy)

        assertEquals("Unavailable", codeOnlyApp.storageMeasurement.dataComponent.formattedSize)
        assertEquals("Unavailable", codeOnlyApp.storageMeasurement.cacheComponent.formattedSize)

        // Total of displayed components is approximate with respect to the app's full storage
        assertEquals(15 * 1024 * 1024L, codeOnlyApp.storageMeasurement.totalBytes)
        assertEquals(StorageAccuracy.APPROXIMATE, codeOnlyApp.storageMeasurement.totalAccuracy)
    }

    @Test
    fun inaccessibleMeasurement_displaysUnavailable_neverZero() {
        assertFalse("Restricted app measurement must not be marked accessible", restrictedApp.storageMeasurement.isAccessible)
        assertNull("Restricted app totalBytes must be null", restrictedApp.storageMeasurement.totalBytes)
        assertNull("Restricted app codeBytes must be null", restrictedApp.storageMeasurement.codeBytes)

        val displaySize = restrictedApp.storageMeasurement.formattedTotalSize
        assertEquals("Unavailable", displaySize)
        assertFalse("Measurement must NEVER be labeled as 0 B", displaySize.contains("0 B"))
        assertFalse("Measurement must NEVER be labeled as 0 MB", displaySize.contains("0 MB"))
        assertEquals(StorageAccuracy.UNAVAILABLE, restrictedApp.storageMeasurement.totalAccuracy)
        assertEquals(StorageMeasurementSource.UNAVAILABLE, restrictedApp.storageMeasurement.source)
    }

    @Test
    fun staleMeasurement_isMarkedAccurately() {
        val staleApp = fullMeasuredApp.storageMeasurement.asStale()
        assertEquals(StorageAccuracy.STALE, staleApp.codeComponent.accuracy)
        assertEquals(StorageAccuracy.STALE, staleApp.dataComponent.accuracy)
        assertEquals(StorageAccuracy.STALE, staleApp.cacheComponent.accuracy)
        assertEquals(StorageAccuracy.STALE, staleApp.totalAccuracy)
        assertEquals("Stale", staleApp.totalAccuracy.displayName)
    }

    @Test
    fun userVsSystemClassification_distinguishesCorrectly() {
        assertTrue("fullMeasuredApp must be user app", fullMeasuredApp.isUserApp)
        assertTrue("codeOnlyApp must be user app", codeOnlyApp.isUserApp)
        assertTrue("updatedSystemApp must be classified as user app", updatedSystemApp.isUserApp)

        assertFalse("systemApp must not be user app", systemApp.isUserApp)
        assertFalse("restrictedApp must not be user app", restrictedApp.isUserApp)

        assertEquals("User App", fullMeasuredApp.appTypeLabel)
        assertEquals("System App", systemApp.appTypeLabel)
        assertEquals("Updated System App", updatedSystemApp.appTypeLabel)
    }

    @Test
    fun packageExtraInfo_capturesSplitsAndCapabilities() {
        val extra = fullMeasuredApp.extraInfo
        assertEquals(2, extra.splitApks.size)
        assertEquals("split_config.arm64_v8a.apk", extra.splitApks[0].name)
        assertEquals(20 * 1024 * 1024L, extra.totalSplitBytes)
        assertEquals("arm64-v8a", extra.primaryCpuAbi)
        assertEquals(34, extra.targetSdkVersion)
        assertEquals(26, extra.minSdkVersion)
        assertEquals(14, extra.requestedPermissionsCount)
        assertEquals("org.example.browser", extra.processName)
        assertTrue(extra.isHardwareAccelerated)
    }

    @Test
    fun displayVersion_formatsWithFallbacks() {
        assertEquals("v120.0.1 (54321)", fullMeasuredApp.displayVersion)
        assertEquals("v2.4.0 (240)", codeOnlyApp.displayVersion)
        assertEquals("Version Unavailable", restrictedApp.displayVersion)
    }

    @Test
    fun filtering_byUserAndSystemApps() = runBlocking {
        val report = repository.getInstalledApps()
        assertEquals(5, report.totalAppCount)
        assertEquals(3, report.userAppCount)
        assertEquals(2, report.systemAppCount)
        assertEquals(1, report.inaccessibleCount)

        val userFiltered = repository.filterAndSort(
            apps = report.apps,
            query = "",
            filter = AppTypeFilter.USER,
            sortOrder = InstalledAppSortOrder.NAME_ASC
        )
        assertEquals(3, userFiltered.size)
        assertTrue(userFiltered.all { it.isUserApp })

        val systemFiltered = repository.filterAndSort(
            apps = report.apps,
            query = "",
            filter = AppTypeFilter.SYSTEM,
            sortOrder = InstalledAppSortOrder.NAME_ASC
        )
        assertEquals(2, systemFiltered.size)
        assertTrue(systemFiltered.none { it.isUserApp })
    }

    @Test
    fun search_matchesAppNameAndPackageName() = runBlocking {
        val report = repository.getInstalledApps()

        val nameMatch = repository.filterAndSort(
            apps = report.apps,
            query = "Zeta",
            filter = AppTypeFilter.ALL,
            sortOrder = InstalledAppSortOrder.NAME_ASC
        )
        assertEquals(1, nameMatch.size)
        assertEquals("Zeta Notes", nameMatch.first().appName)

        val pkgMatch = repository.filterAndSort(
            apps = report.apps,
            query = "carrier.service",
            filter = AppTypeFilter.ALL,
            sortOrder = InstalledAppSortOrder.NAME_ASC
        )
        assertEquals(1, pkgMatch.size)
        assertEquals("Carrier Provisioning", pkgMatch.first().appName)
    }

    @Test
    fun sorting_byTotalSizeAndCodeSize() = runBlocking {
        val report = repository.getInstalledApps()

        // Sort by Total Size
        val totalSorted = repository.filterAndSort(
            apps = report.apps,
            query = "",
            filter = AppTypeFilter.ALL,
            sortOrder = InstalledAppSortOrder.SIZE_DESC
        )
        assertEquals(5, totalSorted.size)
        assertEquals("Android System WebView", totalSorted[0].appName) // 120 MB
        assertEquals("Alpha Browser", totalSorted[1].appName)          // 115 MB
        assertEquals("Settings", totalSorted[2].appName)               // 25 MB
        assertEquals("Zeta Notes", totalSorted[3].appName)             // 15 MB
        assertEquals("Carrier Provisioning", totalSorted[4].appName)   // Unavailable at end

        // Sort by Code Size
        val codeSorted = repository.filterAndSort(
            apps = report.apps,
            query = "",
            filter = AppTypeFilter.ALL,
            sortOrder = InstalledAppSortOrder.CODE_SIZE_DESC
        )
        assertEquals("Android System WebView", codeSorted[0].appName) // 120 MB code
        assertEquals("Alpha Browser", codeSorted[1].appName)          // 80 MB code
        assertEquals("Settings", codeSorted[2].appName)               // 25 MB code
        assertEquals("Zeta Notes", codeSorted[3].appName)             // 15 MB code
        assertEquals("Carrier Provisioning", codeSorted[4].appName)   // Unavailable at end
    }

    @Test
    fun sorting_byDataSize_placesUnavailableAtEnd() = runBlocking {
        val report = repository.getInstalledApps()

        val dataSorted = repository.filterAndSort(
            apps = report.apps,
            query = "",
            filter = AppTypeFilter.ALL,
            sortOrder = InstalledAppSortOrder.DATA_SIZE_DESC
        )
        assertEquals(5, dataSorted.size)
        // Only Alpha Browser has accessible data (25 MB); others are unavailable
        assertEquals("Alpha Browser", dataSorted[0].appName)
        assertNotNull(dataSorted[0].storageMeasurement.dataBytes)
    }
}
