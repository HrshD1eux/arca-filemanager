package com.Hrshd1eux.filemanager.core.network.manager

import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyDiagnostics
import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyEventType
import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyTriggerCategory
import com.Hrshd1eux.filemanager.core.network.model.NetworkConnectionState
import com.Hrshd1eux.filemanager.core.network.model.NetworkException
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.NetworkProvider
import com.Hrshd1eux.filemanager.core.network.registry.NetworkProviderRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages remote network session lifecycles, transfer jobs, and connection locks.
 *
 * KEY RESPONSIBILITIES:
 * 1. Clean Connect & Disconnect: Gracefully initialises sessions and tears down sockets/channels.
 * 2. Duplicate Connection Prevention: Mutex-guarded connection setup prevents racing duplicate handshakes.
 * 3. Transfer Safety & Cancellation: Tracks active transfers and cancels them safely on network interruption.
 * 4. Local Core Isolation: Remote connection drops or failures NEVER impair local file browsing.
 */
class NetworkConnectionManager(
    private val providerFactory: (NetworkServerConfig) -> NetworkProvider = { NetworkProviderRegistry.createProvider(it) },
    private val diagnostics: NetworkPrivacyDiagnostics = NetworkPrivacyDiagnostics.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val connectionLocks = ConcurrentHashMap<String, Mutex>()
    private val activeProviders = ConcurrentHashMap<String, NetworkProvider>()
    private val activeTransfers = ConcurrentHashMap<String, Job>()

    private fun getLock(connectionId: String): Mutex {
        return connectionLocks.computeIfAbsent(connectionId) { Mutex() }
    }

    /**
     * Connects to a remote server using the provided [config], preventing duplicate concurrent attempts.
     */
    suspend fun getOrConnectProvider(
        config: NetworkServerConfig,
        triggerCategory: NetworkPrivacyTriggerCategory = NetworkPrivacyTriggerCategory.USER_INITIATED
    ): Result<NetworkProvider> = withContext(ioDispatcher) {
        val lock = getLock(config.id)
        lock.withLock {
            val existing = activeProviders[config.id]
            if (existing != null && existing.connectionState.value is NetworkConnectionState.Authenticated) {
                return@withContext Result.success(existing)
            }

            val startTime = System.currentTimeMillis()
            diagnostics.recordEvent(
                connectionId = config.id,
                protocol = config.protocol.name,
                destination = "${config.host}:${config.effectivePort}",
                eventType = NetworkPrivacyEventType.CONNECT_ATTEMPT,
                triggerCategory = triggerCategory
            )

            val provider = providerFactory(config)
            val connectResult = provider.connect()

            val duration = System.currentTimeMillis() - startTime
            if (connectResult.isSuccess) {
                activeProviders[config.id] = provider
                diagnostics.recordEvent(
                    connectionId = config.id,
                    protocol = config.protocol.name,
                    destination = "${config.host}:${config.effectivePort}",
                    eventType = NetworkPrivacyEventType.CONNECT_SUCCESS,
                    triggerCategory = triggerCategory,
                    latencyMs = duration
                )
                Result.success(provider)
            } else {
                val error = connectResult.exceptionOrNull()
                val isSecurityAlert = error is NetworkException.SshHostKeyChangedException ||
                        error is NetworkException.TlsCertificateException

                diagnostics.recordEvent(
                    connectionId = config.id,
                    protocol = config.protocol.name,
                    destination = "${config.host}:${config.effectivePort}",
                    eventType = if (isSecurityAlert) NetworkPrivacyEventType.SECURITY_ALERT else NetworkPrivacyEventType.CONNECT_FAILURE,
                    triggerCategory = triggerCategory,
                    outcome = "FAILURE: ${error?.message ?: "Unknown error"}",
                    latencyMs = duration
                )
                Result.failure(error ?: NetworkException.NetworkConnectionException("Failed to connect to ${config.name}"))
            }
        }
    }

    /**
     * Gracefully disconnects a specific remote session and cancels associated transfers.
     */
    suspend fun disconnect(
        connectionId: String,
        reason: String = "User requested disconnect"
    ) = withContext(ioDispatcher) {
        val lock = getLock(connectionId)
        lock.withLock {
            // Cancel active transfers for this connection
            cancelTransfersForConnection(connectionId)

            val provider = activeProviders.remove(connectionId)
            if (provider != null) {
                provider.disconnect()
                diagnostics.recordEvent(
                    connectionId = connectionId,
                    protocol = provider.protocol.name,
                    destination = "${provider.config.host}:${provider.config.effectivePort}",
                    eventType = NetworkPrivacyEventType.DISCONNECT,
                    outcome = reason
                )
            }
        }
    }

    /**
     * Registers an ongoing transfer job.
     */
    fun registerTransfer(transferId: String, job: Job) {
        activeTransfers[transferId] = job
        job.invokeOnCompletion {
            activeTransfers.remove(transferId)
        }
    }

    /**
     * Safely cancels an ongoing transfer job.
     */
    fun cancelTransfer(transferId: String, reason: String = "Transfer cancelled") {
        activeTransfers.remove(transferId)?.cancel(CancellationException(reason))
    }

    /**
     * Cancels all transfers associated with a connection ID prefix.
     */
    private fun cancelTransfersForConnection(connectionId: String) {
        val toCancel = activeTransfers.filterKeys { it.startsWith(connectionId) }
        toCancel.forEach { (id, job) ->
            job.cancel(CancellationException("Connection closed"))
            activeTransfers.remove(id)
        }
    }

    /**
     * Handles network dropped / lost Wi-Fi event.
     * Cancels active network transfers and marks sessions disconnected without affecting local browsing.
     */
    suspend fun onNetworkLost() = withContext(ioDispatcher) {
        // Cancel all remote network transfers safely
        activeTransfers.values.forEach { it.cancel(CancellationException("Network connectivity lost")) }
        activeTransfers.clear()

        // Disconnect all remote sessions
        for ((connId, provider) in activeProviders) {
            runCatching { provider.disconnect() }
            diagnostics.recordEvent(
                connectionId = connId,
                protocol = provider.protocol.name,
                destination = "${provider.config.host}:${provider.config.effectivePort}",
                eventType = NetworkPrivacyEventType.DISCONNECT,
                triggerCategory = NetworkPrivacyTriggerCategory.APP_OPERATION,
                outcome = "Network interface lost (Wi-Fi dropped)"
            )
        }
        activeProviders.clear()
    }

    /**
     * Returns true if there is an active connected provider for [connectionId].
     */
    fun isConnected(connectionId: String): Boolean {
        return activeProviders[connectionId]?.connectionState?.value is NetworkConnectionState.Authenticated
    }

    /**
     * Returns the active provider for [connectionId], or null if not connected.
     */
    fun getActiveProvider(connectionId: String): NetworkProvider? {
        return activeProviders[connectionId]
    }

    /**
     * Disconnects all active remote sessions.
     */
    suspend fun disconnectAll() = withContext(ioDispatcher) {
        val keys = activeProviders.keys.toList()
        for (key in keys) {
            disconnect(key, "Shutdown / Disconnect All")
        }
        connectionLocks.clear()
    }
}
