package com.Hrshd1eux.filemanager.core.network.repository

import android.content.Context
import com.Hrshd1eux.filemanager.core.network.credentials.SecureCredentialsStore
import com.Hrshd1eux.filemanager.core.network.model.NetworkAuthType
import com.Hrshd1eux.filemanager.core.network.model.NetworkProtocol
import com.Hrshd1eux.filemanager.core.network.model.NetworkServerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class NetworkConnectionDto(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int = 0,
    val authType: String,
    val username: String? = null,
    val basePath: String = "/",
    val isPassiveMode: Boolean = true,
    val isImplicitTls: Boolean = false,
    val allowSelfSignedCert: Boolean = false
) {
    fun toModel(): NetworkServerConfig {
        return NetworkServerConfig(
            id = id,
            name = name,
            protocol = NetworkProtocol.valueOf(protocol),
            host = host,
            port = port,
            authType = NetworkAuthType.valueOf(authType),
            username = username,
            basePath = basePath,
            isPassiveMode = isPassiveMode,
            isImplicitTls = isImplicitTls,
            allowSelfSignedCert = allowSelfSignedCert
        )
    }

    companion object {
        fun fromModel(model: NetworkServerConfig): NetworkConnectionDto {
            return NetworkConnectionDto(
                id = model.id,
                name = model.name,
                protocol = model.protocol.name,
                host = model.host,
                port = model.effectivePort,
                authType = model.authType.name,
                username = model.username,
                basePath = model.basePath,
                isPassiveMode = model.isPassiveMode,
                isImplicitTls = model.isImplicitTls,
                allowSelfSignedCert = model.allowSelfSignedCert
            )
        }
    }
}

interface NetworkConnectionRepository {
    val connections: StateFlow<List<NetworkServerConfig>>
    suspend fun getConnections(): List<NetworkServerConfig>
    suspend fun getConnection(id: String): NetworkServerConfig?
    suspend fun saveConnection(config: NetworkServerConfig)
    suspend fun deleteConnection(id: String)
    suspend fun populateWithSecrets(config: NetworkServerConfig): NetworkServerConfig
}

/**
 * Repository implementation managing saved network connections.
 * Non-sensitive metadata is persisted in SharedPreferences (or in-memory cache for tests),
 * while passwords and private keys are stored encrypted at rest via SecureCredentialsStore.
 */
class NetworkConnectionRepositoryImpl(
    context: Context? = null,
    private val credentialsStore: SecureCredentialsStore = SecureCredentialsStore(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    prefsName: String = PREFS_METADATA
) : NetworkConnectionRepository {

    private val prefs = context?.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private var inMemoryJson: String? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _connections = MutableStateFlow<List<NetworkServerConfig>>(emptyList())
    override val connections: StateFlow<List<NetworkServerConfig>> = _connections.asStateFlow()

    init {
        loadConnectionsFromPrefs()
    }

    private fun loadConnectionsFromPrefs() {
        val jsonString = prefs?.getString(KEY_CONNECTIONS_JSON, null) ?: inMemoryJson
        val loadedList = mutableListOf<NetworkServerConfig>()

        if (!jsonString.isNullOrBlank()) {
            try {
                val dtos = json.decodeFromString<List<NetworkConnectionDto>>(jsonString)
                loadedList.addAll(dtos.map { it.toModel() })
            } catch (e: Exception) {
                // If corrupted, fallback to empty
            }
        }

        // Pre-seed sample SFTP connections if storage is completely empty
        if (loadedList.isEmpty()) {
            val sampleNas = NetworkServerConfig(
                id = "sample-nas-001",
                name = "NAS",
                protocol = NetworkProtocol.SFTP,
                host = "192.168.1.20",
                port = 22,
                authType = NetworkAuthType.PASSWORD,
                username = "admin",
                basePath = "/"
            )
            val sampleServer = NetworkServerConfig(
                id = "sample-server-002",
                name = "Server",
                protocol = NetworkProtocol.SFTP,
                host = "example.local",
                port = 22,
                authType = NetworkAuthType.PASSWORD,
                username = "user",
                basePath = "/"
            )
            loadedList.add(sampleNas)
            loadedList.add(sampleServer)
            saveMetadataList(loadedList)
        }

        _connections.value = loadedList
    }

    override suspend fun getConnections(): List<NetworkServerConfig> = withContext(ioDispatcher) {
        _connections.value
    }

    override suspend fun getConnection(id: String): NetworkServerConfig? = withContext(ioDispatcher) {
        _connections.value.find { it.id == id }
    }

    override suspend fun saveConnection(config: NetworkServerConfig) = withContext(ioDispatcher) {
        config.validate()

        // 1. Encrypt and store sensitive credentials if present
        config.password?.let {
            credentialsStore.saveSecret(config.id, SecureCredentialsStore.SECRET_PASSWORD, it)
        }
        config.privateKey?.let {
            credentialsStore.saveSecret(config.id, SecureCredentialsStore.SECRET_PRIVATE_KEY, it)
        }
        config.passphrase?.let {
            credentialsStore.saveSecret(config.id, SecureCredentialsStore.SECRET_PASSPHRASE, it)
        }

        // 2. Strip secrets before saving metadata to disk
        val sanitizedConfig = config.copy(
            password = null,
            privateKey = null,
            passphrase = null
        )

        val currentList = _connections.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == config.id }
        if (index != -1) {
            currentList[index] = sanitizedConfig
        } else {
            currentList.add(sanitizedConfig)
        }

        saveMetadataList(currentList)
        _connections.value = currentList
    }

    override suspend fun deleteConnection(id: String) = withContext(ioDispatcher) {
        credentialsStore.purgeConnectionSecrets(id)
        val currentList = _connections.value.filter { it.id != id }
        saveMetadataList(currentList)
        _connections.value = currentList
    }

    override suspend fun populateWithSecrets(config: NetworkServerConfig): NetworkServerConfig = withContext(ioDispatcher) {
        val password = credentialsStore.getSecret(config.id, SecureCredentialsStore.SECRET_PASSWORD)
        val privateKey = credentialsStore.getSecret(config.id, SecureCredentialsStore.SECRET_PRIVATE_KEY)
        val passphrase = credentialsStore.getSecret(config.id, SecureCredentialsStore.SECRET_PASSPHRASE)

        config.copy(
            password = password,
            privateKey = privateKey,
            passphrase = passphrase
        )
    }

    private fun saveMetadataList(list: List<NetworkServerConfig>) {
        val dtos = list.map { NetworkConnectionDto.fromModel(it) }
        val serialized = json.encodeToString(dtos)
        prefs?.edit()?.putString(KEY_CONNECTIONS_JSON, serialized)?.apply()
        inMemoryJson = serialized
    }

    companion object {
        private const val PREFS_METADATA = "arca_network_connections_metadata"
        private const val KEY_CONNECTIONS_JSON = "saved_connections_json"
    }
}
