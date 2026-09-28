package com.Hrshd1eux.filemanager.core.network.model

/**
 * Lifecycle connection state for a remote network provider session.
 */
sealed class NetworkConnectionState {
    /** Session is completely closed and inactive. */
    data object Disconnected : NetworkConnectionState()

    /** Establishing initial TCP transport connection. */
    data object Connecting : NetworkConnectionState()

    /** TCP transport connection established; handshake / TLS pending. */
    data object Connected : NetworkConnectionState()

    /** Authenticating credentials with the remote server. */
    data object Authenticating : NetworkConnectionState()

    /** Successfully authenticated; session ready for remote file operations. */
    data object Authenticated : NetworkConnectionState()

    /** Gracefully closing the remote session. */
    data object Disconnecting : NetworkConnectionState()

    /** An error occurred during connection or communication. */
    data class Error(val error: NetworkException) : NetworkConnectionState()

    val isReady: Boolean
        get() = this is Authenticated

    val isConnected: Boolean
        get() = this is Connected || this is Authenticating || this is Authenticated
}
