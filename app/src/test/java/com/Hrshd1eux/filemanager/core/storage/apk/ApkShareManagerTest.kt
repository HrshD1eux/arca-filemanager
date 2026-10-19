package com.Hrshd1eux.filemanager.core.storage.apk

import android.content.Context
import android.net.Uri
import com.Hrshd1eux.filemanager.core.storage.model.AppStorageMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.model.PackageExtraInfo
import com.Hrshd1eux.filemanager.core.storage.model.SplitApkInfo
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pure JVM Unit tests for Version 1.2.39 — Share Installed APKs.
 *
 * Verifies that:
 * 1. Share APK operates where installed package APK files are accessible.
 * 2. Uses Android's standard sharing interface concepts.
 * 3. Uses secure FileProvider content URIs with temporary read permission.
 * 4. Never exposes unrestricted filesystem paths or file:// URIs.
 * 5. Displays clear errors if extraction or sharing fails.
 * 6. Cleans up temporary APK copies when appropriate.
 * 7. Checks available space before creating temporary copies.
 * 8. Detects split APKs (Android App Bundles) and explains limitations.
 * 9. Sanitizes filenames properly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApkShareManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var cacheDir: File
    private lateinit var appSourceDir: File
    private lateinit var baseApkFile: File
    private lateinit var splitApk1File: File
    private lateinit var splitApk2File: File



    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        cacheDir = tempFolder.newFolder("mock_cache")
        appSourceDir = tempFolder.newFolder("mock_app_dir")

        baseApkFile = File(appSourceDir, "base.apk").apply {
            writeBytes(ByteArray(1024 * 50) { 1 }) // 50 KB base APK
        }

        splitApk1File = File(appSourceDir, "split_config.arm64_v8a.apk").apply {
            writeBytes(ByteArray(1024 * 20) { 2 }) // 20 KB split
        }

        splitApk2File = File(appSourceDir, "split_config.xxhdpi.apk").apply {
            writeBytes(ByteArray(1024 * 15) { 3 }) // 15 KB split
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestApp(
        isSplit: Boolean = false,
        sourceDir: String? = baseApkFile.absolutePath,
        appName: String = "Test Application",
        versionName: String = "2.1.0"
    ): InstalledAppInfo {
        val splits = if (isSplit) {
            listOf(
                SplitApkInfo("split_config.arm64_v8a.apk", splitApk1File.absolutePath, splitApk1File.length(), true),
                SplitApkInfo("split_config.xxhdpi.apk", splitApk2File.absolutePath, splitApk2File.length(), true)
            )
        } else {
            emptyList()
        }

        return InstalledAppInfo(
            packageName = "com.example.testapp",
            appName = appName,
            versionName = versionName,
            versionCode = 210L,
            isSystemApp = false,
            sourceDir = sourceDir,
            storageMeasurement = AppStorageMeasurement(
                codeComponent = StorageComponentMeasurement.exact(baseApkFile.length()),
                dataComponent = StorageComponentMeasurement.unavailable(),
                cacheComponent = StorageComponentMeasurement.unavailable(),
                totalComponent = StorageComponentMeasurement.exact(baseApkFile.length()),
                source = StorageMeasurementSource.APK_ON_DISK,
                isAccessible = true
            ),
            extraInfo = PackageExtraInfo(
                splitApks = splits
            )
        )
    }

    private fun createUriMock(): (Context?, String, File) -> Uri {
        return { _, auth, file ->
            android.net.TestUri("content://$auth/shared_apks/${file.name}")
        }
    }

    @Test
    fun singleStandaloneApk_inspectsSuccessfully_andDetectsNoSplits() {
        val manager = ApkShareManager(ioDispatcher = testDispatcher)
        val app = createTestApp(isSplit = false)

        val inspection = manager.inspectShareableApks(app)
        assertTrue("Single APK must be shareable", inspection.isShareable)
        assertFalse("Must not be detected as split APK", inspection.isSplitApk)
        assertEquals(baseApkFile.length(), inspection.baseSizeBytes)
        assertEquals(0L, inspection.splitSizeBytes)
        assertEquals(baseApkFile.length(), inspection.totalSizeBytes)
        assertEquals("Single Standalone APK", app.apkFormatLabel)
    }

    @Test
    fun splitApks_inspectsSuccessfully_andDetectsAllSplits() {
        val manager = ApkShareManager(ioDispatcher = testDispatcher)
        val app = createTestApp(isSplit = true)

        val inspection = manager.inspectShareableApks(app)
        assertTrue("Split APK must be shareable", inspection.isShareable)
        assertTrue("Must be detected as split APK bundle", inspection.isSplitApk)
        assertEquals(2, inspection.splitFiles.size)
        assertEquals(baseApkFile.length(), inspection.baseSizeBytes)
        assertEquals(splitApk1File.length() + splitApk2File.length(), inspection.splitSizeBytes)
        assertEquals(baseApkFile.length() + splitApk1File.length() + splitApk2File.length(), inspection.totalSizeBytes)
        assertEquals("Split APKs (2 splits)", app.apkFormatLabel)
    }

    @Test
    fun inaccessibleApk_detectsMissingOrUnreadableFiles() {
        val manager = ApkShareManager(ioDispatcher = testDispatcher)

        // Case 1: Null sourceDir
        val nullSourceApp = createTestApp(sourceDir = null)
        val nullInspection = manager.inspectShareableApks(nullSourceApp)
        assertFalse(nullInspection.isShareable)
        assertNotNull(nullInspection.inaccessibilityReason)

        // Case 2: Non-existent file
        val missingApp = createTestApp(sourceDir = "/non/existent/path/base.apk")
        val missingInspection = manager.inspectShareableApks(missingApp)
        assertFalse(missingInspection.isShareable)
        assertTrue(missingInspection.inaccessibilityReason!!.contains("does not exist"))

        // Case 3: Zero-byte file
        val emptyFile = File(appSourceDir, "empty.apk").apply { createNewFile() }
        val emptyApp = createTestApp(sourceDir = emptyFile.absolutePath)
        val emptyInspection = manager.inspectShareableApks(emptyApp)
        assertFalse(emptyInspection.isShareable)
        assertTrue(emptyInspection.inaccessibilityReason!!.contains("zero-byte"))
    }

    @Test
    fun storageCheck_returnsInsufficientStorage_whenFreeSpaceIsLow() = runBlocking {
        // Mock storage checker reporting only 1 KB available
        val manager = ApkShareManager(
            ioDispatcher = testDispatcher,
            safetyBufferBytes = 25 * 1024 * 1024L,
            storageChecker = { 1024L },
            uriProvider = createUriMock()
        )
        val app = createTestApp(isSplit = false)

        val result = manager.prepareAndCreateShareIntent(
            context = null,
            app = app,
            customCacheDir = cacheDir
        )
        assertTrue("Must fail with InsufficientStorage", result is ApkShareResult.InsufficientStorage)

        val storageResult = result as ApkShareResult.InsufficientStorage
        assertTrue(storageResult.requiredBytes > storageResult.availableBytes)
        assertNotNull(storageResult.formattedRequired)
        assertNotNull(storageResult.formattedAvailable)
    }

    @Test
    fun singleApk_prepareAndCreateShareIntent_producesActionSendWithSecureContentUri() = runBlocking {
        val manager = ApkShareManager(
            ioDispatcher = testDispatcher,
            storageChecker = { 100 * 1024 * 1024L }, // 100 MB free
            uriProvider = createUriMock()
        )
        val app = createTestApp(isSplit = false, appName = "ARCA Vault", versionName = "1.2.39")

        val result = manager.prepareAndCreateShareIntent(
            context = null,
            app = app,
            shareMode = ApkShareMode.BASE_ONLY,
            customCacheDir = cacheDir
        )
        assertTrue("Must succeed", result is ApkShareResult.Success)

        val success = result as ApkShareResult.Success
        assertEquals(1, success.sharedFileCount)
        assertEquals(1, success.targetFiles.size)
        assertEquals(1, success.uris.size)

        // Target file in cache/shared_apks
        val exportedFile = success.targetFiles.first()
        assertTrue("Target file must exist in cache", exportedFile.exists())
        assertEquals("ARCA_Vault_1.2.39.apk", exportedFile.name)
        assertEquals(baseApkFile.length(), exportedFile.length())

        // Secure content URI check: never expose raw file:// URI!
        val uri = success.uris.first()
        assertTrue("Must use secure content:// scheme", uri.toString().startsWith("content://"))
        assertFalse("Must NEVER use file:// scheme", uri.toString().startsWith("file://"))

        // Standard Sharing Intent verification
        assertNotNull("Share intent must be created", success.shareIntent)
        assertNotNull("Chooser intent must be created", success.chooserIntent)
    }

    @Test
    fun splitApks_allSplits_prepareAndCreateShareIntent_producesActionSendMultiple() = runBlocking {
        val manager = ApkShareManager(
            ioDispatcher = testDispatcher,
            storageChecker = { 100 * 1024 * 1024L },
            uriProvider = createUriMock()
        )
        val app = createTestApp(isSplit = true, appName = "Heavy Game", versionName = "3.0")

        val result = manager.prepareAndCreateShareIntent(
            context = null,
            app = app,
            shareMode = ApkShareMode.ALL_SPLITS,
            customCacheDir = cacheDir
        )
        assertTrue("Must succeed with all splits", result is ApkShareResult.Success)

        val success = result as ApkShareResult.Success
        assertEquals(3, success.sharedFileCount) // Base + 2 splits
        assertEquals(3, success.targetFiles.size)
        assertEquals(3, success.uris.size)

        // Base APK should be labeled _base.apk
        val baseCopy = success.targetFiles.first { it.name.contains("base.apk") }
        assertEquals("Heavy_Game_3.0_base.apk", baseCopy.name)

        // Intent verification
        assertNotNull(success.shareIntent)
    }

    @Test
    fun splitApks_baseOnly_prepareAndCreateShareIntent_producesActionSendForBaseOnly() = runBlocking {
        val manager = ApkShareManager(
            ioDispatcher = testDispatcher,
            storageChecker = { 100 * 1024 * 1024L },
            uriProvider = createUriMock()
        )
        val app = createTestApp(isSplit = true, appName = "Heavy Game", versionName = "3.0")

        val result = manager.prepareAndCreateShareIntent(
            context = null,
            app = app,
            shareMode = ApkShareMode.BASE_ONLY,
            customCacheDir = cacheDir
        )
        assertTrue("Must succeed with base only", result is ApkShareResult.Success)

        val success = result as ApkShareResult.Success
        assertEquals("Must only share 1 file when BASE_ONLY is requested", 1, success.sharedFileCount)
    }

    @Test
    fun temporaryCopiesPruning_deletesOlderCopies_preservesRecent() {
        val manager = ApkShareManager(ioDispatcher = testDispatcher)
        val sharedDir = File(cacheDir, ApkShareManager.SHARED_APKS_DIR_NAME).apply { mkdirs() }

        val oldFile = File(sharedDir, "old_app.apk").apply {
            createNewFile()
            setLastModified(System.currentTimeMillis() - (2 * 3600 * 1000L)) // 2 hours old
        }

        val recentFile = File(sharedDir, "recent_app.apk").apply {
            createNewFile()
            setLastModified(System.currentTimeMillis()) // Current
        }

        manager.cleanupOldCopies(cacheDir, maxAgeMillis = 3600 * 1000L) // 1 hour threshold

        assertFalse("Old file should be deleted", oldFile.exists())
        assertTrue("Recent file should be preserved", recentFile.exists())

        manager.clearAllSharedCopies(cacheDir)
        assertFalse("All files should be deleted", recentFile.exists())
    }

    @Test
    fun sanitizeFileName_stripsSpecialCharactersAndEnforcesLength() {
        assertEquals("MyApp_v1.0", ApkShareManager.sanitizeFileName("MyApp v1.0"))
        assertEquals("Private_Vault_2026", ApkShareManager.sanitizeFileName("Private: Vault (2026)!@#$%^&*"))
        assertEquals("app", ApkShareManager.sanitizeFileName("   "))
        assertEquals("app", ApkShareManager.sanitizeFileName("!@#$%^"))

        // Very long name should be truncated
        val longName = "A".repeat(100)
        val sanitized = ApkShareManager.sanitizeFileName(longName)
        assertTrue(sanitized.length <= 36)
    }
}
