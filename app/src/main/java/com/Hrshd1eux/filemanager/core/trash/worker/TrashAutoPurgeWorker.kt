package com.Hrshd1eux.filemanager.core.trash.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.Hrshd1eux.filemanager.core.trash.manager.TrashManager

/**
 * Background WorkManager worker responsible for auto-purging trashed files
 * exceeding the 30-day retention window using cryptographic file shredding.
 */
class TrashAutoPurgeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val trashManager = TrashManager.getInstance(applicationContext)
            trashManager.purgeExpired(retentionDays = 30)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "arca_trash_auto_purge"
    }
}
