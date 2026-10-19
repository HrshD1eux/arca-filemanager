package com.Hrshd1eux.filemanager.core.network.ssh

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

enum class SshHostKeyStatus {
    TRUSTED,
    NEW_HOST,
    CHANGED
}

data class SshHostKeyRecord(
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprintSha256: String,
    val rawKeyBase64: String,
    val addedDate: Long = System.currentTimeMillis()
)

data class SshHostKeyVerificationResult(
    val status: SshHostKeyStatus,
    val host: String,
    val port: Int,
    val keyType: String,
    val actualFingerprint: String,
    val expectedFingerprint: String? = null
)

/**
 * Manages trusted SSH server host keys for SFTP connections.
 * Strictly adheres to SSH host-key verification principles:
 * - Detects new hosts and requires explicit verification/approval.
 * - Alerts on changed host keys (mitigating Man-in-the-Middle attacks).
 * - NEVER silently accepts changed host keys.
 */
class SshKnownHostsStore(
    context: Context? = null,
    prefName: String = PREFS_NAME
) {
    private val prefs: SharedPreferences? = context?.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    private val memoryStore = ConcurrentHashMap<String, SshHostKeyRecord>()

    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    init {
        loadStoredKeys()
    }

    /**
     * Verifies the presented SSH host key against the known hosts registry.
     */
    fun verifyHostKey(
        host: String,
        port: Int,
        keyType: String,
        rawKeyBytes: ByteArray
    ): SshHostKeyVerificationResult {
        val normalizedHost = host.trim().lowercase()
        val key = hostKeyId(normalizedHost, port)
        val actualFingerprint = calculateFingerprint(rawKeyBytes)

        val existing = memoryStore[key]
        return when {
            existing == null -> {
                SshHostKeyVerificationResult(
                    status = SshHostKeyStatus.NEW_HOST,
                    host = normalizedHost,
                    port = port,
                    keyType = keyType,
                    actualFingerprint = actualFingerprint
                )
            }
            existing.fingerprintSha256 == actualFingerprint -> {
                SshHostKeyVerificationResult(
                    status = SshHostKeyStatus.TRUSTED,
                    host = normalizedHost,
                    port = port,
                    keyType = keyType,
                    actualFingerprint = actualFingerprint,
                    expectedFingerprint = existing.fingerprintSha256
                )
            }
            else -> {
                // FINGERPRINT MISMATCH — Potential Man-in-the-Middle attack!
                SshHostKeyVerificationResult(
                    status = SshHostKeyStatus.CHANGED,
                    host = normalizedHost,
                    port = port,
                    keyType = keyType,
                    actualFingerprint = actualFingerprint,
                    expectedFingerprint = existing.fingerprintSha256
                )
            }
        }
    }

    /**
     * Explicitly records or updates a trusted host key after user inspection and approval.
     */
    fun trustHostKey(
        host: String,
        port: Int,
        keyType: String,
        rawKeyBytes: ByteArray
    ): SshHostKeyRecord {
        val normalizedHost = host.trim().lowercase()
        val key = hostKeyId(normalizedHost, port)
        val fingerprint = calculateFingerprint(rawKeyBytes)
        val base64Key = base64Encoder.encodeToString(rawKeyBytes)

        val record = SshHostKeyRecord(
            host = normalizedHost,
            port = port,
            keyType = keyType,
            fingerprintSha256 = fingerprint,
            rawKeyBase64 = base64Key
        )

        memoryStore[key] = record
        persistRecord(record)
        return record
    }

    /**
     * Removes a host key entry from known hosts.
     */
    fun removeHostKey(host: String, port: Int) {
        val normalizedHost = host.trim().lowercase()
        val key = hostKeyId(normalizedHost, port)
        memoryStore.remove(key)
        prefs?.edit()?.remove(key)?.apply()
    }

    /**
     * Returns all known host entries.
     */
    fun getAllKnownHosts(): List<SshHostKeyRecord> = memoryStore.values.toList()

    /**
     * Clears all known host records.
     */
    fun clear() {
        memoryStore.clear()
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * Calculates the standard OpenSSH SHA-256 fingerprint ("SHA256:<base64-without-padding>").
     */
    fun calculateFingerprint(rawKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawKeyBytes)
        val b64 = base64Encoder.encodeToString(digest).trimEnd('=')
        return "SHA256:$b64"
    }

    private fun hostKeyId(host: String, port: Int): String = "$host:$port"

    private fun persistRecord(record: SshHostKeyRecord) {
        if (prefs == null) return
        val serialized = "${record.host}|${record.port}|${record.keyType}|${record.fingerprintSha256}|${record.rawKeyBase64}|${record.addedDate}"
        val key = hostKeyId(record.host, record.port)
        prefs.edit().putString(key, serialized).apply()
    }

    private fun loadStoredKeys() {
        prefs?.all?.forEach { (key, value) ->
            if (value is String) {
                val parts = value.split('|')
                if (parts.size >= 6) {
                    val port = parts[1].toIntOrNull() ?: 22
                    val addedDate = parts[5].toLongOrNull() ?: System.currentTimeMillis()
                    val record = SshHostKeyRecord(
                        host = parts[0],
                        port = port,
                        keyType = parts[2],
                        fingerprintSha256 = parts[3],
                        rawKeyBase64 = parts[4],
                        addedDate = addedDate
                    )
                    memoryStore[key] = record
                }
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "arca_ssh_known_hosts"

        @Volatile
        private var INSTANCE: SshKnownHostsStore? = null

        fun getInstance(context: Context? = null): SshKnownHostsStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SshKnownHostsStore(context?.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
