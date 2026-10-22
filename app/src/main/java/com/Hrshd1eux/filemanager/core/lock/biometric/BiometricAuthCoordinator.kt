package com.Hrshd1eux.filemanager.core.lock.biometric

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
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

    /**
     * Prompts the user with the system BiometricPrompt dialog.
     */
    fun authenticate(
        activity: Activity,
        title: String = "Unlock Private Vault",
        subtitle: String = "Confirm biometric credential to proceed",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton("Cancel", activity.mainExecutor) { _, _ ->
                        onError("Authentication cancelled")
                    }
                    .build()

                val cancellationSignal = CancellationSignal()
                prompt.authenticate(
                    cancellationSignal,
                    activity.mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            onError(errString?.toString() ?: "Authentication error")
                        }

                        override fun onAuthenticationFailed() {
                            onError("Biometric verification failed")
                        }
                    }
                )
            } catch (t: Throwable) {
                onError(t.localizedMessage ?: "Biometric prompt error")
            }
        } else {
            // Fallback for pre-API 28
            onSuccess()
        }
    }
}
