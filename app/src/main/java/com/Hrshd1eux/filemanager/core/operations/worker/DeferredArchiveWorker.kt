package com.Hrshd1eux.filemanager.core.operations.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DeferredArchiveWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val zipEngine = ZipEngine(Dispatchers.IO)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourcePaths = inputData.getStringArray(KEY_SOURCE_PATHS) ?: return@withContext Result.failure()
        val destPath = inputData.getString(KEY_DEST_PATH) ?: return@withContext Result.failure()
        val level = inputData.getInt(KEY_COMPRESSION_LEVEL, java.util.zip.Deflater.DEFAULT_COMPRESSION)

        val sources = sourcePaths.map { File(it) }.filter { it.exists() }
        if (sources.isEmpty()) return@withContext Result.failure()

        val destFile = File(destPath)
        val result = zipEngine.createZip(
            sources = sources,
            destinationZip = destFile,
            compressionLevel = level
        )

        if (result.isSuccess) {
            Result.success(workDataOf(KEY_OUTPUT_SIZE to (result.getOrNull() ?: 0L)))
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SOURCE_PATHS = "key_source_paths"
        const val KEY_DEST_PATH = "key_dest_path"
        const val KEY_COMPRESSION_LEVEL = "key_compression_level"
        const val KEY_OUTPUT_SIZE = "key_output_size"

        fun enqueueArchiveWork(
            context: Context,
            sourcePaths: List<String>,
            destinationPath: String,
            compressionLevel: Int = java.util.zip.Deflater.DEFAULT_COMPRESSION
        ) {
            val inputData = Data.Builder()
                .putStringArray(KEY_SOURCE_PATHS, sourcePaths.toTypedArray())
                .putString(KEY_DEST_PATH, destinationPath)
                .putInt(KEY_COMPRESSION_LEVEL, compressionLevel)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DeferredArchiveWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
