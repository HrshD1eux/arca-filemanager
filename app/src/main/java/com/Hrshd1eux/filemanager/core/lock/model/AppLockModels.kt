package com.Hrshd1eux.filemanager.core.lock.model

import kotlinx.serialization.Serializable

/**
 * Operating mode for App Lock.
 */
@Serializable
enum class AppLockMode(val displayName: String) {
    DISABLED("Disabled"),
    PIN("PIN Code"),
    BIOMETRIC_WITH_PIN("Biometric with PIN Fallback")
}

/**
 * Inactivity timeout before ARCA locks automatically.
 */
@Serializable
enum class AppLockTimeout(val durationMs: Long, val displayName: String) {
    IMMEDIATELY(0L, "Immediately on Background"),
    THIRTY_SECONDS(30_000L, "30 seconds"),
    ONE_MINUTE(60_000L, "1 minute"),
    FIVE_MINUTES(300_000L, "5 minutes"),
    FIFTEEN_MINUTES(900_000L, "15 minutes")
}

/**
 * Result of attempting to unlock with a PIN.
 */
sealed interface PinUnlockResult {
    data object Success : PinUnlockResult
    data class InvalidPin(val attemptsRemainingBeforeLockout: Int) : PinUnlockResult
    data class LockedOut(val remainingSeconds: Long) : PinUnlockResult
}

/**
 * Status of hardware biometric support on the current device.
 */
enum class BiometricCapabilityStatus(val displayName: String) {
    READY("Biometric Authentication Ready"),
    NOT_ENROLLED("No Biometrics Enrolled in Android Settings"),
    NO_HARDWARE("No Biometric Sensor Available"),
    UNAVAILABLE("Biometric Sensor Temporarily Unavailable")
}

/**
 * Comprehensive persistent configuration for App Lock.
 */
@Serializable
data class AppLockSettings(
    val mode: AppLockMode = AppLockMode.DISABLED,
    val timeout: AppLockTimeout = AppLockTimeout.IMMEDIATELY,
    val lockOnScreenOff: Boolean = true,
    val lockOnBackground: Boolean = true,
    val pinSaltHex: String = "",
    val pinHashHex: String = "",
    val pinLength: Int = 4,
    val failedAttempts: Int = 0,
    val lockoutUntilTimestamp: Long = 0L
) {
    val isConfigured: Boolean
        get() = mode != AppLockMode.DISABLED && pinHashHex.isNotEmpty() && pinSaltHex.isNotEmpty()

    val isBiometricsActive: Boolean
        get() = mode == AppLockMode.BIOMETRIC_WITH_PIN

    fun isCurrentlyLockedOut(currentTime: Long = System.currentTimeMillis()): Boolean {
        return lockoutUntilTimestamp > currentTime
    }

    fun remainingLockoutSeconds(currentTime: Long = System.currentTimeMillis()): Long {
        return if (lockoutUntilTimestamp > currentTime) {
            (lockoutUntilTimestamp - currentTime + 999L) / 1000L
        } else 0L
    }
}
