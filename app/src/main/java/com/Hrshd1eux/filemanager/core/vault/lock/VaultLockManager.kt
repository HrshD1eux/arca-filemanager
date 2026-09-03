package com.Hrshd1eux.filemanager.core.vault.lock

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.vault.VaultManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class VaultLockManager(
    private val context: Context? = null,
    private val vaultManager: VaultManager? = null
) {

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _lockSettings = MutableStateFlow(loadSettings())
    val lockSettings: StateFlow<VaultLockSettings> = _lockSettings.asStateFlow()

    private val _lockEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lockEvents: SharedFlow<Unit> = _lockEvents.asSharedFlow()

    @Volatile
    var lastInteractionTimestamp: Long = System.currentTimeMillis()
        private set

    @Volatile
    var backgroundTimestamp: Long = 0L
        private set

    fun onUserInteraction() {
        lastInteractionTimestamp = System.currentTimeMillis()
    }

    fun onScreenOff() {
        if (_lockSettings.value.lockOnScreenOff) {
            triggerLock()
        }
    }

    fun onAppBackgrounded() {
        backgroundTimestamp = System.currentTimeMillis()
        if (_lockSettings.value.lockOnBackground && _lockSettings.value.autoLockTimeout == VaultAutoLockTimeout.IMMEDIATELY) {
            triggerLock()
        }
    }

    fun onAppForegrounded() {
        if (backgroundTimestamp > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            val timeout = _lockSettings.value.autoLockTimeout
            val shouldLock = _lockSettings.value.lockOnBackground ||
                    (timeout != VaultAutoLockTimeout.NEVER && elapsed >= timeout.durationMs)

            if (shouldLock) {
                triggerLock()
            }
            backgroundTimestamp = 0L
        }
    }

    fun checkIdleTimeout() {
        val timeout = _lockSettings.value.autoLockTimeout
        if (timeout != VaultAutoLockTimeout.NEVER && timeout != VaultAutoLockTimeout.IMMEDIATELY) {
            val idleDuration = System.currentTimeMillis() - lastInteractionTimestamp
            if (idleDuration >= timeout.durationMs) {
                triggerLock()
            }
        }
    }

    fun manualLock() {
        triggerLock()
    }

    fun triggerLock() {
        vaultManager?.lock()
        _lockEvents.tryEmit(Unit)
    }

    fun updateSettings(settings: VaultLockSettings) {
        _lockSettings.value = settings
        prefs?.edit()?.apply {
            putString(KEY_TIMEOUT, settings.autoLockTimeout.name)
            putBoolean(KEY_LOCK_ON_BG, settings.lockOnBackground)
            putBoolean(KEY_LOCK_ON_SCREEN_OFF, settings.lockOnScreenOff)
            apply()
        }
    }

    private fun loadSettings(): VaultLockSettings {
        val sp = prefs ?: return VaultLockSettings()
        val timeoutName = sp.getString(KEY_TIMEOUT, VaultAutoLockTimeout.FIVE_MINUTES.name)
        val timeout = runCatching {
            VaultAutoLockTimeout.valueOf(timeoutName ?: VaultAutoLockTimeout.FIVE_MINUTES.name)
        }.getOrDefault(VaultAutoLockTimeout.FIVE_MINUTES)
        val lockOnBg = sp.getBoolean(KEY_LOCK_ON_BG, true)
        val lockOnScreenOff = sp.getBoolean(KEY_LOCK_ON_SCREEN_OFF, true)

        return VaultLockSettings(
            autoLockTimeout = timeout,
            lockOnBackground = lockOnBg,
            lockOnScreenOff = lockOnScreenOff
        )
    }

    companion object {
        private const val PREFS_NAME = "arca_vault_lock_prefs"
        private const val KEY_TIMEOUT = "vault_auto_lock_timeout"
        private const val KEY_LOCK_ON_BG = "vault_lock_on_bg"
        private const val KEY_LOCK_ON_SCREEN_OFF = "vault_lock_on_screen_off"

        @Volatile
        private var instance: VaultLockManager? = null

        fun getInstance(context: Context? = null, vaultManager: VaultManager? = null): VaultLockManager {
            return instance ?: synchronized(this) {
                instance ?: VaultLockManager(
                    context = context?.applicationContext,
                    vaultManager = vaultManager
                ).also { instance = it }
            }
        }
    }
}
