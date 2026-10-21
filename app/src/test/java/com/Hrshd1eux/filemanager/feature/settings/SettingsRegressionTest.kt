package com.Hrshd1eux.filemanager.feature.settings

import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionLevelPreset
import com.Hrshd1eux.filemanager.core.archive.model.CompressionProfile
import com.Hrshd1eux.filemanager.core.archive.preferences.ArchivePreferencesRepository
import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.display.preferences.DisplayPreferencesRepository
import com.Hrshd1eux.filemanager.core.settings.model.ThemeMode
import com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.vault.VaultManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultAutoLockTimeout
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import com.Hrshd1eux.filemanager.feature.browser.FileBrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Rigorous regression tests for Version 1.2.23 — Settings & Navigation.
 * Validates that every visible control in Settings is connected to real behavior,
 * persists state, handles errors meaningfully, and cannot be put into invalid states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var displayPreferencesRepository: DisplayPreferencesRepository
    private lateinit var archivePreferencesRepository: ArchivePreferencesRepository
    private lateinit var vaultLockManager: VaultLockManager
    private lateinit var vaultManager: VaultManager

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appSettingsRepository = AppSettingsRepository(context = null)
        displayPreferencesRepository = DisplayPreferencesRepository(context = null)
        archivePreferencesRepository = ArchivePreferencesRepository(context = null)

        val vaultDir = tempFolder.newFolder("regression_vault")
        vaultManager = VaultManager(
            vaultDir = vaultDir,
            cryptoEngine = CryptoEngine(ioDispatcher = Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined
        )
        vaultLockManager = VaultLockManager(
            context = null,
            vaultManager = vaultManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun themeSettings_changesThemeModeAndDynamicColor() = runTest {
        // Default is SYSTEM and dynamic color enabled
        assertEquals(ThemeMode.SYSTEM, appSettingsRepository.themeMode.value)
        assertTrue(appSettingsRepository.dynamicColor.value)

        // Switch to DARK
        appSettingsRepository.updateThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, appSettingsRepository.themeMode.value)

        // Switch to LIGHT
        appSettingsRepository.updateThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, appSettingsRepository.themeMode.value)

        // Toggle dynamic color off
        appSettingsRepository.updateDynamicColor(false)
        assertFalse(appSettingsRepository.dynamicColor.value)

        // Toggle dynamic color on
        appSettingsRepository.updateDynamicColor(true)
        assertTrue(appSettingsRepository.dynamicColor.value)
    }

    @Test
    fun confirmDeleteSettings_togglesAndControlsState() = runTest {
        // Default confirm before deletion is true
        assertTrue(appSettingsRepository.confirmDelete.value)

        appSettingsRepository.updateConfirmDelete(false)
        assertFalse(appSettingsRepository.confirmDelete.value)

        appSettingsRepository.updateConfirmDelete(true)
        assertTrue(appSettingsRepository.confirmDelete.value)
    }

    @Test
    fun showHiddenFilesSettings_togglesAndControlsState() = runTest {
        assertFalse(displayPreferencesRepository.showHiddenFiles.value)

        displayPreferencesRepository.updateShowHiddenFiles(true)
        assertTrue(displayPreferencesRepository.showHiddenFiles.value)

        displayPreferencesRepository.updateShowHiddenFiles(false)
        assertFalse(displayPreferencesRepository.showHiddenFiles.value)
    }

    @Test
    fun archiveSettings_enforcesAtLeastOneEnabledFormat() = runTest {
        val initial = archivePreferencesRepository.enabledFormats.value
        assertEquals(ArchiveFormatOption.entries.size, initial.size)

        // Disable 5 formats one by one
        val formatsToDisable = listOf(
            ArchiveFormatOption.TAR_BZ2,
            ArchiveFormatOption.TAR_XZ,
            ArchiveFormatOption.TAR_GZ,
            ArchiveFormatOption.TAR,
            ArchiveFormatOption.SEVEN_Z
        )
        for (f in formatsToDisable) {
            val res = archivePreferencesRepository.toggleFormatEnabled(f, false)
            assertTrue("Expected success when disabling ${f.name}", res.isSuccess)
        }

        assertEquals(setOf(ArchiveFormatOption.ZIP), archivePreferencesRepository.enabledFormats.value)

        // Attempt to disable the last remaining format (ZIP) — MUST FAIL
        val failResult = archivePreferencesRepository.toggleFormatEnabled(ArchiveFormatOption.ZIP, false)
        assertTrue(failResult.isFailure)
        assertEquals("At least one archive format must remain enabled.", failResult.exceptionOrNull()?.message)

        // ZIP must remain enabled
        assertTrue(archivePreferencesRepository.isFormatEnabled(ArchiveFormatOption.ZIP))
    }

    @Test
    fun archiveSettings_autoReassignsDefaultWhenDefaultIsDisabled() = runTest {
        archivePreferencesRepository.setDefaultFormat(ArchiveFormatOption.ZIP)
        assertEquals(ArchiveFormatOption.ZIP, archivePreferencesRepository.defaultFormat.value)

        // Disable ZIP format
        val result = archivePreferencesRepository.toggleFormatEnabled(ArchiveFormatOption.ZIP, false)
        assertTrue(result.isSuccess)

        // Default format must have automatically changed to another enabled format!
        val newDefault = archivePreferencesRepository.defaultFormat.value
        assertFalse(newDefault == ArchiveFormatOption.ZIP)
        assertTrue(archivePreferencesRepository.enabledFormats.value.contains(newDefault))
    }

    @Test
    fun archiveSettings_cannotSetDisabledFormatAsDefault() = runTest {
        // Disable TAR format
        archivePreferencesRepository.toggleFormatEnabled(ArchiveFormatOption.TAR, false)

        val result = archivePreferencesRepository.setDefaultFormat(ArchiveFormatOption.TAR)
        assertTrue(result.isFailure)
        assertEquals("Cannot set disabled format TAR as default.", result.exceptionOrNull()?.message)
    }

    @Test
    fun archiveSettings_defaultCompressionLevelUpdates() = runTest {
        assertEquals(CompressionLevelPreset.NORMAL, archivePreferencesRepository.defaultLevel.value)

        archivePreferencesRepository.setDefaultLevel(CompressionLevelPreset.ULTRA)
        assertEquals(CompressionLevelPreset.ULTRA, archivePreferencesRepository.defaultLevel.value)

        archivePreferencesRepository.setDefaultLevel(CompressionLevelPreset.STORE)
        assertEquals(CompressionLevelPreset.STORE, archivePreferencesRepository.defaultLevel.value)
    }

    @Test
    fun archiveSettings_disabledFormatRejectsCompressionInFileBrowser() = runTest {
        val rootDir = tempFolder.newFolder("browser_root")
        val sampleFile = File(rootDir, "test.txt").apply { writeText("Hello regression") }
        val sampleNode = StorageNode(
            id = sampleFile.absolutePath,
            name = sampleFile.name,
            path = sampleFile.absolutePath,
            isDirectory = false,
            size = sampleFile.length()
        )

        // Disable TAR_GZ format in repository
        archivePreferencesRepository.toggleFormatEnabled(ArchiveFormatOption.TAR_GZ, false)
        assertFalse(archivePreferencesRepository.isFormatEnabled(ArchiveFormatOption.TAR_GZ))

        val provider = com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider(
            rootDirectory = rootDir,
            displayName = "Test Storage",
            ioDispatcher = testDispatcher
        )
        val viewModel = FileBrowserViewModel(
            storageProvider = provider,
            archivePreferencesRepository = archivePreferencesRepository,
            appSettingsRepository = appSettingsRepository,
            ioDispatcher = testDispatcher
        )

        viewModel.initialize(rootDir.absolutePath, hasPermission = true)
        advanceUntilIdle()

        var msg: String? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            msg = viewModel.userMessage.first()
        }

        val profile = CompressionProfile(format = ArchiveFormatOption.TAR_GZ)

        // Attempt compression into disabled format
        viewModel.compressFiles(
            sources = listOf(sampleNode),
            profile = profile,
            archiveName = "test.tar.gz",
            hasPermission = true
        )
        advanceUntilIdle()

        // Verify meaningful error message is delivered
        assertEquals("Archive format TAR.GZ is disabled in Settings.", msg)
        job.cancel()
    }

    @Test
    fun vaultLockSettings_realBehaviorAndManualLock() = runTest {
        vaultManager.createVault("TestPassword123!".toCharArray())
        assertTrue(vaultManager.isUnlocked())

        val settingsViewModel = SettingsViewModel(
            displayPreferencesRepository = displayPreferencesRepository,
            appSettingsRepository = appSettingsRepository,
            archivePreferencesRepository = archivePreferencesRepository,
            vaultLockManager = vaultLockManager
        )

        val updatedSettings = VaultLockSettings(
            autoLockTimeout = VaultAutoLockTimeout.ONE_MINUTE,
            lockOnBackground = false,
            lockOnScreenOff = false
        )

        settingsViewModel.updateVaultLockSettings(updatedSettings)
        assertEquals(VaultAutoLockTimeout.ONE_MINUTE, vaultLockManager.lockSettings.value.autoLockTimeout)
        assertFalse(vaultLockManager.lockSettings.value.lockOnBackground)
        assertFalse(vaultLockManager.lockSettings.value.lockOnScreenOff)

        // Test real lock trigger
        settingsViewModel.lockVaultNow()
        assertFalse(vaultManager.isUnlocked())
        val msg = settingsViewModel.userMessage.first()
        assertEquals("Private Vault locked.", msg)
    }

    @Test
    fun settingsViewModel_toggleArchiveFormatEmitsErrorOnInvalidAction() = runTest {
        val settingsViewModel = SettingsViewModel(
            displayPreferencesRepository = displayPreferencesRepository,
            appSettingsRepository = appSettingsRepository,
            archivePreferencesRepository = archivePreferencesRepository,
            vaultLockManager = vaultLockManager
        )

        // Disable all but ZIP
        listOf(
            ArchiveFormatOption.TAR_BZ2,
            ArchiveFormatOption.TAR_XZ,
            ArchiveFormatOption.TAR_GZ,
            ArchiveFormatOption.TAR,
            ArchiveFormatOption.SEVEN_Z
        ).forEach {
            settingsViewModel.toggleArchiveFormat(it, false)
        }

        // Try disabling the last one (ZIP)
        settingsViewModel.toggleArchiveFormat(ArchiveFormatOption.ZIP, false)

        val msg = settingsViewModel.userMessage.first()
        assertEquals("At least one archive format must remain enabled.", msg)
    }

    @Test
    fun appSettingsRepository_hapticAndSoundFeedbackTogglesWork() = runTest {
        // Defaults should be enabled
        assertTrue(appSettingsRepository.hapticFeedback.value)
        assertTrue(appSettingsRepository.soundFeedback.value)

        // Toggle haptics
        appSettingsRepository.updateHapticFeedback(false)
        assertFalse(appSettingsRepository.hapticFeedback.value)
        appSettingsRepository.updateHapticFeedback(true)
        assertTrue(appSettingsRepository.hapticFeedback.value)

        // Toggle sound
        appSettingsRepository.updateSoundFeedback(false)
        assertFalse(appSettingsRepository.soundFeedback.value)
        appSettingsRepository.updateSoundFeedback(true)
        assertTrue(appSettingsRepository.soundFeedback.value)
    }

    @Test
    fun settingsViewModel_hapticAndSoundFeedbackWiredProperly() = runTest {
        val settingsViewModel = SettingsViewModel(
            displayPreferencesRepository = displayPreferencesRepository,
            appSettingsRepository = appSettingsRepository,
            archivePreferencesRepository = archivePreferencesRepository,
            vaultLockManager = vaultLockManager
        )

        assertTrue(settingsViewModel.hapticFeedback.value)
        assertTrue(settingsViewModel.soundFeedback.value)

        settingsViewModel.setHapticFeedback(false)
        assertFalse(settingsViewModel.hapticFeedback.value)

        settingsViewModel.setSoundFeedback(false)
        assertFalse(settingsViewModel.soundFeedback.value)
    }
}
