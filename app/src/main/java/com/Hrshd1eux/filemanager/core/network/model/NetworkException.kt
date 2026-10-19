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
     * TLS/SSL handshake failure or untrusted certificate error.
     */
    class TlsCertificateException(
        message: String,
        cause: Throwable? = null
    ) : NetworkException("TLS/SSL certificate error: $message", cause)

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
