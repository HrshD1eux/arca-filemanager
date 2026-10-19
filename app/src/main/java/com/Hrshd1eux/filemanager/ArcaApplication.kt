package com.Hrshd1eux.filemanager

import android.app.Application
import com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageOperator
import com.Hrshd1eux.filemanager.core.operations.engine.RemoteStorageOperatorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ARCA Application class.
 * Foundation entry point for application-level lifecycle and future dependency injection / setup.
 */
class ArcaApplication : Application() {

    companion object {
        var instance: ArcaApplication? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Register remote storage bridge into offline operations core
        RemoteStorageOperatorRegistry.register(NetworkStorageOperator())

        // Prune any lingering temporary shared APK copies from previous sessions
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.Hrshd1eux.filemanager.core.storage.apk.ApkShareManager.getInstance().cleanupOldCopies(cacheDir)
        }
    }
}
