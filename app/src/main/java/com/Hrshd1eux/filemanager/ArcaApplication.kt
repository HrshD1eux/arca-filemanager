package com.Hrshd1eux.filemanager

import android.app.Application
import com.Hrshd1eux.filemanager.core.network.bridge.NetworkStorageOperator
import com.Hrshd1eux.filemanager.core.operations.engine.RemoteStorageOperatorRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.Hrshd1eux.filemanager.core.trash.worker.TrashAutoPurgeWorker
import java.util.concurrent.TimeUnit
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

        // Schedule 30-day trash auto-purge via WorkManager
        runCatching {
            val purgeRequest = PeriodicWorkRequestBuilder<TrashAutoPurgeWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                TrashAutoPurgeWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                purgeRequest
            )
        }
    }
}
