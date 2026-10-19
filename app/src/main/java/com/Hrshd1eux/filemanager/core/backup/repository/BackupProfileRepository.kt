package com.Hrshd1eux.filemanager.core.backup.repository

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.backup.model.BackupMode
import com.Hrshd1eux.filemanager.core.backup.model.BackupProfile
import com.Hrshd1eux.filemanager.core.backup.model.BackupStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupProfileRepository(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("arca_backup_profiles_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _profiles = MutableStateFlow<List<BackupProfile>>(loadProfiles())
    val profiles: StateFlow<List<BackupProfile>> = _profiles.asStateFlow()

    fun getProfiles(): List<BackupProfile> = _profiles.value

    fun getProfileById(id: String): BackupProfile? {
        return _profiles.value.find { it.id == id }
    }

    fun saveProfile(profile: BackupProfile) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        updateProfiles(current)
    }

    fun deleteProfile(id: String) {
        val current = _profiles.value.filter { it.id != id }
        updateProfiles(current)
    }

    fun updateProfileRunResult(
        id: String,
        timestamp: Long,
        status: BackupStatus,
        summary: String
    ) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(
                lastRunTimestamp = timestamp,
                lastRunStatus = status,
                lastRunSummary = summary
            )
            updateProfiles(current)
        }
    }

    fun resetToDefaults() {
        updateProfiles(defaultProfiles())
    }

    private fun updateProfiles(newList: List<BackupProfile>) {
        _profiles.value = newList
        val encoded = runCatching { json.encodeToString(newList) }.getOrNull()
        prefs?.edit()?.putString(KEY_PROFILES_JSON, encoded)?.apply()
    }

    private fun loadProfiles(): List<BackupProfile> {
        val raw = prefs?.getString(KEY_PROFILES_JSON, null)
        if (raw.isNullOrBlank()) {
            return defaultProfiles()
        }
        return runCatching {
            json.decodeFromString<List<BackupProfile>>(raw)
        }.getOrElse { defaultProfiles() }
    }

    private fun defaultProfiles(): List<BackupProfile> {
        return listOf(
            BackupProfile(
                id = "default_dcim_backup",
                name = "Camera Photos Backup",
                sourcePaths = listOf("/storage/emulated/0/DCIM"),
                destinationPath = "/storage/emulated/0/Backups/DCIM",
                mode = BackupMode.INCREMENTAL,
                exclusionPatterns = listOf("*.tmp", ".thumbnails", ".cache")
            ),
            BackupProfile(
                id = "default_documents_backup",
                name = "Documents Backup",
                sourcePaths = listOf("/storage/emulated/0/Documents"),
                destinationPath = "/storage/emulated/0/Backups/Documents",
                mode = BackupMode.INCREMENTAL,
                exclusionPatterns = listOf("*.tmp", "*.part", "~*")
            )
        )
    }

    companion object {
        private const val KEY_PROFILES_JSON = "key_backup_profiles_json"

        @Volatile
        private var INSTANCE: BackupProfileRepository? = null

        fun getInstance(context: Context? = null): BackupProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupProfileRepository(context).also { INSTANCE = it }
            }
        }
    }
}
