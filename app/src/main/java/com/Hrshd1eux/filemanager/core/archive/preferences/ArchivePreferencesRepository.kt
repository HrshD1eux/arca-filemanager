package com.Hrshd1eux.filemanager.core.archive.preferences

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.ArcaApplication
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionLevelPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages archive engine format settings, enabled format restrictions, and defaults.
 * Guarantees that at least one format remains enabled and invalid states are rejected with meaningful errors.
 */
class ArchivePreferencesRepository(context: Context? = null) {

    private val appContext: Context? = context?.applicationContext
        ?: runCatching { ArcaApplication.instance?.applicationContext }.getOrNull()

    private val prefs: SharedPreferences? = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabledFormats = MutableStateFlow(loadEnabledFormats())
    val enabledFormats: StateFlow<Set<ArchiveFormatOption>> = _enabledFormats.asStateFlow()

    private val _defaultFormat = MutableStateFlow(loadDefaultFormat())
    val defaultFormat: StateFlow<ArchiveFormatOption> = _defaultFormat.asStateFlow()

    private val _defaultLevel = MutableStateFlow(loadDefaultLevel())
    val defaultLevel: StateFlow<CompressionLevelPreset> = _defaultLevel.asStateFlow()

    fun setDefaultFormat(format: ArchiveFormatOption): Result<Unit> {
        if (format !in _enabledFormats.value) {
            return Result.failure(IllegalStateException("Cannot set disabled format ${format.displayName} as default."))
        }
        _defaultFormat.value = format
        prefs?.edit()?.putString(KEY_DEFAULT_FORMAT, format.name)?.apply()
        return Result.success(Unit)
    }

    fun toggleFormatEnabled(format: ArchiveFormatOption, enabled: Boolean): Result<Unit> {
        val current = _enabledFormats.value
        if (!enabled) {
            if (current.size <= 1 && current.contains(format)) {
                return Result.failure(IllegalStateException("At least one archive format must remain enabled."))
            }
            val newSet = current - format
            _enabledFormats.value = newSet
            saveEnabledFormats(newSet)

            // If the disabled format was default, fallback to another enabled format
            if (_defaultFormat.value == format) {
                val newDefault = newSet.first()
                _defaultFormat.value = newDefault
                prefs?.edit()?.putString(KEY_DEFAULT_FORMAT, newDefault.name)?.apply()
            }
            return Result.success(Unit)
        } else {
            val newSet = current + format
            _enabledFormats.value = newSet
            saveEnabledFormats(newSet)
            return Result.success(Unit)
        }
    }

    fun setDefaultLevel(level: CompressionLevelPreset) {
        _defaultLevel.value = level
        prefs?.edit()?.putString(KEY_DEFAULT_LEVEL, level.name)?.apply()
    }

    fun isFormatEnabled(format: ArchiveFormatOption): Boolean {
        return _enabledFormats.value.contains(format)
    }

    private fun loadEnabledFormats(): Set<ArchiveFormatOption> {
        val sp = prefs ?: return DEFAULT_ENABLED_FORMATS
        val saved = sp.getStringSet(KEY_ENABLED_FORMATS, null) ?: return DEFAULT_ENABLED_FORMATS
        val parsed = saved.mapNotNull { name ->
            runCatching { ArchiveFormatOption.valueOf(name) }.getOrNull()
        }.toSet()
        return if (parsed.isNotEmpty()) parsed else DEFAULT_ENABLED_FORMATS
    }

    private fun loadDefaultFormat(): ArchiveFormatOption {
        val sp = prefs ?: return ArchiveFormatOption.ZIP
        val name = sp.getString(KEY_DEFAULT_FORMAT, ArchiveFormatOption.ZIP.name) ?: ArchiveFormatOption.ZIP.name
        val parsed = runCatching { ArchiveFormatOption.valueOf(name) }.getOrDefault(ArchiveFormatOption.ZIP)
        return if (parsed in _enabledFormats.value) parsed else _enabledFormats.value.firstOrNull() ?: ArchiveFormatOption.ZIP
    }

    private fun loadDefaultLevel(): CompressionLevelPreset {
        val sp = prefs ?: return CompressionLevelPreset.NORMAL
        val name = sp.getString(KEY_DEFAULT_LEVEL, CompressionLevelPreset.NORMAL.name) ?: CompressionLevelPreset.NORMAL.name
        return runCatching { CompressionLevelPreset.valueOf(name) }.getOrDefault(CompressionLevelPreset.NORMAL)
    }

    private fun saveEnabledFormats(formats: Set<ArchiveFormatOption>) {
        val names = formats.map { it.name }.toSet()
        prefs?.edit()?.putStringSet(KEY_ENABLED_FORMATS, names)?.apply()
    }

    companion object {
        private const val PREFS_NAME = "arca_archive_prefs"
        private const val KEY_ENABLED_FORMATS = "archive_enabled_formats"
        private const val KEY_DEFAULT_FORMAT = "archive_default_format"
        private const val KEY_DEFAULT_LEVEL = "archive_default_level"

        val DEFAULT_ENABLED_FORMATS: Set<ArchiveFormatOption> = ArchiveFormatOption.entries.toSet()

        @Volatile
        private var instance: ArchivePreferencesRepository? = null

        fun getInstance(context: Context? = null): ArchivePreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: ArchivePreferencesRepository(context).also { instance = it }
            }
        }
    }
}
