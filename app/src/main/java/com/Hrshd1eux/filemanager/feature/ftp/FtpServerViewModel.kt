package com.Hrshd1eux.filemanager.feature.ftp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.ftp.server.FtpNetworkUtil
import com.Hrshd1eux.filemanager.core.ftp.server.FtpServerConfig
import com.Hrshd1eux.filemanager.core.ftp.server.FtpServerManager
import com.Hrshd1eux.filemanager.core.ftp.server.FtpServerPreferences
import com.Hrshd1eux.filemanager.core.ftp.server.FtpServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class FtpServerViewModel(application: Application) : AndroidViewModel(application) {

    private val serverManager = FtpServerManager.getInstance()
    private val _draftConfig = MutableStateFlow(FtpServerPreferences.loadConfig(application))

    val uiState: StateFlow<FtpServerState> = combine(serverManager.state, _draftConfig) { serverState, draft ->
        if (serverState.isRunning) {
            serverState
        } else {
            serverState.copy(config = draft, port = draft.port)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FtpServerState(
            config = FtpServerPreferences.loadConfig(application),
            ipAddress = FtpNetworkUtil.getLocalIpAddress()
        )
    )

    init {
        serverManager.refreshConfig(application)
    }

    fun updatePort(port: Int) {
        val safePort = port.coerceIn(1024, 65535)
        _draftConfig.value = _draftConfig.value.copy(port = safePort)
    }

    fun updateUsername(user: String) {
        _draftConfig.value = _draftConfig.value.copy(username = user.trim())
    }

    fun updatePassword(pass: String) {
        _draftConfig.value = _draftConfig.value.copy(password = pass)
    }

    fun updateAnonymous(allowed: Boolean) {
        _draftConfig.value = _draftConfig.value.copy(isAnonymousAllowed = allowed)
    }

    fun updateRootDirectory(path: String) {
        _draftConfig.value = _draftConfig.value.copy(rootDirectoryPath = path)
    }

    fun updateReadOnly(readOnly: Boolean) {
        _draftConfig.value = _draftConfig.value.copy(isReadOnly = readOnly)
    }

    fun updateShareApk(shareApk: Boolean) {
        _draftConfig.value = _draftConfig.value.copy(shareApkEnabled = shareApk)
    }

    fun startServer(context: Context) {
        val config = _draftConfig.value
        serverManager.startServer(context, config)
    }

    fun stopServer(context: Context) {
        serverManager.stopServer(context)
    }

    fun refreshNetwork(context: Context) {
        serverManager.refreshConfig(context)
    }
}
