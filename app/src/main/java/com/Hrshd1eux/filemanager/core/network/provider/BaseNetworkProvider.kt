package com.Hrshd1eux.filemanager.core.network.provider

import com.Hrshd1eux.filemanager.core.network.model.NetworkCapabilities
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base implementation of NetworkProvider managing connection lifecycle state,
 * path sanitization, and session guard assertions.
 */
abstract class BaseNetworkProvider(
    final override val config: NetworkServerConfig,
    final override val protocol: NetworkProtocol,
    final override val capabilities: NetworkCapabilities
) : NetworkProvider {

    protected val _connectionState = MutableStateFlow<NetworkConnectionState>(NetworkConnectionState.Disconnected)
    override val connectionState: StateFlow<NetworkConnectionState> = _connectionState.asStateFlow()

    init {
        config.validate()
        require(config.protocol == protocol) {
            "Configuration protocol '${config.protocol}' does not match provider protocol '$protocol'."
        }
    }

    /**
     * Asserts that the session is authenticated and ready to service file requests.
     * @throws NetworkException if session is disconnected or in error state.
     */
    protected fun checkReady() {
        val state = _connectionState.value
        if (state !is NetworkConnectionState.Authenticated) {
            throw NetworkException.NetworkConnectionException(
                "Cannot perform remote operations on ${protocol.name}: session is not authenticated (current state: $state)"
            )
        }
    }

    /**
     * Normalizes remote paths to canonical POSIX-style paths (e.g. "/documents/report.pdf").
     */
    protected fun normalizePath(remotePath: String): String {
        val clean = remotePath.trim().replace('\\', '/')
        val formatted = if (clean.startsWith("/")) clean else "/$clean"
        return if (formatted.length > 1 && formatted.endsWith("/")) formatted.dropLast(1) else formatted
    }
}
