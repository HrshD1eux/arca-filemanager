package com.Hrshd1eux.filemanager.core.drop.repository

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.ArcaApplication
import com.Hrshd1eux.filemanager.core.drop.model.DropHistoryRetention
import com.Hrshd1eux.filemanager.core.drop.model.DropLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages persistent storage and configurable retention (1 Month, Lifetime, Stop)
 * for ARCA Drop P2P transfer activity logs.
 */
class ArcaDropHistoryRepository(context: Context? = null) {

    private val appContext: Context? = context?.applicationContext
        ?: runCatching { ArcaApplication.instance?.applicationContext }.getOrNull()

    private val prefs: SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    private val _retention = MutableStateFlow(loadRetention())
    val retention: StateFlow<DropHistoryRetention> = _retention.asStateFlow()

    private val _logs = MutableStateFlow(loadLogsAndPurge())
    val logs: StateFlow<List<DropLogEntry>> = _logs.asStateFlow()

    fun appendLog(message: String, isError: Boolean = false): DropLogEntry? {
        val currentRetention = _retention.value
        if (currentRetention == DropHistoryRetention.STOP) {
            return null
        }

        val entry = DropLogEntry(
            timestampMs = System.currentTimeMillis(),
            message = message,
            isError = isError
        )

        val updated = (listOf(entry) + _logs.value)
        val purged = applyRetentionFilter(updated, currentRetention)
        saveLogs(purged)
        return entry
    }

    fun updateRetention(retention: DropHistoryRetention) {
        _retention.value = retention
        prefs?.edit()?.putString(KEY_RETENTION, retention.name)?.apply()

        if (retention == DropHistoryRetention.STOP) {
            // Stop recording; clear existing logs as user explicitly opted out
            saveLogs(emptyList())
        } else {
            val purged = applyRetentionFilter(_logs.value, retention)
            saveLogs(purged)
        }
    }

    fun clearHistory() {
        saveLogs(emptyList())
    }

    private fun applyRetentionFilter(
        entries: List<DropLogEntry>,
        retentionPolicy: DropHistoryRetention
    ): List<DropLogEntry> {
        return when (retentionPolicy) {
            DropHistoryRetention.STOP -> emptyList()
            DropHistoryRetention.ONE_MONTH -> {
                val cutoff = System.currentTimeMillis() - ONE_MONTH_MILLIS
                entries.filter { it.timestampMs >= cutoff }.take(MAX_PERSISTED_LOGS)
            }
            DropHistoryRetention.LIFETIME -> {
                entries.take(MAX_PERSISTED_LOGS)
            }
        }
    }

    private fun saveLogs(newList: List<DropLogEntry>) {
        _logs.value = newList
        val encoded = runCatching { json.encodeToString(newList) }.getOrNull()
        prefs?.edit()?.putString(KEY_LOGS_JSON, encoded)?.apply()
    }

    private fun loadRetention(): DropHistoryRetention {
        val name = prefs?.getString(KEY_RETENTION, DropHistoryRetention.ONE_MONTH.name)
        return runCatching {
            DropHistoryRetention.valueOf(name ?: DropHistoryRetention.ONE_MONTH.name)
        }.getOrDefault(DropHistoryRetention.ONE_MONTH)
    }

    private fun loadLogsAndPurge(): List<DropLogEntry> {
        if (prefs == null) return emptyList()
        val rawJson = prefs.getString(KEY_LOGS_JSON, null) ?: return emptyList()
        val loaded = runCatching {
            json.decodeFromString<List<DropLogEntry>>(rawJson)
        }.getOrDefault(emptyList())

        val currentRetention = loadRetention()
        val filtered = applyRetentionFilter(loaded, currentRetention)
        if (filtered.size != loaded.size) {
            val encoded = runCatching { json.encodeToString(filtered) }.getOrNull()
            prefs.edit().putString(KEY_LOGS_JSON, encoded).apply()
        }
        return filtered
    }

    companion object {
        private const val PREFS_NAME = "arca_drop_history_prefs"
        private const val KEY_LOGS_JSON = "drop_logs_json"
        private const val KEY_RETENTION = "drop_history_retention"
        private const val MAX_PERSISTED_LOGS = 1000
        private const val ONE_MONTH_MILLIS = 30L * 24L * 60L * 60L * 1000L // 30 days
    }
}
