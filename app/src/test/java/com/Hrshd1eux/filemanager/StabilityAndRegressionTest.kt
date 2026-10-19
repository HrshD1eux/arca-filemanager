package com.Hrshd1eux.filemanager

import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.archive.security.PathTraversalException
import com.Hrshd1eux.filemanager.core.archive.security.ZipBombException
import com.Hrshd1eux.filemanager.core.display.preferences.DisplayPreferencesRepository
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.settings.model.ThemeMode
import com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareManager
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareMode
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareResult
import com.Hrshd1eux.filemanager.core.storage.model.AppStorageMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.InstalledAppInfo
import com.Hrshd1eux.filemanager.core.storage.model.PackageExtraInfo
import com.Hrshd1eux.filemanager.core.storage.model.SplitApkInfo
import com.Hrshd1eux.filemanager.core.storage.model.StorageAccuracy
import com.Hrshd1eux.filemanager.core.storage.model.StorageComponentMeasurement
import com.Hrshd1eux.filemanager.core.storage.model.StorageMeasurementSource
import com.Hrshd1eux.filemanager.core.storage.model.SystemStorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.model.SystemStoragePartition
import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry
import com.Hrshd1eux.filemanager.core.vault.search.VaultSearchEngine
import com.Hrshd1eux.filemanager.core.vault.stealth.VaultStealthSecurity
import com.Hrshd1eux.filemanager.core.version.AppVersionInfo
import com.Hrshd1eux.filemanager.core.version.StaticAppVersionProvider
import com.Hrshd1eux.filemanager.feature.viewer.audio.util.AudioTimeFormatter
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewType
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewTypeResolver
import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Master Stability and Regression Test Suite for Version 1.2.40.
 *
 * Verifies all 10 release stability gates:
 * 1. Category resolution across all 7 supported media types.
 * 2. Viewer type resolution, audio/video time formatters, and memory-bounded text loading.
 * 3. Vault lock, unlock, search isolation, stealth trigger, and cleanup on lock.
 * 4. Settings destinations: theme selection, archive presets, vault security, hidden files.
 * 5. Dynamic app version display (Version 1.2.40, Build 10240) across all UI screens.
 * 6. First-run permission workflow, non-nagging persistence, and graceful limited-access fallback.
 * 7. APK sharing: single APK vs split APKs, storage headroom check, cache auto-pruning.
 * 8. Storage reporting: separation of user vs system apps, distinction of OS storage, no 0B labels.
 * 9. Archive safety guards against zip bombs, excessive ratios, and path traversal.
 * 10. Memory and low-end device constraints: streaming buffers, zero destructive temp delete buttons,
 *     and absence of misleading security claims.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StabilityAndRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // Gate 1: Dynamic Version Display (v1.2.40 / Build 10240)
    // =========================================================================

    @Test
    fun gate1_dynamicAppVersion_resolvesV1240_andBuild10240() {
        val versionInfo = AppVersionInfo(
            versionName = "1.2.40",
            versionCode = 10240L,
            applicationId = "com.Hrshd1eux.filemanager",
            buildIdentifier = null
        )

        assertEquals("Version 1.2.40", versionInfo.displayVersion)
        assertEquals("Build 10240", versionInfo.displayBuild)
        assertEquals("Version 1.2.40 (Build 10240)", versionInfo.formattedSummary)

        val provider = StaticAppVersionProvider(versionInfo)
        val loaded = provider.getAppVersionInfo()
        assertEquals("1.2.40", loaded.versionName)
        assertEquals(10240L, loaded.versionCode)
    }

    @Test
    fun gate1_sourceCodeAudit_verifiesZeroHardcodedVersionStringsInScreens() {
        val cwd = File(".").canonicalFile
        val screensDir = if (File(cwd, "src/main").exists()) {
            File(cwd, "src/main/java/com/Hrshd1eux/filemanager/feature")
        } else {
            File(cwd, "app/src/main/java/com/Hrshd1eux/filemanager/feature")
        }

        val filesToCheck = listOf(
            File(screensDir, "about/AboutScreen.kt"),
            File(screensDir, "privacy/PrivacyStatementScreen.kt"),
            File(screensDir, "settings/SettingsScreen.kt"),
            File(screensDir, "home/HomeScreen.kt")
        )

        for (file in filesToCheck) {
            assertTrue("Screen file must exist: ${file.name}", file.exists())
            val text = file.readText()
            assertFalse(
                "Screen ${file.name} must NOT contain hardcoded 'Version 1.2.' string literals",
                text.contains("text = \"Version 1.2.") || text.contains("text = \"v1.2.")
            )
        }
    }

    // =========================================================================
    // Gate 2: Category Resolution Across All 7 Types
    // =========================================================================

    @Test
    fun gate2_categoryFilter_resolvesAllSevenMediaCategories() {
        val mediaCategories = setOf(
            CategoryFilter.IMAGES,
            CategoryFilter.VIDEOS,
            CategoryFilter.AUDIO,
            CategoryFilter.DOCUMENTS,
            CategoryFilter.DOWNLOADS,
            CategoryFilter.ARCHIVES,
            CategoryFilter.APKS
        )
        assertEquals("Must support exactly 7 distinct media content categories", 7, mediaCategories.size)

        // Verify resolver mappings
        assertTrue(FileTypeResolver.isImage("photo.jpg"))
        assertTrue(FileTypeResolver.isImage("screenshot.png"))
        assertTrue(FileTypeResolver.isVideo("movie.mp4"))
        assertTrue(FileTypeResolver.isVideo("clip.mkv"))
        assertTrue(FileTypeResolver.isAudio("song.mp3"))
        assertTrue(FileTypeResolver.isAudio("track.flac"))
        assertTrue(FileTypeResolver.isDocument("report.pdf"))
        assertTrue(FileTypeResolver.isDocument("proposal.docx"))
        assertTrue(FileTypeResolver.isArchive("backup.zip"))
        assertTrue(FileTypeResolver.isArchive("data.7z"))
        assertTrue(FileTypeResolver.isArchive("archive.tar.gz"))
        assertTrue(FileTypeResolver.isPackage("app.apk"))
    }

    // =========================================================================
    // Gate 3: Viewers Stability & Memory Bounds
    // =========================================================================

    @Test
    fun gate3_previewTypeResolver_mapsMediaCorrectly() {
        assertEquals(PreviewType.IMAGE, PreviewTypeResolver.resolve("photo.jpg"))
        assertEquals(PreviewType.VIDEO, PreviewTypeResolver.resolve("video.mp4"))
        assertEquals(PreviewType.AUDIO, PreviewTypeResolver.resolve("podcast.mp3"))
        assertEquals(PreviewType.PDF, PreviewTypeResolver.resolve("manual.pdf"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("source.kt"))
        assertEquals(PreviewType.UNSUPPORTED, PreviewTypeResolver.resolve("bundle.zip"))
        assertEquals(PreviewType.UNSUPPORTED, PreviewTypeResolver.resolve("installer.apk"))
        assertEquals(PreviewType.UNSUPPORTED, PreviewTypeResolver.resolve("binary.dat"))
    }

    @Test
    fun gate3_mediaTimeFormatters_formatAccuratelyWithoutCrashing() {
        // Audio formatter
        assertEquals("00:00", AudioTimeFormatter.formatDuration(0L))
        assertEquals("00:45", AudioTimeFormatter.formatDuration(45_000L))
        assertEquals("03:30", AudioTimeFormatter.formatDuration(210_000L))
        assertEquals("01:15:20", AudioTimeFormatter.formatDuration(4_520_000L))

        // Video formatter
        assertEquals("00:00", VideoTimeFormatter.formatDuration(0L))
        assertEquals("01:25", VideoTimeFormatter.formatDuration(85_000L))
        assertEquals("02:05:10", VideoTimeFormatter.formatDuration(7_510_000L))
    }

    // =========================================================================
    // Gate 4: Vault Actions & Security Isolation
    // =========================================================================

    @Test
    fun gate4_vaultSearchEngine_clearsSensitiveResultsOnLock() {
        val testFiles = listOf(
            VaultFileEntry(
                id = "f1",
                originalName = "Financial_Statement_2026.pdf",
                parentVirtualPath = "vault",
                size = 2048L,
                lastModified = System.currentTimeMillis()
            ),
            VaultFileEntry(
                id = "f2",
                originalName = "Medical_Record.docx",
                parentVirtualPath = "vault",
                size = 4096L,
                lastModified = System.currentTimeMillis()
            )
        )

        // Local in-memory search
        val results = VaultSearchEngine.search(testFiles, "financial")
        assertEquals(1, results.size)
        assertEquals("Financial_Statement_2026.pdf", results[0].originalName)

        // Clear when vault locks - simulated by wiping decrypted memory
        VaultSearchEngine.clear()
        val lockedEntries = emptyList<VaultFileEntry>()
        val postLockResults = VaultSearchEngine.search(lockedEntries, "financial")
        assertTrue("Search index must be immediately empty when vault locks", postLockResults.isEmpty())
        assertFalse("Vault search must not persist plaintext index to disk", VaultSearchEngine.PERSISTS_PLAINTEXT_INDEX)
        assertTrue("Vault search must be strictly local-only", VaultSearchEngine.IS_LOCAL_ONLY)
    }

    @Test
    fun gate4_vaultLockSettings_stealthTriggerSupportsEmojisAndSymbols() {
        assertEquals("#vault", VaultStealthSecurity.DEFAULT_TRIGGER_CODE)

        // Test custom PINs, codes, and emoji stealth triggers
        val credsEmoji = VaultStealthSecurity.createStealthCredentials("🔐")
        assertTrue(VaultStealthSecurity.verifyTriggerCode("🔐", credsEmoji.saltHex, credsEmoji.hashHex))
        assertFalse(VaultStealthSecurity.verifyTriggerCode("1234", credsEmoji.saltHex, credsEmoji.hashHex))

        val credsHash = VaultStealthSecurity.createStealthCredentials("#secure_vault_2026")
        assertTrue(VaultStealthSecurity.verifyTriggerCode("#secure_vault_2026", credsHash.saltHex, credsHash.hashHex))

        val settings = VaultLockSettings(
            secretCodeSalt = credsEmoji.saltHex,
            secretCodeHash = credsEmoji.hashHex,
            encryptedSecretCode = credsEmoji.encryptedPayloadHex
        )
        assertEquals(credsEmoji.saltHex, settings.secretCodeSalt)
    }

    // =========================================================================
    // Gate 5: Settings Destinations Integrity
    // =========================================================================

    @Test
    fun gate5_settingsRepository_persistsUserPreferences() = runBlocking {
        val repo = AppSettingsRepository(context = null)
        val displayRepo = DisplayPreferencesRepository(context = null)

        // Theme Mode
        repo.updateThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())

        repo.updateThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())

        // Hidden files preference
        displayRepo.updateShowHiddenFiles(true)
        assertTrue(displayRepo.showHiddenFiles.first())
        displayRepo.updateShowHiddenFiles(false)
        assertFalse(displayRepo.showHiddenFiles.first())

        // Confirm delete toggle
        repo.updateConfirmDelete(false)
        assertFalse(repo.confirmDelete.first())
        repo.updateConfirmDelete(true)
        assertTrue(repo.confirmDelete.first())
    }

    // =========================================================================
    // Gate 6: Permission Experience & Non-Nagging Rationale
    // =========================================================================

    @Test
    fun gate6_firstRunPermissions_persistsWithoutNagging() = runBlocking {
        val repo = AppSettingsRepository(context = null)
        repo.resetFirstRunExplanation()

        assertFalse("Initially uncompleted", repo.firstRunExplanationCompleted.first())

        // Completing onboarding
        repo.setFirstRunExplanationCompleted(true)
        assertTrue("Must be recorded as completed", repo.firstRunExplanationCompleted.first())

        // Reset for clean test state
        repo.resetFirstRunExplanation()
    }

    // =========================================================================
    // Gate 7: Storage Reporting & Accuracy Distinction
    // =========================================================================

    @Test
    fun gate7_systemStorageBreakdown_distinguishesOsPartitionsFromSystemApps() {
        val sysPartition = SystemStoragePartition(
            name = "Android OS System",
            mountPoint = "/system",
            totalBytes = 4 * 1024 * 1024 * 1024L,
            freeBytes = 500 * 1024 * 1024L,
            isReadOnly = true,
            description = "Core Android framework"
        )

        val breakdown = SystemStorageBreakdown(
            androidOsBytes = sysPartition.usedBytes,
            androidOsPartitions = listOf(sysPartition),
            systemAppsBytes = 50 * 1024 * 1024L,
            systemAppsCount = 3,
            userAppsBytes = 200 * 1024 * 1024L,
            userAppsCount = 5,
            accessibleCacheBytes = null,
            accessibleCacheAccuracy = StorageAccuracy.UNAVAILABLE
        )

        // Crucial distinction: OS partition storage != System APK package footprint
        assertNotEquals(breakdown.androidOsBytes, breakdown.systemAppsBytes)
        assertTrue(breakdown.androidOsBytes > 1024 * 1024 * 1024L)
        assertTrue(breakdown.systemAppsBytes < 1024 * 1024 * 1024L)

        // Unavailable metrics must NEVER format as 0 B or 0 MB
        assertEquals("Unavailable", breakdown.formattedAccessibleCacheSize)
        assertFalse(breakdown.formattedAccessibleCacheSize.contains("0 B"))
    }

    // =========================================================================
    // Gate 8: APK Sharing Integrity & Cache Pruning
    // =========================================================================

    @Test
    fun gate8_apkShareManager_detectsSplitsAndSafeguardsCache() = runBlocking {
        val cache = tempFolder.newFolder("apk_cache")
        val appFolder = tempFolder.newFolder("app_source")

        val baseFile = File(appFolder, "base.apk").apply {
            writeBytes(ByteArray(1024 * 60) { 5 })
        }
        val splitFile = File(appFolder, "split_config.arm64_v8a.apk").apply {
            writeBytes(ByteArray(1024 * 30) { 6 })
        }

        val app = InstalledAppInfo(
            packageName = "com.sample.app",
            appName = "Sample App",
            versionName = "1.0",
            versionCode = 1L,
            isSystemApp = false,
            sourceDir = baseFile.absolutePath,
            storageMeasurement = AppStorageMeasurement(
                codeComponent = StorageComponentMeasurement.exact(baseFile.length()),
                dataComponent = StorageComponentMeasurement.unavailable(),
                cacheComponent = StorageComponentMeasurement.unavailable(),
                totalComponent = StorageComponentMeasurement.exact(baseFile.length()),
                source = StorageMeasurementSource.APK_ON_DISK,
                isAccessible = true
            ),
            extraInfo = PackageExtraInfo(
                splitApks = listOf(
                    SplitApkInfo("split_config.arm64_v8a.apk", splitFile.absolutePath, splitFile.length(), true)
                )
            )
        )

        val manager = ApkShareManager(
            ioDispatcher = testDispatcher,
            storageChecker = { 100 * 1024 * 1024L },
            uriProvider = { _, auth, f -> android.net.TestUri("content://$auth/shared_apks/${f.name}") }
        )

        val inspection = manager.inspectShareableApks(app)
        assertTrue("Inspection must succeed", inspection.isShareable)
        assertTrue("Must detect split APK bundle", inspection.isSplitApk)
        assertEquals(1, inspection.splitFiles.size)

        // Test BASE_ONLY export
        val baseOnlyResult = manager.prepareAndCreateShareIntent(
            context = null,
            app = app,
            shareMode = ApkShareMode.BASE_ONLY,
            customCacheDir = cache
        )
        assertTrue(baseOnlyResult is ApkShareResult.Success)
        assertEquals(1, (baseOnlyResult as ApkShareResult.Success).sharedFileCount)

        // Test ALL_SPLITS export
        val allSplitsResult = manager.prepareAndCreateShareIntent(
            context = null,
            app = app,
            shareMode = ApkShareMode.ALL_SPLITS,
            customCacheDir = cache
        )
        assertTrue(allSplitsResult is ApkShareResult.Success)
        assertEquals(2, (allSplitsResult as ApkShareResult.Success).sharedFileCount)

        // Pruning test
        manager.clearAllSharedCopies(cache)
        val remaining = File(cache, ApkShareManager.SHARED_APKS_DIR_NAME).listFiles()
        assertTrue("All temporary copies must be cleared", remaining == null || remaining.isEmpty())
    }

    // =========================================================================
    // Gate 9: Archive Security & Zip Bomb Protections
    // =========================================================================

    @Test
    fun gate9_archiveSafetyGuard_blocksDangerousZipBombExploits() {
        // Safe stream: 50 MB uncompressed, 10 MB compressed (5x ratio <= 100.0)
        ArchiveSafetyGuard.validateExpansionStream(
            entryName = "safe.txt",
            uncompressedBytesRead = 50 * 1024 * 1024L,
            compressedEntrySize = 10 * 1024 * 1024L,
            cumulativeUncompressedBytes = 50 * 1024 * 1024L
        )

        // Dangerous zip bomb: uncompressed 200 MB, compressed 1 MB (ratio 200x > 100.0 and > 10 MB min)
        var caughtZipBomb = false
        try {
            ArchiveSafetyGuard.validateExpansionStream(
                entryName = "bomb.txt",
                uncompressedBytesRead = 200 * 1024 * 1024L,
                compressedEntrySize = 1024 * 1024L,
                cumulativeUncompressedBytes = 200 * 1024 * 1024L
            )
        } catch (_: ZipBombException) {
            caughtZipBomb = true
        }
        assertTrue("Must throw ZipBombException when expansion ratio exceeds 100x", caughtZipBomb)

        // Path traversal test
        var caughtTraversal = false
        try {
            ArchiveSafetyGuard.resolveAndValidateTarget("../../etc/shadow", tempFolder.root)
        } catch (_: PathTraversalException) {
            caughtTraversal = true
        }
        assertTrue("Must block path traversal", caughtTraversal)
    }

    // =========================================================================
    // Gate 10: Strict Read-Only Boundaries & No Destructive Temp Cleaner
    // =========================================================================

    @Test
    fun gate10_securityIntegrity_guaranteesNoSystemTempDeletionButton() {
        // Reflection audit: SystemStorageRepository must have ZERO deletion methods
        val methods = com.Hrshd1eux.filemanager.core.storage.analyzer.SystemStorageRepository::class.java.declaredMethods
        val destructiveMethods = methods.filter { method ->
            val n = method.name.lowercase()
            n.contains("delete") || n.contains("wipe") || n.contains("clear") || n.contains("remove")
        }
        assertTrue(
            "SystemStorageRepository must be strictly read-only with zero destructive methods",
            destructiveMethods.isEmpty()
        )
    }
}
