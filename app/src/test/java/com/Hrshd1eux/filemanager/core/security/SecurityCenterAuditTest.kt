package com.Hrshd1eux.filemanager.core.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.security.engine.SecurityAuditEngine
import com.Hrshd1eux.filemanager.core.security.model.SecurityAuditReport
import com.Hrshd1eux.filemanager.core.security.model.SecurityClaimCategory
import com.Hrshd1eux.filemanager.core.security.model.SecurityCheckStatus
import com.Hrshd1eux.filemanager.core.security.model.SecurityOverallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Rigorous Unit & Truthfulness Test Suite for Version 1.5.6 – 1.5.10:
 * Security Center with Verified, Accurately Worded Claims.
 */
class SecurityCenterAuditTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testContext: Context
    private lateinit var fakeFilesDir: File

    @Before
    fun setUp() {
        fakeFilesDir = tempFolder.newFolder("files")
        testContext = TestContext(fakeFilesDir)
    }

    // =========================================================================
    // 1.5.6 & 1.5.7 Audit Engine & Verified Claims Catalog
    // =========================================================================

    @Test
    fun performAudit_producesComprehensiveAndCompleteClaimsList() {
        val engine = SecurityAuditEngine(rootBinaries = emptyList())
        val report = engine.performAudit(testContext)

        assertNotNull(report)
        assertTrue("Report must contain at least 8 evaluated claims", report.claims.size >= 8)

        val categories = report.claims.map { it.category }.toSet()
        assertTrue("Must cover VAULT_ENCRYPTION", categories.contains(SecurityClaimCategory.VAULT_ENCRYPTION))
        assertTrue("Must cover HARDWARE_KEYSTORE", categories.contains(SecurityClaimCategory.HARDWARE_KEYSTORE))
        assertTrue("Must cover LOCAL_PRIVACY", categories.contains(SecurityClaimCategory.LOCAL_PRIVACY))
        assertTrue("Must cover NETWORK_SECURITY", categories.contains(SecurityClaimCategory.NETWORK_SECURITY))
        assertTrue("Must cover STORAGE_SANDBOX", categories.contains(SecurityClaimCategory.STORAGE_SANDBOX))
        assertTrue("Must cover FLASH_STORAGE_ERASURE", categories.contains(SecurityClaimCategory.FLASH_STORAGE_ERASURE))
        assertTrue("Must cover ENVIRONMENT_INTEGRITY", categories.contains(SecurityClaimCategory.ENVIRONMENT_INTEGRITY))
    }

    // =========================================================================
    // 1.5.8 Truthfulness & Zero Exaggeration Verification
    // =========================================================================

    @Test
    fun verifiedClaims_strictlyAvoidDeceptiveMarketingPhrases() {
        val forbiddenBuzzwords = listOf(
            "military-grade",
            "unbreakable",
            "hack-proof",
            "bulletproof",
            "unhackable",
            "impossible to decrypt",
            "100% secure"
        )

        val engine = SecurityAuditEngine(rootBinaries = emptyList())
        val report = engine.performAudit(testContext)

        for (claim in report.claims) {
            val fullText = "${claim.title} ${claim.verifiedFact} ${claim.technicalProof} ${claim.platformLimitations}".lowercase()
            for (word in forbiddenBuzzwords) {
                assertFalse(
                    "Security claim '${claim.id}' MUST NOT contain deceptive marketing term '$word'",
                    fullText.contains(word)
                )
            }
        }
    }

    @Test
    fun storageSandboxClaim_truthfullyDisclosesAndroidPrivateSandboxLimitation() {
        val engine = SecurityAuditEngine()
        val report = engine.performAudit(testContext)
        val storageClaim = report.claims.first { it.category == SecurityClaimCategory.STORAGE_SANDBOX }

        assertTrue(
            "Storage claim must explicitly state that ARCA cannot access other apps' /data/data/ sandbox",
            storageClaim.platformLimitations.contains("/data/data/")
        )
        assertTrue(
            "Storage claim must reference UID isolation or sandbox",
            storageClaim.platformLimitations.lowercase().contains("sandbox")
        )
    }

    @Test
    fun flashErasureClaim_truthfullyDisclosesWearLevelingLimitation() {
        val engine = SecurityAuditEngine()
        val report = engine.performAudit(testContext)
        val flashClaim = report.claims.first { it.category == SecurityClaimCategory.FLASH_STORAGE_ERASURE }

        assertEquals(SecurityCheckStatus.INFO, flashClaim.status)
        assertTrue(
            "Flash erasure claim must explicitly disclose wear-leveling reality",
            flashClaim.platformLimitations.lowercase().contains("wear-leveling")
        )
        assertTrue(
            "Flash erasure claim must explain that software overwrite cannot guarantee physical erasure of remapped blocks",
            flashClaim.platformLimitations.lowercase().contains("nand") || flashClaim.platformLimitations.lowercase().contains("flash")
        )
    }

    @Test
    fun rootIntegrityClaim_truthfullyExplainsRootCompromiseWithoutExaggeratedDefenseClaims() {
        val engine = SecurityAuditEngine()
        val report = engine.performAudit(testContext)
        val envClaim = report.claims.first { it.category == SecurityClaimCategory.ENVIRONMENT_INTEGRITY }

        assertTrue(
            "Root claim must honestly disclose that root allows OS-level sandbox bypass",
            envClaim.platformLimitations.lowercase().contains("superuser") || envClaim.platformLimitations.lowercase().contains("sandboxing")
        )
        assertTrue(
            "Root claim must not claim to prevent superuser inspection",
            envClaim.platformLimitations.contains("does not claim to prevent")
        )
    }

    @Test
    fun rootDetection_flagsCautionWhenRootBinaryPresent() {
        val fakeSu = tempFolder.newFile("fake_su")
        val engine = SecurityAuditEngine(rootBinaries = listOf(fakeSu.absolutePath))
        val report = engine.performAudit(testContext)
        val envClaim = report.claims.first { it.category == SecurityClaimCategory.ENVIRONMENT_INTEGRITY }

        assertEquals(SecurityCheckStatus.CAUTION, envClaim.status)
        assertTrue("Verified fact must mention root or su presence", envClaim.verifiedFact.contains("root binaries"))
        assertNotNull("Must suggest recommendation on rooted device", envClaim.actionRecommendation)
    }

    // =========================================================================
    // 1.5.9 Vault & Cryptography Claim Technical Specifics
    // =========================================================================

    @Test
    fun vaultClaim_specifiesAesGcmAndPbkdf2Parameters() {
        val engine = SecurityAuditEngine()
        val report = engine.performAudit(testContext)
        val vaultClaim = report.claims.first { it.category == SecurityClaimCategory.VAULT_ENCRYPTION }

        assertTrue(vaultClaim.verifiedFact.contains("AES-256"))
        assertTrue(vaultClaim.verifiedFact.contains("Galois/Counter Mode (GCM)"))
        assertTrue(vaultClaim.technicalProof.contains("AES/GCM/NoPadding"))
        assertTrue(vaultClaim.technicalProof.contains("128-bit authentication tags"))
        assertTrue(vaultClaim.technicalProof.contains("PBKDF2-HMAC-SHA256"))
    }

    @Test
    fun localPrivacyClaim_disclosesUserInitiatedNetworkCapabilityWithoutBlanketExclusion() {
        val engine = SecurityAuditEngine()
        val report = engine.performAudit(testContext)
        val privacyClaim = report.claims.first { it.category == SecurityClaimCategory.LOCAL_PRIVACY }

        // Must state offline-first and zero analytics SDKs
        assertTrue(privacyClaim.verifiedFact.contains("zero third-party analytics"))

        // Must disclose that ARCA supports user-initiated remote connections (no false blanket "never communicates over network")
        assertTrue(
            "Privacy claim must explain user-initiated remote network connections (SFTP/SMB/FTP/WebDAV)",
            privacyClaim.platformLimitations.contains("user-initiated") || privacyClaim.platformLimitations.contains("SFTP")
        )
    }

    @Test
    fun vaultClaim_disclosesVolatileMemoryLimitationWhenUnlocked() {
        val engine = SecurityAuditEngine()
        val report = engine.performAudit(testContext)
        val vaultClaim = report.claims.first { it.category == SecurityClaimCategory.VAULT_ENCRYPTION }

        assertTrue(
            "Vault claim must disclose volatile RAM behavior and lock/unlock reality",
            vaultClaim.platformLimitations.contains("volatile") || vaultClaim.platformLimitations.contains("RAM")
        )
    }

    @Test
    fun networkSecurityClaim_specifiesHostKeyPinningAndTlsValidation() {
        val engine = SecurityAuditEngine()
        val report = engine.performAudit(testContext)
        val netClaim = report.claims.first { it.id == "claim_ssh_pinning_tls_validation" }

        assertTrue(netClaim.verifiedFact.contains("SSH host-key verification"))
        assertTrue(netClaim.verifiedFact.contains("TLS certificate chains"))
        assertTrue(netClaim.technicalProof.contains("SshHostKeyVerifier"))
        assertTrue(netClaim.technicalProof.contains("TlsCertificateValidator"))
    }

    // =========================================================================
    // 1.5.10 Report Diagnostic Export Formatting
    // =========================================================================

    @Test
    fun formatDiagnosticSummary_producesSanitizedSecretFreeSummary() {
        val engine = SecurityAuditEngine(rootBinaries = emptyList())
        val report = engine.performAudit(testContext)
        val summary = report.formatDiagnosticSummary()

        assertTrue(summary.contains("=== ARCA SECURITY CENTER AUDIT REPORT ==="))
        assertTrue(summary.contains("Overall Status:"))
        assertTrue(summary.contains("Vault AES-256-GCM"))
        assertTrue(summary.contains("Zero Telemetry"))
        assertTrue(summary.contains("=== END OF SECURITY REPORT ==="))

        // Zero secret / credential leakage check
        assertFalse(summary.contains("password="))
        assertFalse(summary.contains("BEGIN PRIVATE KEY"))
        assertFalse(summary.contains("auth_token"))
        assertFalse(summary.contains("client_secret"))
    }

    // =========================================================================
    // Test Fakes for Android Context & SharedPreferences
    // =========================================================================

    private class TestContext(private val baseDir: File) : ContextWrapper(null) {
        private val prefs = FakeSharedPreferences()

        override fun getFilesDir(): File = baseDir

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return prefs
        }

        override fun getApplicationContext(): Context = this
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = map[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { temp[key ?: ""] = values; return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { temp[key ?: ""] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { temp.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { temp.clear(); return this }
        override fun commit(): Boolean { map.putAll(temp); return true }
        override fun apply() { map.putAll(temp) }
    }
}
