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
 *
 * Replaces simplistic permanent checkmarks with transparent behavioral classifications:
 * - ACTIVE_POLICY: Verified local application policy or OS configuration
 * - USER_CONFIGURED: Protection active for a user-configured feature
 * - PLATFORM_REALITY: Transparent platform limitation or boundary
 * - CAUTION: Elevated risk environment (e.g. root binary detection)
 * - ACTION_RECOMMENDED: Explicit action recommended to enable protection
 */
enum class SecurityCheckStatus(val label: String) {
    ACTIVE_POLICY("Active Policy"),
    USER_CONFIGURED("Configured Feature"),
    PLATFORM_REALITY("Platform Boundary"),
    CAUTION("Advisory"),
    ACTION_RECOMMENDED("Attention");

    companion object {
        // Compatibility aliases
        val PASSED get() = ACTIVE_POLICY
        val INFO get() = PLATFORM_REALITY
    }
}

/**
 * Overall security posture level.
 */
enum class SecurityOverallStatus(val title: String, val description: String) {
    EXCELLENT(
        "Verified Protections Active",
        "Local protections and policy boundaries are active. Outbound network traffic is limited strictly to user-initiated connections."
    ),
    GOOD(
        "Verified Protections Active with Advisories",
        "Key protections are active. Review informational advisories and platform boundaries below."
    ),
    ATTENTION_REQUIRED(
        "Review Recommended",
        "One or more security settings or storage permissions require user attention."
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
    val verifiedCount: Int = claims.count { it.status == SecurityCheckStatus.ACTIVE_POLICY || it.status == SecurityCheckStatus.USER_CONFIGURED },
    val cautionCount: Int = claims.count { it.status == SecurityCheckStatus.CAUTION },
    val actionCount: Int = claims.count { it.status == SecurityCheckStatus.ACTION_RECOMMENDED },
    val infoCount: Int = claims.count { it.status == SecurityCheckStatus.PLATFORM_REALITY }
) {
    /**
     * Formats a sanitized, secret-free diagnostic summary for export or clipboard copy.
     */
    fun formatDiagnosticSummary(): String = buildString {
        appendLine("=== ARCA SECURITY CENTER AUDIT REPORT ===")
        appendLine("Timestamp: $timestamp")
        appendLine("Overall Status: ${overallStatus.title} (${overallStatus.name})")
        appendLine("Active Policies & Features: $verifiedCount / ${claims.size}")
        appendLine("Advisories & Platform Boundaries: $cautionCount caution, $actionCount attention, $infoCount platform boundaries")
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
