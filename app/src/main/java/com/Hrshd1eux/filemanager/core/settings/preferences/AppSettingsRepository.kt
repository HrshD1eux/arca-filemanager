package com.Hrshd1eux.filemanager.core.settings.preferences

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.ArcaApplication
import com.Hrshd1eux.filemanager.core.settings.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages general app settings such as theme mode, dynamic colors, and delete confirmation.
 * Fully persisted via SharedPreferences and reactive via StateFlow.
 */
class AppSettingsRepository(context: Context? = null) {

    private val appContext: Context? = context?.applicationContext
        ?: runCatching { ArcaApplication.instance?.applicationContext }.getOrNull()

    private val prefs: SharedPreferences? = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(loadDynamicColor())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _confirmDelete = MutableStateFlow(loadConfirmDelete())
    val confirmDelete: StateFlow<Boolean> = _confirmDelete.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(loadHapticFeedback())
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val _soundFeedback = MutableStateFlow(loadSoundFeedback())
    val soundFeedback: StateFlow<Boolean> = _soundFeedback.asStateFlow()

    private val _firstRunExplanationCompleted = MutableStateFlow(loadFirstRunExplanationCompleted())
    val firstRunExplanationCompleted: StateFlow<Boolean> = _firstRunExplanationCompleted.asStateFlow()

    fun updateThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun updateDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLOR, enabled)?.apply()
    }

    fun updateConfirmDelete(enabled: Boolean) {
        _confirmDelete.value = enabled
        prefs?.edit()?.putBoolean(KEY_CONFIRM_DELETE, enabled)?.apply()
    }

    fun updateHapticFeedback(enabled: Boolean) {
        _hapticFeedback.value = enabled
        prefs?.edit()?.putBoolean(KEY_HAPTIC_FEEDBACK, enabled)?.apply()
    }

    fun updateSoundFeedback(enabled: Boolean) {
        _soundFeedback.value = enabled
        prefs?.edit()?.putBoolean(KEY_SOUND_FEEDBACK, enabled)?.apply()
    }

    fun setFirstRunExplanationCompleted(completed: Boolean = true) {
        _firstRunExplanationCompleted.value = completed
        prefs?.edit()?.putBoolean(KEY_FIRST_RUN_EXPLANATION_COMPLETED, completed)?.apply()
    }

    fun resetFirstRunExplanation() {
        _firstRunExplanationCompleted.value = false
        prefs?.edit()?.remove(KEY_FIRST_RUN_EXPLANATION_COMPLETED)?.apply()
    }
    fun getLastPromptedUpdateVersion(): String? {
        return prefs?.getString(KEY_LAST_PROMPTED_UPDATE_VERSION, null)
    }

    fun setLastPromptedUpdateVersion(version: String) {
        prefs?.edit()?.putString(KEY_LAST_PROMPTED_UPDATE_VERSION, version)?.apply()
    }


    private fun loadThemeMode(): ThemeMode {
        val sp = prefs ?: return ThemeMode.SYSTEM
        val modeStr = sp.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(modeStr) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private fun loadDynamicColor(): Boolean {
        return prefs?.getBoolean(KEY_DYNAMIC_COLOR, true) ?: true
    }

    private fun loadConfirmDelete(): Boolean {
        return prefs?.getBoolean(KEY_CONFIRM_DELETE, true) ?: true
    }

    private fun loadHapticFeedback(): Boolean {
        return prefs?.getBoolean(KEY_HAPTIC_FEEDBACK, true) ?: true
    }

    private fun loadSoundFeedback(): Boolean {
        return prefs?.getBoolean(KEY_SOUND_FEEDBACK, true) ?: true
    }

    private fun loadFirstRunExplanationCompleted(): Boolean {
        return prefs?.getBoolean(KEY_FIRST_RUN_EXPLANATION_COMPLETED, false) ?: false
    }

    companion object {
        private const val PREFS_NAME = "arca_app_settings"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_DYNAMIC_COLOR = "app_dynamic_color"
        private const val KEY_CONFIRM_DELETE = "app_confirm_delete"
        private const val KEY_HAPTIC_FEEDBACK = "app_haptic_feedback"
        private const val KEY_SOUND_FEEDBACK = "app_sound_feedback"
        private const val KEY_FIRST_RUN_EXPLANATION_COMPLETED = "app_first_run_storage_explanation_completed"
        private const val KEY_LAST_PROMPTED_UPDATE_VERSION = "app_last_prompted_update_version"

        @Volatile
        private var instance: AppSettingsRepository? = null

        fun getInstance(context: Context? = null): AppSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: AppSettingsRepository(context).also { instance = it }
            }
        }
    }
}
