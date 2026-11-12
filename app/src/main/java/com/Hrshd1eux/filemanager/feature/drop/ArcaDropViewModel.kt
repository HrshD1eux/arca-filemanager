package com.Hrshd1eux.filemanager.feature.drop

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.drop.model.ArcaDropState
import com.Hrshd1eux.filemanager.core.drop.server.ArcaDropManager
import com.Hrshd1eux.filemanager.core.drop.server.ArcaDropService
import com.Hrshd1eux.filemanager.core.network.util.LocalLanNetworkUtil
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ArcaDropViewModel @JvmOverloads constructor(
    private val manager: ArcaDropManager = ArcaDropManager.getInstance()
) : ViewModel() {

    val uiState: StateFlow<ArcaDropState> = manager.state

    fun startServer(context: Context) {
        ArcaDropService.start(context.applicationContext)
    }

    fun stopServer(context: Context) {
        ArcaDropService.stop(context.applicationContext)
    }

    fun setReceiveDirectory(path: String) {
        manager.setReceiveDirectory(path)
    }

    fun setSharedText(text: String?) {
        manager.setSharedText(text)
    }

    fun addStorageNodes(nodes: List<StorageNode>) {
        viewModelScope.launch(Dispatchers.IO) {
            manager.addStorageNodes(nodes)
        }
    }

    fun addPickedUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            manager.stageExternalUris(appContext, uris)
        }
    }

    fun removeSharedItem(id: String) {
        manager.removeSharedItem(id)
    }

    fun clearSharedQueue() {
        manager.clearSharedQueue()
    }

    fun refreshLanIp() {
        manager.onIpUpdated(LocalLanNetworkUtil.getLocalIpAddress())
    }
}
