package com.Hrshd1eux.filemanager

import android.app.Application
import com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageOperator
import com.Hrshd1eux.filemanager.core.operations.engine.RemoteStorageOperatorRegistry

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
    }
}
