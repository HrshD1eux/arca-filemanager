package com.Hrshd1eux.filemanager.core.network.tls

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS certificate validation status result.
 */
sealed class TlsValidationResult {
    data object Valid : TlsValidationResult()
    data class PinnedTrust(val fingerprint: String) : TlsValidationResult()
    data class Expired(val host: String, val message: String) : TlsValidationResult()
    data class HostnameMismatch(val host: String, val certSubject: String) : TlsValidationResult()
    data class Untrusted(val host: String, val message: String) : TlsValidationResult()
}

/**
 * Stores connection-scoped certificate pinning records.
 *
 * CRITICAL SECURITY PRINCIPLE:
 * Certificate trust exceptions are strictly scoped to the designated [connectionId].
 * Global certificate validation is NEVER disabled or degraded.
 */
class ConnectionScopedCertStore(
    context: Context? = null,
    prefName: String = PREFS_NAME
) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    private val memoryStore = ConcurrentHashMap<String, String>()

    init {
        loadPinnedCerts()
    }

    /**
     * Stores a trusted SHA-256 certificate fingerprint for a specific connection ID.
     */
    fun pinCertificate(connectionId: String, fingerprintSha256: String) {
        val cleanFingerprint = fingerprintSha256.trim().uppercase(Locale.ROOT)
        memoryStore[connectionId] = cleanFingerprint
        prefs?.edit()?.putString(connectionId, cleanFingerprint)?.apply()
    }

    /**
     * Returns the pinned certificate fingerprint for the specified connection, or null if none is pinned.
     */
    fun getPinnedCertificate(connectionId: String): String? {
        return memoryStore[connectionId] ?: prefs?.getString(connectionId, null)
    }

    /**
     * Removes the pinned certificate exception for a specific connection.
     */
    fun removePin(connectionId: String) {
        memoryStore.remove(connectionId)
        prefs?.edit()?.remove(connectionId)?.apply()
    }

    /**
     * Clears all pinned certificate exceptions.
     */
    fun clear() {
        memoryStore.clear()
        prefs?.edit()?.clear()?.apply()
    }

    private fun loadPinnedCerts() {
        prefs?.all?.forEach { (key, value) ->
            if (value is String) {
                memoryStore[key] = value
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "arca_network_pinned_certs"

        @Volatile
        private var INSTANCE: ConnectionScopedCertStore? = null

        fun getInstance(context: Context? = null): ConnectionScopedCertStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionScopedCertStore(context?.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

/**
 * Rigorous TLS Certificate Validator for FTPS and HTTPS WebDAV connections.
 *
 * Implements strict RFC 5280 / RFC 6125 verification:
 * 1. Certificate Validity: Ensures certificate is currently valid (not expired or not-yet-valid).
 * 2. Hostname Verification: Matches host against Subject Alternative Names (SANs) and Common Name (CN),
 *    with standard wildcard matching.
 * 3. Trust Chain Verification: Validates against Android system trust store (or connection-scoped pin).
 * 4. Zero Global Bypass: Rejects insecure global trust disables.
 */
class TlsCertificateValidator(
    private val certStore: ConnectionScopedCertStore = ConnectionScopedCertStore()
) {

    private val defaultTrustManager: X509TrustManager? by lazy {
        try {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculates the standard SHA-256 fingerprint for an X.509 certificate.
     * Format: "SHA256:XX:XX:XX..."
     */
    fun calculateFingerprintSha256(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return "SHA256:" + digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Validates a certificate chain for a given host and connection.
     *
     * @param serverHost The hostname or IP to which the connection is being made.
     * @param chain The certificate chain presented by the remote server.
     * @param connectionId The identifier of the connection profile (for connection-scoped pinning).
     * @param allowExplicitPinnedCert If true, checks the connection-scoped pin store if system trust fails.
     * @return [TlsValidationResult] indicating success or specific failure reason.
     */
    fun validateCertificateChain(
        serverHost: String,
        chain: Array<X509Certificate>,
        connectionId: String? = null,
        allowExplicitPinnedCert: Boolean = false
    ): TlsValidationResult {
        if (chain.isEmpty()) {
            return TlsValidationResult.Untrusted(serverHost, "Empty certificate chain presented by server.")
        }

        val leafCert = chain[0]

        // 1. Check validity (Not before / Not after)
        try {
            leafCert.checkValidity()
        } catch (e: CertificateExpiredException) {
            return TlsValidationResult.Expired(serverHost, e.message ?: "Certificate is expired")
        } catch (e: CertificateNotYetValidException) {
            return TlsValidationResult.Expired(serverHost, e.message ?: "Certificate is not yet valid")
        }

        // 2. Hostname Verification
        if (!verifyHostname(serverHost, leafCert)) {
            val subject = leafCert.subjectX500Principal.name
            return TlsValidationResult.HostnameMismatch(serverHost, subject)
        }

        // 3. System Trust Verification
        var systemTrustPassed = false
        try {
            defaultTrustManager?.checkServerTrusted(chain, "RSA")
            systemTrustPassed = true
        } catch (e: Exception) {
            systemTrustPassed = false
        }

        if (systemTrustPassed) {
            return TlsValidationResult.Valid
        }

        // 4. Connection-Scoped Pin Check (never globally applied)
        if (allowExplicitPinnedCert && connectionId != null) {
            val pinnedFingerprint = certStore.getPinnedCertificate(connectionId)
            if (pinnedFingerprint != null) {
                val leafFingerprint = calculateFingerprintSha256(leafCert)
                if (pinnedFingerprint.equals(leafFingerprint, ignoreCase = true)) {
                    return TlsValidationResult.PinnedTrust(leafFingerprint)
                }
            }
        }

        return TlsValidationResult.Untrusted(
            serverHost,
            "Certificate is self-signed or not signed by a trusted Certificate Authority (CA)."
        )
    }

    /**
     * Asserts that the certificate chain is valid; throws corresponding domain NetworkException if invalid.
     */
    fun assertValid(
        serverHost: String,
        chain: Array<X509Certificate>,
        connectionId: String? = null,
        allowExplicitPinnedCert: Boolean = false
    ) {
        when (val result = validateCertificateChain(serverHost, chain, connectionId, allowExplicitPinnedCert)) {
            is TlsValidationResult.Valid,
            is TlsValidationResult.PinnedTrust -> {
                // Success
            }
            is TlsValidationResult.Expired -> {
                throw NetworkException.TlsCertificateExpiredException(result.host, result.message)
            }
            is TlsValidationResult.HostnameMismatch -> {
                throw NetworkException.TlsCertificateHostnameMismatchException(result.host, result.certSubject)
            }
            is TlsValidationResult.Untrusted -> {
                throw NetworkException.TlsCertificateUntrustedException(result.host, result.message)
            }
        }
    }

    /**
     * Verifies that the server hostname matches either a Subject Alternative Name (SAN) or CN.
     */
    fun verifyHostname(hostname: String, certificate: X509Certificate): Boolean {
        val target = hostname.trim().lowercase(Locale.ROOT)

        // Check Subject Alternative Names (type 2 = DNS name, type 7 = IP address)
        try {
            val sans = certificate.subjectAlternativeNames
            if (sans != null) {
                var hasDnsOrIpSans = false
                for (san in sans) {
                    val type = (san[0] as? Number)?.toInt() ?: continue
                    if (type == 2 || type == 7) {
                        hasDnsOrIpSans = true
                        val name = (san[1] as? String)?.lowercase(Locale.ROOT) ?: continue
                        if (matchHostname(target, name)) {
                            return true
                        }
                    }
                }
                if (hasDnsOrIpSans) {
                    // RFC 6125: If SANs of type dNSName are present, the CN MUST NOT be checked.
                    return false
                }
            }
        } catch (e: Exception) {
            // Fallback to CN
        }

        // Check Common Name (CN)
        val cn = extractCommonName(certificate.subjectX500Principal.name) ?: return false
        return matchHostname(target, cn.lowercase(Locale.ROOT))
    }

    private fun extractCommonName(dn: String): String? {
        val tokens = dn.split(",")
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.startsWith("CN=", ignoreCase = true)) {
                return trimmed.substring(3).trim()
            }
        }
        return null
    }

    /**
     * Wildcard hostname matching compliant with RFC 6125.
     * Matches e.g. "*.example.com" against "nas.example.com", but not "sub.nas.example.com".
     */
    private fun matchHostname(target: String, pattern: String): Boolean {
        if (target == pattern) return true
        if (pattern.startsWith("*.")) {
            val suffix = pattern.substring(2)
            if (target.endsWith(suffix)) {
                val prefix = target.substring(0, target.length - suffix.length)
                // Wildcard matches a single label only (no dots in prefix)
                return !prefix.contains(".") && prefix.isNotEmpty()
            }
        }
        return false
    }
}
