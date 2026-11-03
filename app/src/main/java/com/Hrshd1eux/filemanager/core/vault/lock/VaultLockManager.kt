package com.Hrshd1eux.filemanager.core.vault.lock

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.crypto.aead.EncryptedPayload
import com.Hrshd1eux.filemanager.core.crypto.keystore.AndroidKeyStoreManager
import com.Hrshd1eux.filemanager.core.vault.VaultManager
import com.Hrshd1eux.filemanager.core.vault.stealth.VaultStealthSecurity
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

    fun matchesSecretCode(query: String): Boolean {
        val settings = _lockSettings.value
        if (!settings.isVaultHidden) return false
        return VaultStealthSecurity.verifyTriggerCode(
            input = query,
            saltHex = settings.secretCodeSalt,
            hashHex = settings.secretCodeHash
        )
    }

    fun updateSettings(settings: VaultLockSettings) {
        _lockSettings.value = settings
        prefs?.edit()?.apply {
            putString(KEY_TIMEOUT, settings.autoLockTimeout.name)
            putBoolean(KEY_LOCK_ON_BG, settings.lockOnBackground)
            putBoolean(KEY_LOCK_ON_SCREEN_OFF, settings.lockOnScreenOff)
            putBoolean(KEY_IS_VAULT_HIDDEN, settings.isVaultHidden)
            putString(KEY_SECRET_SALT, settings.secretCodeSalt)
            putString(KEY_SECRET_HASH, settings.secretCodeHash)
            putString(KEY_SECRET_ENCRYPTED, settings.encryptedSecretCode)
            putBoolean(KEY_IS_BIOMETRIC_ENABLED, settings.isBiometricEnabled)
            apply()
        }
        if (!settings.isBiometricEnabled) {
            clearBiometricVaultPassword()
        }
    }

    /**
     * Resets all vault lock settings to default (unhidden, standard timeout)
     * and clears any saved biometric credentials.
     */
    fun resetToDefaults() {
        updateSettings(VaultLockSettings())
        clearBiometricVaultPassword()
    }

    fun saveBiometricVaultPassword(password: CharArray) {
        if (context == null || password.isEmpty()) return
        runCatching {
            val ks = AndroidKeyStoreManager()
            val plainBytes = String(password).toByteArray(Charsets.UTF_8)
            val encrypted = ks.encryptWithMasterKey(KEYSTORE_BIOMETRIC_ALIAS, plainBytes)
            prefs?.edit()?.apply {
                putString(KEY_BIOMETRIC_CIPHERTEXT, encrypted.ciphertextWithTag.joinToString("") { "%02x".format(it) })
                putString(KEY_BIOMETRIC_IV, encrypted.iv.joinToString("") { "%02x".format(it) })
                apply()
            }
        }
    }

    fun getBiometricVaultPassword(): CharArray? {
        if (context == null) return null
        return runCatching {
            val sp = prefs ?: return null
            val cipherHex = sp.getString(KEY_BIOMETRIC_CIPHERTEXT, null) ?: return null
            val ivHex = sp.getString(KEY_BIOMETRIC_IV, null) ?: return null
            val ks = AndroidKeyStoreManager()
            val payload = EncryptedPayload(
                iv = hexToBytes(ivHex),
                ciphertextWithTag = hexToBytes(cipherHex)
            )
            val decrypted = ks.decryptWithMasterKey(KEYSTORE_BIOMETRIC_ALIAS, payload)
            String(decrypted, Charsets.UTF_8).toCharArray()
        }.getOrNull()
    }

    fun hasBiometricPassword(): Boolean {
        val sp = prefs ?: return false
        return sp.contains(KEY_BIOMETRIC_CIPHERTEXT) && sp.contains(KEY_BIOMETRIC_IV)
    }

    fun clearBiometricVaultPassword() {
        runCatching {
            val ks = AndroidKeyStoreManager()
            ks.deleteKey(KEYSTORE_BIOMETRIC_ALIAS)
        }
        prefs?.edit()?.apply {
            remove(KEY_BIOMETRIC_CIPHERTEXT)
            remove(KEY_BIOMETRIC_IV)
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
        val isVaultHidden = sp.getBoolean(KEY_IS_VAULT_HIDDEN, false)
        val secretSalt = sp.getString(KEY_SECRET_SALT, "") ?: ""
        val secretHash = sp.getString(KEY_SECRET_HASH, "") ?: ""
        val secretEncrypted = sp.getString(KEY_SECRET_ENCRYPTED, "") ?: ""
        val isBiometricEnabled = sp.getBoolean(KEY_IS_BIOMETRIC_ENABLED, false)

        return VaultLockSettings(
            autoLockTimeout = timeout,
            lockOnBackground = lockOnBg,
            lockOnScreenOff = lockOnScreenOff,
            isVaultHidden = isVaultHidden,
            secretCodeSalt = secretSalt,
            secretCodeHash = secretHash,
            encryptedSecretCode = secretEncrypted,
            isBiometricEnabled = isBiometricEnabled
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            result[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "arca_vault_lock_prefs"
        private const val KEY_TIMEOUT = "vault_auto_lock_timeout"
        private const val KEY_LOCK_ON_BG = "vault_lock_on_bg"
        private const val KEY_LOCK_ON_SCREEN_OFF = "vault_lock_on_screen_off"
        private const val KEY_IS_VAULT_HIDDEN = "vault_is_hidden"
        private const val KEY_SECRET_SALT = "vault_secret_code_salt"
        private const val KEY_SECRET_HASH = "vault_secret_code_hash"
        private const val KEY_SECRET_ENCRYPTED = "vault_secret_code_encrypted"
        private const val KEY_IS_BIOMETRIC_ENABLED = "vault_is_biometric_enabled"
        private const val KEY_BIOMETRIC_CIPHERTEXT = "vault_biometric_cipher"
        private const val KEY_BIOMETRIC_IV = "vault_biometric_iv"
        private const val KEYSTORE_BIOMETRIC_ALIAS = "arca_vault_biometric_master_key"

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
