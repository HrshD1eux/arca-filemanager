package com.Hrshd1eux.filemanager.core.lock.biometric

import android.content.Context
import android.os.Build
import com.Hrshd1eux.filemanager.core.lock.model.BiometricCapabilityStatus

/**
 * Platform-independent coordinator for checking biometric capabilities
 * and mediating hardware unlock.
 *
 * TRUTHFUL SECURITY DESIGN:
 * 1. Biometrics provide local convenience on top of the PIN.
 * 2. Biometrics can NEVER be enabled without an enrolled, verified PIN fallback.
 * 3. Platform limitations (e.g. sensor smudged, reboot requirement) fall back to PIN seamlessly.
 */
class BiometricAuthCoordinator(
    private val context: Context? = null
) {

    /**
     * Inspects the current device's biometric capability status.
     */
    fun checkBiometricCapability(): BiometricCapabilityStatus {
        val ctx = context ?: return BiometricCapabilityStatus.UNAVAILABLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val biometricManager = ctx.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
                if (biometricManager == null) {
                    BiometricCapabilityStatus.NO_HARDWARE
                } else {
                    when (biometricManager.canAuthenticate()) {
                        android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS ->
                            BiometricCapabilityStatus.READY
                        android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                            BiometricCapabilityStatus.NOT_ENROLLED
                        android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                            BiometricCapabilityStatus.NO_HARDWARE
                        else ->
                            BiometricCapabilityStatus.UNAVAILABLE
                    }
                }
            } catch (_: Throwable) {
                BiometricCapabilityStatus.UNAVAILABLE
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 to 9.0 legacy fingerprint manager fallback
            return try {
                @Suppress("DEPRECATION")
                val fingerprintManager = ctx.getSystemService(Context.FINGERPRINT_SERVICE) as? android.hardware.fingerprint.FingerprintManager
                if (fingerprintManager == null || !fingerprintManager.isHardwareDetected) {
                    BiometricCapabilityStatus.NO_HARDWARE
                } else if (!fingerprintManager.hasEnrolledFingerprints()) {
                    BiometricCapabilityStatus.NOT_ENROLLED
                } else {
                    BiometricCapabilityStatus.READY
                }
            } catch (_: Throwable) {
                BiometricCapabilityStatus.UNAVAILABLE
            }
        } else {
            return BiometricCapabilityStatus.NO_HARDWARE
        }
    }
}
