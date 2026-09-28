package com.Hrshd1eux.filemanager.core.network.model

import java.util.UUID

/**
 * Immutable configuration defining a remote server endpoint, credentials, and connection options.
 */
data class NetworkServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int = 0,
    val authType: NetworkAuthType = NetworkAuthType.ANONYMOUS,
    val username: String? = null,
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
    val domain: String? = null,
    val shareName: String? = null,
    val basePath: String = "/",
    val isPassiveMode: Boolean = true,
    val isImplicitTls: Boolean = false,
    val allowSelfSignedCert: Boolean = false,
    val connectionTimeoutMs: Long = 10_000L,
    val readTimeoutMs: Long = 30_000L
) {
    /**
     * Resolves the effective port (specified port or protocol default).
     */
    val effectivePort: Int
        get() = if (port > 0) port else {
            if (protocol == NetworkProtocol.FTPS && isImplicitTls) 990 else protocol.defaultPort
        }

    /**
     * Validates connection parameters.
     * @throws IllegalArgumentException if configuration is malformed or violates protocol rules.
     */
    fun validate() {
        require(host.isNotBlank()) { "Server hostname or IP address cannot be blank." }
        require(effectivePort in 1..65535) { "Port number must be between 1 and 65535. Given: $effectivePort" }
        require(protocol.supportedAuthTypes.contains(authType)) {
            "Authentication type '$authType' is not supported by protocol '${protocol.name}'."
        }

        when (authType) {
            NetworkAuthType.PASSWORD -> {
                require(!username.isNullOrBlank()) { "Username is required for password authentication." }
            }
            NetworkAuthType.KEY_PAIR -> {
                require(!privateKey.isNullOrBlank()) { "Private key is required for SSH Key authentication." }
            }
            NetworkAuthType.DOMAIN_NTLM -> {
                require(!username.isNullOrBlank()) { "Username is required for domain authentication." }
            }
            NetworkAuthType.TOKEN -> {
                require(!password.isNullOrBlank()) { "Token value is required for token authentication." }
            }
            NetworkAuthType.ANONYMOUS, NetworkAuthType.CERTIFICATE -> {
                // No username required
            }
        }

        if (protocol == NetworkProtocol.SMB && !shareName.isNullOrBlank()) {
            require(!shareName.contains("/") && !shareName.contains("\\")) {
                "SMB share name cannot contain path separators: '$shareName'"
            }
        }
    }

    /**
     * Produces a sanitized URI representation suitable for logs and UI (passwords omitted).
     */
    fun toSanitizedUriString(): String {
        val userPart = if (!username.isNullOrBlank()) "$username@" else ""
        val portPart = if (effectivePort != protocol.defaultPort) ":$effectivePort" else ""
        val sharePart = if (!shareName.isNullOrBlank()) "/$shareName" else ""
        val cleanBasePath = if (basePath.startsWith("/")) basePath else "/$basePath"
        return "${protocol.scheme}://$userPart$host$portPart$sharePart$cleanBasePath"
    }
}
