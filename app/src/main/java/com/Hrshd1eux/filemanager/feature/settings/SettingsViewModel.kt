package com.Hrshd1eux.filemanager.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionLevelPreset
import com.Hrshd1eux.filemanager.core.archive.preferences.ArchivePreferencesRepository
import com.Hrshd1eux.filemanager.core.display.preferences.DisplayPreferencesRepository
import com.Hrshd1eux.filemanager.core.settings.model.ThemeMode
import com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository
import com.Hrshd1eux.filemanager.core.storage.analyzer.InstalledAppsRepository
import com.Hrshd1eux.filemanager.core.storage.permission.StoragePermissionManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel managing all user-configurable settings in ARCA.
 * Fully wired to real repositories, ensuring immediate reactivity,
 * meaningful error feedback, and persistence across app restarts.
 */
class SettingsViewModel @JvmOverloads constructor(
    private val displayPreferencesRepository: DisplayPreferencesRepository = DisplayPreferencesRepository(),
    private val appSettingsRepository: AppSettingsRepository = AppSettingsRepository.getInstance(),
    private val archivePreferencesRepository: ArchivePreferencesRepository = ArchivePreferencesRepository.getInstance(),
    private val vaultLockManager: VaultLockManager = VaultLockManager.getInstance()
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = appSettingsRepository.themeMode
    val dynamicColor: StateFlow<Boolean> = appSettingsRepository.dynamicColor
    val showHiddenFiles: StateFlow<Boolean> = displayPreferencesRepository.showHiddenFiles
    val confirmDelete: StateFlow<Boolean> = appSettingsRepository.confirmDelete

    val enabledArchiveFormats: StateFlow<Set<ArchiveFormatOption>> = archivePreferencesRepository.enabledFormats
    val defaultArchiveFormat: StateFlow<ArchiveFormatOption> = archivePreferencesRepository.defaultFormat
    val defaultCompressionLevel: StateFlow<CompressionLevelPreset> = archivePreferencesRepository.defaultLevel

    val vaultLockSettings: StateFlow<VaultLockSettings> = vaultLockManager.lockSettings

    private val _hasAllFilesAccess = MutableStateFlow(false)
    val hasAllFilesAccess: StateFlow<Boolean> = _hasAllFilesAccess.asStateFlow()

    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun refreshPermissions(context: Context) {
        _hasAllFilesAccess.value = StoragePermissionManager.hasAllFilesAccess(context)
        _hasUsageAccess.value = InstalledAppsRepository.getInstance(context).hasUsageStatsPermission()
    }

    fun setThemeMode(mode: ThemeMode) {
        appSettingsRepository.updateThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        appSettingsRepository.updateDynamicColor(enabled)
    }

    fun setShowHiddenFiles(show: Boolean) {
        displayPreferencesRepository.updateShowHiddenFiles(show)
    }

    fun setConfirmDelete(confirm: Boolean) {
        appSettingsRepository.updateConfirmDelete(confirm)
    }

    fun setDefaultArchiveFormat(format: ArchiveFormatOption) {
        archivePreferencesRepository.setDefaultFormat(format)
            .onFailure { error ->
                _userMessage.tryEmit(error.message ?: "Failed to set default archive format")
            }
    }

    fun toggleArchiveFormat(format: ArchiveFormatOption, enabled: Boolean) {
        archivePreferencesRepository.toggleFormatEnabled(format, enabled)
            .onFailure { error ->
                _userMessage.tryEmit(error.message ?: "Failed to update archive format")
            }
    }

    fun setDefaultCompressionLevel(preset: CompressionLevelPreset) {
        archivePreferencesRepository.setDefaultLevel(preset)
    }

    fun updateVaultLockSettings(settings: VaultLockSettings) {
        vaultLockManager.updateSettings(settings)
    }

    fun lockVaultNow() {
        vaultLockManager.manualLock()
        _userMessage.tryEmit("Private Vault locked.")
    }
}
