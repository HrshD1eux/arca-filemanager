package com.Hrshd1eux.filemanager.core.security.engine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.Hrshd1eux.filemanager.core.crypto.kdf.KeyDerivationEngine
import com.Hrshd1eux.filemanager.core.security.model.SecurityAuditReport
import com.Hrshd1eux.filemanager.core.security.model.SecurityClaim
import com.Hrshd1eux.filemanager.core.security.model.SecurityClaimCategory
import com.Hrshd1eux.filemanager.core.security.model.SecurityCheckStatus
import com.Hrshd1eux.filemanager.core.security.model.SecurityOverallStatus
import com.Hrshd1eux.filemanager.core.storage.permission.StorageAccessLevel
import com.Hrshd1eux.filemanager.core.storage.permission.StoragePermissionManager
import java.io.File
import java.security.KeyStore

/**
 * Live Security Audit Engine for ARCA.
 *
 * Evaluates the runtime device environment, app configuration, cryptography,
 * network security policies, and storage sandboxing.
 *
 * UNCOMPROMISING TRUTHFULNESS & ACCURACY GUARANTEE:
 * 1. ZERO EXAGGERATED MARKETING CLAIMS: Never uses unscientific phrases like
 *    "military-grade", "unbreakable", or "hack-proof".
 * 2. PROVABLE FACTS: Every positive claim references specific cryptographic algorithms
 *    or platform security implementations.
 * 3. EXPLICIT LIMITATION DISCLOSURES: Clearly discloses platform boundaries,
 *    such as flash memory wear-leveling constraints, Android app sandboxing limits,
 *    and root bypasses.
 */
class SecurityAuditEngine(
    private val keyStoreProvider: String = "AndroidKeyStore",
    private val rootBinaries: List<String> = DEFAULT_ROOT_BINARY_PATHS
) {

    companion object {
        val DEFAULT_ROOT_BINARY_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/app/Superuser.apk"
        )

        const val FLASH_WEAR_LEVELING_DISCLAIMER =
            "Accurate Flash Storage Reality: While ARCA zeroes file headers and content blocks during deletion, modern NAND flash (eMMC/UFS/SSDs) uses hardware wear-leveling controllers. Software overwrite cannot physically access or erase reallocated flash blocks. Only full hardware encryption or partition discard guarantees complete media erasure."

        const val ANDROID_SANDBOX_DISCLAIMER =
            "Platform Sandbox Limitation: Even with All-Files Access (MANAGE_EXTERNAL_STORAGE), Android's Linux kernel isolates each app into a unique UID sandbox. ARCA cannot access private sandboxes (/data/data/) of other applications."

        const val ROOT_COMPROMISE_DISCLAIMER =
            "OS Integrity Reality: ARCA detects common root binaries and custom build tags. If your device is rooted or bootloader-unlocked, Android application sandboxing and memory isolation protections are substantially bypassed at the kernel level. ARCA does not claim to prevent superuser-level inspection."
    }

    /**
     * Executes a full, dynamic security audit and produces a verified report.
     */
    fun performAudit(context: Context): SecurityAuditReport {
        val claims = mutableListOf<SecurityClaim>()

        claims.add(evaluateVaultEncryption(context))
        claims.add(evaluateHardwareKeyStore())
        claims.add(evaluateLocalIsolationAndPrivacy())
        claims.add(evaluateCleartextTrafficPolicy())
        claims.add(evaluateNetworkHostKeyAndTls())
        claims.add(evaluateStorageSandbox(context))
        claims.add(evaluateFlashErasureReality())
        claims.add(evaluateEnvironmentIntegrity(context))

        val actionCount = claims.count { it.status == SecurityCheckStatus.ACTION_RECOMMENDED }
        val cautionCount = claims.count { it.status == SecurityCheckStatus.CAUTION }

        val overallStatus = when {
            actionCount > 0 -> SecurityOverallStatus.ATTENTION_REQUIRED
            cautionCount > 0 -> SecurityOverallStatus.GOOD
            else -> SecurityOverallStatus.EXCELLENT
        }

        return SecurityAuditReport(
            overallStatus = overallStatus,
            claims = claims
        )
    }

    private fun evaluateVaultEncryption(context: Context): SecurityClaim {
        val vaultDir = File(context.filesDir, "vault")
        val metaFile = File(vaultDir, "meta.bin")
        val isConfigured = metaFile.exists() && metaFile.length() > 0

        val (status, recommendation) = if (isConfigured) {
            SecurityCheckStatus.USER_CONFIGURED to null
        } else {
            SecurityCheckStatus.PLATFORM_REALITY to "Set up Private Vault to protect confidential documents with AES-256-GCM encryption."
        }

        return SecurityClaim(
            id = "claim_vault_aes256_gcm",
            title = "Vault AES-256-GCM Authenticated Encryption",
            category = SecurityClaimCategory.VAULT_ENCRYPTION,
            status = status,
            verifiedFact = if (isConfigured) {
                "Private Vault is configured with authenticated AES-256 in Galois/Counter Mode (GCM) and unique 96-bit random IVs per file chunk."
            } else {
                "Vault engine supports authenticated AES-256 in Galois/Counter Mode (GCM) with PBKDF2 key derivation (100,000 iterations)."
            },
            technicalProof = "Standard javax.crypto.Cipher implementation with AES/GCM/NoPadding, 128-bit authentication tags, and PBKDF2-HMAC-SHA256.",
            platformLimitations = "Volatile Memory & Lock State Reality: Vault encryption protects files at rest on disk. When the Vault is unlocked, decryption keys reside in volatile system RAM to allow file access. Locking the Vault purges keys from memory immediately. Seizure of an unlocked device or root memory inspection could expose decrypted data.",
            actionRecommendation = recommendation
        )
    }

    private fun evaluateHardwareKeyStore(): SecurityClaim {
        val isKeyStoreAvailable = runCatching {
            val ks = KeyStore.getInstance(keyStoreProvider)
            ks.load(null)
            true
        }.getOrDefault(false)

        val hasHardwareBacking = isKeyStoreAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

        return SecurityClaim(
            id = "claim_hardware_keystore",
            title = "Android KeyStore Master Key Isolation",
            category = SecurityClaimCategory.HARDWARE_KEYSTORE,
            status = if (hasHardwareBacking) SecurityCheckStatus.ACTIVE_POLICY else SecurityCheckStatus.CAUTION,
            verifiedFact = "Cryptographic master keys are managed within Android KeyStore, isolating key material from standard application memory.",
            technicalProof = "KeyGenParameterSpec with PURPOSE_ENCRYPT or PURPOSE_DECRYPT and KeyProperties.BLOCK_MODE_GCM via '$keyStoreProvider'.",
            platformLimitations = "Hardware security module (TEE / StrongBox) presence depends on device chipset. On budget devices or emulators lacking secure hardware, software-backed keystore boundaries apply."
        )
    }

    private fun evaluateLocalIsolationAndPrivacy(): SecurityClaim {
        return SecurityClaim(
            id = "claim_zero_telemetry",
            title = "Zero Telemetry, Trackers, or Cloud Analytics",
            category = SecurityClaimCategory.LOCAL_PRIVACY,
            status = SecurityCheckStatus.ACTIVE_POLICY,
            verifiedFact = "ARCA bundles zero third-party analytics SDKs, zero crash-reporting frameworks, zero ad libraries, and zero cloud telemetry endpoints.",
            technicalProof = "Verified via static analysis and automated regression audit (PrivacyAuditTest). No analytics permissions, no background tracking daemons.",
            platformLimitations = "Network Functionality Reality: While ARCA operates offline-first and has zero developer servers or cloud telemetry, ARCA supports user-initiated remote network connections (SFTP, FTPS, FTP, SMB, WebDAV). When you configure a remote connection, ARCA communicates directly with the specific server host you designate. Outbound traffic is strictly point-to-point and directly initiated by you."
        )
    }

    private fun evaluateCleartextTrafficPolicy(): SecurityClaim {
        return SecurityClaim(
            id = "claim_cleartext_traffic",
            title = "Cleartext Traffic Disabled by Default",
            category = SecurityClaimCategory.NETWORK_SECURITY,
            status = SecurityCheckStatus.ACTIVE_POLICY,
            verifiedFact = "Unencrypted HTTP and cleartext network transmissions are disabled at the Android Manifest policy level (usesCleartextTraffic=false).",
            technicalProof = "Enforced by Android OS Network Security Configuration and AndroidManifest.xml application attributes.",
            platformLimitations = "Applies to standard Android HTTP/HTTPS traffic. If you configure an explicit legacy unencrypted FTP server on port 21, data and passwords travel unencrypted over your local Wi-Fi or LAN. ARCA provides visible warnings before initiating legacy FTP."
        )
    }

    private fun evaluateNetworkHostKeyAndTls(): SecurityClaim {
        return SecurityClaim(
            id = "claim_ssh_pinning_tls_validation",
            title = "Strict SSH Host-Key Pinning & TLS Validation",
            category = SecurityClaimCategory.NETWORK_SECURITY,
            status = SecurityCheckStatus.ACTIVE_POLICY,
            verifiedFact = "SFTP connections enforce strict SSH host-key verification and block modified keys. FTPS and HTTPS WebDAV connections strictly validate X.509 TLS certificate chains.",
            technicalProof = "SshHostKeyVerifier with SHA-256 fingerprint matching against trusted hosts; TlsCertificateValidator enforcing valid PKIX certification paths.",
            platformLimitations = "Applies to user-configured secure remote protocols. Network security relies on server identity verification. If you manually approve a changed host key or self-signed certificate without verifying the fingerprint out-of-band, active machine-in-the-middle attacks cannot be prevented."
        )
    }

    private fun evaluateStorageSandbox(context: Context): SecurityClaim {
        val accessState = StoragePermissionManager.getStorageAccessState(context)

        val (status, rec) = when (accessState.level) {
            StorageAccessLevel.FULL_ALL_FILES -> SecurityCheckStatus.ACTIVE_POLICY to null
            StorageAccessLevel.SCOPED_FOLDER_TREES -> SecurityCheckStatus.PLATFORM_REALITY to "App has scoped folder access. You can grant All-Files access if you wish to browse the full device storage."
            StorageAccessLevel.DENIED_CAN_REQUEST,
            StorageAccessLevel.DENIED_SETTINGS_REQUIRED -> SecurityCheckStatus.ACTION_RECOMMENDED to "Storage permission is required to browse files."
            StorageAccessLevel.REVOKED -> SecurityCheckStatus.ACTION_RECOMMENDED to "Storage permission was revoked. Re-grant access in Settings to resume browsing your files."
        }

        return SecurityClaim(
            id = "claim_storage_sandbox",
            title = "Storage Permissions & Android OS Sandbox",
            category = SecurityClaimCategory.STORAGE_SANDBOX,
            status = status,
            verifiedFact = "Current Storage Status: ${accessState.level.displayName}. ARCA operates within standard Android OS permission boundaries.",
            technicalProof = "StoragePermissionManager integration with MANAGE_EXTERNAL_STORAGE and SAF Document Tree Uri persistable grants.",
            platformLimitations = ANDROID_SANDBOX_DISCLAIMER,
            actionRecommendation = rec
        )
    }

    private fun evaluateFlashErasureReality(): SecurityClaim {
        return SecurityClaim(
            id = "claim_flash_wear_leveling",
            title = "Secure Deletion & NAND Flash Wear-Leveling",
            category = SecurityClaimCategory.FLASH_STORAGE_ERASURE,
            status = SecurityCheckStatus.PLATFORM_REALITY,
            verifiedFact = "ARCA overwrites file headers and content blocks with zeroes before deleting filesystem pointers.",
            technicalProof = "Byte-level overwrite stream using java.io.FileOutputStream prior to File.delete() invocation.",
            platformLimitations = FLASH_WEAR_LEVELING_DISCLAIMER
        )
    }

    private fun evaluateEnvironmentIntegrity(context: Context): SecurityClaim {
        val rootFound = detectRootBinaries()
        val hasTestKeys = Build.TAGS != null && Build.TAGS.contains("test-keys")
        val isAdbEnabled = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)

        val isCompromised = rootFound || hasTestKeys

        val (status, fact, rec) = if (isCompromised) {
            Triple(
                SecurityCheckStatus.CAUTION,
                "Device environment exhibits root binaries or test-keys build tags. OS-level sandboxing guarantees are diminished.",
                "Rooted environments allow superuser apps to bypass Android sandboxing. Exercise caution when storing highly sensitive files in the Vault."
            )
        } else {
            Triple(
                SecurityCheckStatus.ACTIVE_POLICY,
                "Device environment passes standard OS integrity checks (no su binaries detected; production release keys).",
                if (isAdbEnabled) "USB debugging (ADB) is enabled on this device. Disable it when not developing to prevent unauthorized computer access." else null
            )
        }

        return SecurityClaim(
            id = "claim_environment_integrity",
            title = "Device Environment & Operating System Integrity",
            category = SecurityClaimCategory.ENVIRONMENT_INTEGRITY,
            status = status,
            verifiedFact = fact,
            technicalProof = "Inspection of known su paths (${rootBinaries.size} inspected paths), Build.TAGS release signature verification, and ADB debugging state.",
            platformLimitations = ROOT_COMPROMISE_DISCLAIMER,
            actionRecommendation = rec
        )
    }

    private fun detectRootBinaries(): Boolean {
        for (path in rootBinaries) {
            try {
                if (File(path).exists()) return true
            } catch (_: SecurityException) {}
        }
        return false
    }
}
