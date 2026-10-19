package com.Hrshd1eux.filemanager.core.network.model

import java.io.IOException

/**
 * Domain error hierarchy for remote network operations in ARCA.
 * Isolates the rest of the application from raw network / socket exceptions.
 */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause) {

    /**
     * General connection error when establishing or maintaining session.
     */
    class NetworkConnectionException(
        message: String,
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    /**
     * The target remote host or IP could not be resolved or reached.
     */
    class HostUnreachableException(
        val host: String,
        val port: Int,
        cause: Throwable? = null
    ) : NetworkException("Unable to reach host '$host' on port $port: ${cause?.localizedMessage ?: "Connection refused or unreachable"}", cause)

    /**
     * A socket connect, read, or write timeout expired.
     */
    class NetworkTimeoutException(
        val timeoutMs: Long,
        cause: Throwable? = null
    ) : NetworkException("Network operation timed out after ${timeoutMs}ms", cause)

    /**
     * Authentication failed (invalid password, rejected key pair, expired token, or invalid domain).
     */
    class NetworkAuthException(
        message: String,
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    /**
     * Requested remote path does not exist on the server.
     */
    class RemoteFileNotFoundException(
        val path: String
    ) : NetworkException("Remote path not found: '$path'")

    /**
     * User lacks read/write/execute permissions for the requested remote resource.
     */
    class RemotePermissionDeniedException(
        val path: String
    ) : NetworkException("Permission denied on remote server for path: '$path'")

    /**
     * SSH host key verification detected a changed host key (potential MITM attack).
     */
    class SshHostKeyChangedException(
        val host: String,
        val port: Int,
        val expectedFingerprint: String,
        val actualFingerprint: String
    ) : NetworkException("WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED! Host key for '$host:$port' ($actualFingerprint) does not match trusted key ($expectedFingerprint). Connection aborted for security.")

    /**
     * SSH server host key is not yet trusted / unknown.
     */
    class SshHostKeyUnverifiedException(
        val host: String,
        val port: Int,
        val keyType: String,
        val actualFingerprint: String
    ) : NetworkException("SSH host key for '$host:$port' ($keyType $actualFingerprint) is not trusted. Explicit verification required.")

    /**
     * TLS/SSL handshake failure or untrusted certificate error.
     */
    open class TlsCertificateException(
        message: String,
        cause: Throwable? = null
    ) : NetworkException("TLS/SSL certificate error: $message", cause)

    class TlsCertificateExpiredException(
        val host: String,
        message: String
    ) : TlsCertificateException("Certificate for '$host' is expired: $message")

    class TlsCertificateUntrustedException(
        val host: String,
        message: String
    ) : TlsCertificateException("Certificate for '$host' is not trusted by system authorities: $message")

    class TlsCertificateHostnameMismatchException(
        val host: String,
        val certSubject: String
    ) : TlsCertificateException("Certificate hostname mismatch: Connected to '$host', but certificate was issued for '$certSubject'")

    /**
     * An insecure protocol or dialect was rejected (e.g. attempting SMB1).
     */
    class InsecureProtocolException(
        message: String
    ) : NetworkException("Insecure protocol rejected: $message")

    /**
     * Unexpected protocol-level framing, banner, or response violation.
     */
    class ProtocolViolationException(
        val protocol: NetworkProtocol,
        message: String
    ) : NetworkException("[${protocol.name}] Protocol violation: $message")

    /**
     * A destructive operation (such as delete, move, or rename) failed or was interrupted by a connection drop.
     * Blind retry is strictly forbidden to prevent unintended file loss or state corruption.
     */
    class NonRetriableOperationException(
        val operation: String,
        val targetPath: String,
        message: String,
        cause: Throwable? = null
    ) : NetworkException("Cannot automatically retry destructive operation '$operation' on '$targetPath': $message. Please verify remote state before proceeding.", cause)
}
