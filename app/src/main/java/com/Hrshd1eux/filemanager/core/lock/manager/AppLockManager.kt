package com.Hrshd1eux.filemanager.core.lock.manager

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.lock.biometric.BiometricAuthCoordinator
import com.Hrshd1eux.filemanager.core.lock.model.AppLockMode
import com.Hrshd1eux.filemanager.core.lock.model.AppLockSettings
import com.Hrshd1eux.filemanager.core.lock.model.AppLockTimeout
import com.Hrshd1eux.filemanager.core.lock.model.PinUnlockResult
import com.Hrshd1eux.filemanager.core.lock.security.AppLockSecurity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Primary coordinator managing App Lock lifecycle, PIN authentication,
 * progressive brute-force lockout, and biometric unlock integration.
 */
class AppLockManager(
    private val context: Context? = null,
    val biometricCoordinator: BiometricAuthCoordinator = BiometricAuthCoordinator(context)
) {

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppLockSettings> = _settings.asStateFlow()

    private val _isAppLocked = MutableStateFlow(_settings.value.isConfigured)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

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
        if (_settings.value.isConfigured && _settings.value.lockOnScreenOff) {
            lockApp()
        }
    }

    fun onAppBackgrounded(timestamp: Long = System.currentTimeMillis()) {
        backgroundTimestamp = timestamp
        if (_settings.value.isConfigured && _settings.value.lockOnBackground && _settings.value.timeout == AppLockTimeout.IMMEDIATELY) {
            lockApp()
        }
    }

    fun onAppForegrounded(currentTime: Long = System.currentTimeMillis()) {
        if (!_settings.value.isConfigured) return

        if (backgroundTimestamp > 0L) {
            val elapsed = currentTime - backgroundTimestamp
            val timeout = _settings.value.timeout
            val shouldLock = _settings.value.lockOnBackground && (timeout == AppLockTimeout.IMMEDIATELY || elapsed >= timeout.durationMs)

            if (shouldLock) {
                lockApp()
            }
            backgroundTimestamp = 0L
        }
    }

    fun lockApp() {
        if (_settings.value.isConfigured) {
            _isAppLocked.value = true
        }
    }

    /**
     * Attempts to unlock the app with a user-supplied PIN.
     * Enforces rate limiting and progressive lockout.
     */
    fun unlockWithPin(pin: String, currentTime: Long = System.currentTimeMillis()): PinUnlockResult {
        val currentSettings = _settings.value
        if (!currentSettings.isConfigured) {
            _isAppLocked.value = false
            return PinUnlockResult.Success
        }

        // Check if currently locked out
        if (currentSettings.isCurrentlyLockedOut(currentTime)) {
            val remainingSecs = currentSettings.remainingLockoutSeconds(currentTime)
            return PinUnlockResult.LockedOut(remainingSecs)
        }

        val isValid = AppLockSecurity.verifyPin(
            inputPin = pin,
            saltHex = currentSettings.pinSaltHex,
            expectedHashHex = currentSettings.pinHashHex
        )

        return if (isValid) {
            // Reset failed attempts upon successful authentication
            val updated = currentSettings.copy(
                failedAttempts = 0,
                lockoutUntilTimestamp = 0L
            )
            saveSettings(updated)
            _isAppLocked.value = false
            PinUnlockResult.Success
        } else {
            val newFailures = currentSettings.failedAttempts + 1
            val lockoutDuration = AppLockSecurity.calculateLockoutDurationMs(newFailures)
            val lockoutUntil = if (lockoutDuration > 0) currentTime + lockoutDuration else 0L

            val updated = currentSettings.copy(
                failedAttempts = newFailures,
                lockoutUntilTimestamp = lockoutUntil
            )
            saveSettings(updated)

            if (lockoutDuration > 0) {
                PinUnlockResult.LockedOut(lockoutDuration / 1000L)
            } else {
                val attemptsLeft = (AppLockSecurity.MAX_ATTEMPTS_BEFORE_FIRST_LOCKOUT - newFailures).coerceAtLeast(1)
                PinUnlockResult.InvalidPin(attemptsLeft)
            }
        }
    }

    /**
     * Unlocks the app via verified biometric authentication.
     */
    fun unlockWithBiometrics(): Boolean {
        val currentSettings = _settings.value
        if (currentSettings.isBiometricsActive && !currentSettings.isCurrentlyLockedOut()) {
            _isAppLocked.value = false
            return true
        }
        return false
    }

    /**
     * Sets or updates the master PIN.
     */
    fun setPin(newPin: String, enableBiometrics: Boolean = false) {
        require(newPin.length in 4..8) { "PIN length must be between 4 and 8 digits" }
        val saltBytes = AppLockSecurity.generateSalt()
        val saltHex = AppLockSecurity.byteArrayToHex(saltBytes)
        val hashHex = AppLockSecurity.hashPin(newPin, saltBytes)

        val updated = _settings.value.copy(
            mode = if (enableBiometrics) AppLockMode.BIOMETRIC_WITH_PIN else AppLockMode.PIN,
            pinSaltHex = saltHex,
            pinHashHex = hashHex,
            pinLength = newPin.length,
            failedAttempts = 0,
            lockoutUntilTimestamp = 0L
        )
        saveSettings(updated)
    }

    /**
     * Disables App Lock after verifying current PIN.
     */
    fun disableAppLock(currentPin: String): Boolean {
        val currentSettings = _settings.value
        if (!currentSettings.isConfigured) return true

        val isValid = AppLockSecurity.verifyPin(
            inputPin = currentPin,
            saltHex = currentSettings.pinSaltHex,
            expectedHashHex = currentSettings.pinHashHex
        )

        return if (isValid) {
            val updated = currentSettings.copy(
                mode = AppLockMode.DISABLED,
                pinSaltHex = "",
                pinHashHex = "",
                failedAttempts = 0,
                lockoutUntilTimestamp = 0L
            )
            saveSettings(updated)
            _isAppLocked.value = false
            true
        } else {
            false
        }
    }

    fun updateSettings(settings: AppLockSettings) {
        saveSettings(settings)
    }

    private fun loadSettings(): AppLockSettings {
        val sp = prefs ?: return AppLockSettings()
        val modeStr = sp.getString(KEY_MODE, AppLockMode.DISABLED.name) ?: AppLockMode.DISABLED.name
        val mode = runCatching { AppLockMode.valueOf(modeStr) }.getOrDefault(AppLockMode.DISABLED)

        val timeoutStr = sp.getString(KEY_TIMEOUT, AppLockTimeout.IMMEDIATELY.name) ?: AppLockTimeout.IMMEDIATELY.name
        val timeout = runCatching { AppLockTimeout.valueOf(timeoutStr) }.getOrDefault(AppLockTimeout.IMMEDIATELY)

        return AppLockSettings(
            mode = mode,
            timeout = timeout,
            lockOnScreenOff = sp.getBoolean(KEY_LOCK_SCREEN_OFF, true),
            lockOnBackground = sp.getBoolean(KEY_LOCK_BACKGROUND, true),
            pinSaltHex = sp.getString(KEY_PIN_SALT, "") ?: "",
            pinHashHex = sp.getString(KEY_PIN_HASH, "") ?: "",
            pinLength = sp.getInt(KEY_PIN_LENGTH, 4),
            failedAttempts = sp.getInt(KEY_FAILED_ATTEMPTS, 0),
            lockoutUntilTimestamp = sp.getLong(KEY_LOCKOUT_UNTIL, 0L)
        )
    }

    private fun saveSettings(settings: AppLockSettings) {
        _settings.value = settings
        prefs?.edit()?.apply {
            putString(KEY_MODE, settings.mode.name)
            putString(KEY_TIMEOUT, settings.timeout.name)
            putBoolean(KEY_LOCK_SCREEN_OFF, settings.lockOnScreenOff)
            putBoolean(KEY_LOCK_BACKGROUND, settings.lockOnBackground)
            putString(KEY_PIN_SALT, settings.pinSaltHex)
            putString(KEY_PIN_HASH, settings.pinHashHex)
            putInt(KEY_PIN_LENGTH, settings.pinLength)
            putInt(KEY_FAILED_ATTEMPTS, settings.failedAttempts)
            putLong(KEY_LOCKOUT_UNTIL, settings.lockoutUntilTimestamp)
            apply()
        }
    }

    companion object {
        const val PREFS_NAME = "arca_app_lock_prefs"
        const val KEY_MODE = "lock_mode"
        const val KEY_TIMEOUT = "lock_timeout"
        const val KEY_LOCK_SCREEN_OFF = "lock_screen_off"
        const val KEY_LOCK_BACKGROUND = "lock_background"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"

        @Volatile
        private var INSTANCE: AppLockManager? = null

        fun getInstance(context: Context? = null): AppLockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppLockManager(context?.applicationContext ?: context).also { INSTANCE = it }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
