package com.Hrshd1eux.filemanager.core.ftp.server

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Observable UI state of the ARCA FTP Server.
 */
data class FtpServerState(
    val isRunning: Boolean = false,
    val config: FtpServerConfig = FtpServerConfig(),
    val ipAddress: String? = null,
    val port: Int = FtpServerConfig.DEFAULT_PORT,
    val connectedClientsCount: Int = 0,
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val activityLogs: List<FtpActivityLog> = emptyList(),
    val errorMessage: String? = null
) {
    val ftpUrl: String
        get() = FtpNetworkUtil.formatFtpUrl(ipAddress, port)

    val isNetworkAvailable: Boolean
        get() = !ipAddress.isNullOrBlank()
}

/**
 * Singleton coordinator between UI ViewModels, ArcaFtpServer, and FtpServerService.
 */
class FtpServerManager private constructor() {

    private val _state = MutableStateFlow(FtpServerState())
    val state: StateFlow<FtpServerState> = _state.asStateFlow()

    @Volatile
    private var activeServer: ArcaFtpServer? = null

    companion object {
        private const val MAX_LOGS = 50

        @Volatile
        private var instance: FtpServerManager? = null

        fun getInstance(): FtpServerManager {
            return instance ?: synchronized(this) {
                instance ?: FtpServerManager().also { instance = it }
            }
        }
    }

    /**
     * Initializes state with saved preferences and latest local IP address.
     */
    fun refreshConfig(context: Context) {
        val savedConfig = FtpServerPreferences.loadConfig(context)
        val ip = FtpNetworkUtil.getLocalIpAddress()
        _state.update { current ->
            current.copy(
                config = savedConfig,
                ipAddress = ip,
                port = savedConfig.port
            )
        }
    }

    /**
     * Starts the FTP Server via Android Foreground Service.
     */
    fun startServer(context: Context, config: FtpServerConfig) {
        FtpServerPreferences.saveConfig(context, config)
        val ip = FtpNetworkUtil.getLocalIpAddress()
        _state.update { it.copy(config = config, ipAddress = ip, port = config.port, errorMessage = null) }

        val intent = Intent(context, FtpServerService::class.java).apply {
            action = FtpServerService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Stops the FTP Server and shuts down the Foreground Service.
     */
    fun stopServer(context: Context) {
        val intent = Intent(context, FtpServerService::class.java).apply {
            action = FtpServerService.ACTION_STOP
        }
        context.startService(intent)
    }

    // --- Internal methods called by FtpServerService ---

    internal fun attachServer(server: ArcaFtpServer) {
        activeServer = server
    }

    internal fun detachServer() {
        activeServer = null
        _state.update {
            it.copy(
                isRunning = false,
                connectedClientsCount = 0
            )
        }
    }

    internal fun onServerRunningStateChanged(running: Boolean, clients: Int, sent: Long, received: Long) {
        val ip = FtpNetworkUtil.getLocalIpAddress()
        _state.update {
            it.copy(
                isRunning = running,
                ipAddress = ip,
                connectedClientsCount = clients,
                bytesSent = sent,
                bytesReceived = received
            )
        }
    }

    internal fun addLog(log: FtpActivityLog) {
        _state.update { current ->
            val updated = (listOf(log) + current.activityLogs).take(MAX_LOGS)
            current.copy(activityLogs = updated)
        }
    }

    internal fun setErrorMessage(error: String?) {
        _state.update { it.copy(errorMessage = error) }
    }
}
