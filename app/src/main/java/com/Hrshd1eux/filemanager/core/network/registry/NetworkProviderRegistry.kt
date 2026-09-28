package com.Hrshd1eux.filemanager.core.network.registry

import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import com.Hrshd1eux.filemanager.core.network.provider.FtpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.FtpsNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.NetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.SftpNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.SmbNetworkProvider
import com.Hrshd1eux.filemanager.core.network.provider.WebDavNetworkProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory and lifecycle registry for remote network providers.
 * Manages active provider instances, session caching, and graceful shutdown.
 */
object NetworkProviderRegistry {

    private val activeProviders = ConcurrentHashMap<String, NetworkProvider>()

    /**
     * Factory method creating the appropriate concrete NetworkProvider for a given server configuration.
     */
    fun createProvider(config: NetworkServerConfig): NetworkProvider {
        config.validate()
        return when (config.protocol) {
            NetworkProtocol.FTP -> FtpNetworkProvider(config)
            NetworkProtocol.FTPS -> FtpsNetworkProvider(config)
            NetworkProtocol.SFTP -> SftpNetworkProvider(config)
            NetworkProtocol.SMB -> SmbNetworkProvider(config)
            NetworkProtocol.WEBDAV -> WebDavNetworkProvider(config)
        }
    }

    /**
     * Registers an active provider in the session cache.
     */
    fun register(provider: NetworkProvider) {
        activeProviders[provider.config.id] = provider
    }

    /**
     * Retrieves an active provider by its configuration ID.
     */
    fun get(configId: String): NetworkProvider? {
        return activeProviders[configId]
    }

    /**
     * Returns all active network providers.
     */
    fun getAll(): List<NetworkProvider> {
        return activeProviders.values.toList()
    }

    /**
     * Unregisters and disconnects a provider.
     */
    suspend fun unregister(configId: String) {
        val provider = activeProviders.remove(configId)
        provider?.disconnect()
    }

    /**
     * Disconnects and purges all active remote sessions.
     */
    suspend fun disconnectAll() {
        val providers = activeProviders.values.toList()
        activeProviders.clear()
        providers.forEach { it.disconnect() }
    }
}
