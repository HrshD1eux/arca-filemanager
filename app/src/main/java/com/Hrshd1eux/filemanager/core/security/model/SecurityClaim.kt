package com.Hrshd1eux.filemanager.core.security.model

/**
 * Categorization of security claims and checks within ARCA's Security Center.
 */
enum class SecurityClaimCategory(val displayName: String) {
    VAULT_ENCRYPTION("Vault & Cryptography"),
    HARDWARE_KEYSTORE("Android KeyStore & Key Isolation"),
    LOCAL_PRIVACY("Local Isolation & Zero Telemetry"),
    NETWORK_SECURITY("Network Security & Host Verification"),
    STORAGE_SANDBOX("Storage Access & Platform Sandboxing"),
    FLASH_STORAGE_ERASURE("Secure Erasure & Flash Media"),
    ENVIRONMENT_INTEGRITY("Device Environment & OS Integrity")
}

/**
 * Status of an individual security check.
 */
enum class SecurityCheckStatus(val label: String) {
    PASSED("Verified"),
    CAUTION("Caution"),
    ACTION_RECOMMENDED("Attention"),
    INFO("Informational")
}

/**
 * Overall security posture level.
 */
enum class SecurityOverallStatus(val title: String, val description: String) {
    EXCELLENT(
        "Optimal Security Posture",
        "Hardware-backed keys, local isolation, and strict network verification are fully active."
    ),
    GOOD(
        "Strong Security Posture",
        "Key protections are active. Review minor informational advisories below."
    ),
    ATTENTION_REQUIRED(
        "Attention Recommended",
        "One or more security settings or permissions require user review."
    )
}

/**
 * Represents a verified, unexaggerated technical claim and audit item.
 *
 * Each claim MUST strictly separate:
 * 1. Verified Fact: The actual capability implemented in the code.
 * 2. Technical Proof: The underlying algorithm, library, or platform API.
 * 3. Platform Limitations & Reality: Transparent disclaimers regarding what software
 *    can and cannot guarantee (e.g. flash wear-leveling, root access, OS boundaries).
 */
data class SecurityClaim(
    val id: String,
    val title: String,
    val category: SecurityClaimCategory,
    val status: SecurityCheckStatus,
    val verifiedFact: String,
    val technicalProof: String,
    val platformLimitations: String,
    val actionRecommendation: String? = null,
    val isActionable: Boolean = actionRecommendation != null
)

/**
 * Comprehensive security audit report generated at runtime.
 */
data class SecurityAuditReport(
    val timestamp: Long = System.currentTimeMillis(),
    val overallStatus: SecurityOverallStatus,
    val claims: List<SecurityClaim>,
    val verifiedCount: Int = claims.count { it.status == SecurityCheckStatus.PASSED },
    val cautionCount: Int = claims.count { it.status == SecurityCheckStatus.CAUTION },
    val actionCount: Int = claims.count { it.status == SecurityCheckStatus.ACTION_RECOMMENDED },
    val infoCount: Int = claims.count { it.status == SecurityCheckStatus.INFO }
) {
    /**
     * Formats a sanitized, secret-free diagnostic summary for export or clipboard copy.
     */
    fun formatDiagnosticSummary(): String = buildString {
        appendLine("=== ARCA SECURITY CENTER AUDIT REPORT ===")
        appendLine("Timestamp: $timestamp")
        appendLine("Overall Status: ${overallStatus.title} (${overallStatus.name})")
        appendLine("Verified Checks: $verifiedCount / ${claims.size}")
        appendLine("Advisories: $cautionCount caution, $actionCount attention, $infoCount info")
        appendLine()
        appendLine("--- DETAILED CLAIMS & VERIFICATIONS ---")
        for (claim in claims) {
            appendLine("[${claim.status.label.uppercase()}] ${claim.title} (${claim.category.displayName})")
            appendLine("  Verified Fact: ${claim.verifiedFact}")
            appendLine("  Technical Proof: ${claim.technicalProof}")
            appendLine("  Platform Reality: ${claim.platformLimitations}")
            if (claim.actionRecommendation != null) {
                appendLine("  Action: ${claim.actionRecommendation}")
            }
            appendLine()
        }
        appendLine("=== END OF SECURITY REPORT ===")
    }
}
