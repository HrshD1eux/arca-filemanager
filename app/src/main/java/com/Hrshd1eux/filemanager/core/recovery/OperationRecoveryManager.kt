package com.Hrshd1eux.filemanager.core.recovery

import com.Hrshd1eux.filemanager.core.operations.util.StreamCopier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class InterruptedArtifact(
    val file: File,
    val originalFileName: String,
    val size: Long,
    val lastModified: Long,
    val reason: String
)

data class BatchCheckpoint(
    val operationId: String,
    val totalFiles: Int,
    val completedFiles: List<String>,
    val pendingFiles: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

class OperationRecoveryManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val checkpoints = mutableMapOf<String, BatchCheckpoint>()

    /**
     * Scans a directory for orphaned or interrupted partial files resulting from
     * aborted copies, interrupted extractions, or crashed transfers.
     */
    fun scanInterruptedArtifacts(
        directory: File,
        recursive: Boolean = false,
        minAgeMs: Long = 1000L // Artifacts older than 1s to avoid catching actively written files
    ): List<InterruptedArtifact> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val results = mutableListOf<InterruptedArtifact>()
        val now = System.currentTimeMillis()

        fun inspect(file: File) {
            val name = file.name
            val age = now - file.lastModified()
            if (age < minAgeMs) return

            when {
                name.contains(".arca_backup_tmp") -> {
                    val original = name.removePrefix(".").removeSuffix(".arca_backup_tmp")
                    results.add(
                        InterruptedArtifact(
                            file = file,
                            originalFileName = original,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            reason = "Interrupted backup temporary file"
                        )
                    )
                }
                name.endsWith(".part", ignoreCase = true) -> {
                    results.add(
                        InterruptedArtifact(
                            file = file,
                            originalFileName = name.removeSuffix(".part"),
                            size = file.length(),
                            lastModified = file.lastModified(),
                            reason = "Incomplete archive extraction or download"
                        )
                    )
                }
                name.endsWith(".tmp", ignoreCase = true) || name.startsWith(".tmp") -> {
                    results.add(
                        InterruptedArtifact(
                            file = file,
                            originalFileName = name.removeSuffix(".tmp").removePrefix("."),
                            size = file.length(),
                            lastModified = file.lastModified(),
                            reason = "Orphaned temporary copy"
                        )
                    )
                }
                name.endsWith(".crdownload", ignoreCase = true) -> {
                    results.add(
                        InterruptedArtifact(
                            file = file,
                            originalFileName = name.removeSuffix(".crdownload"),
                            size = file.length(),
                            lastModified = file.lastModified(),
                            reason = "Interrupted transfer file"
                        )
                    )
                }
            }
        }

        if (recursive) {
            directory.walkTopDown().forEach { f ->
                if (f.isFile) inspect(f)
            }
        } else {
            directory.listFiles()?.forEach { f ->
                if (f.isFile) inspect(f)
            }
        }

        return results
    }

    /**
     * Cleans up a single interrupted artifact safely.
     */
    fun cleanArtifact(artifact: InterruptedArtifact): Boolean {
        return runCatching {
            if (artifact.file.exists()) artifact.file.delete() else true
        }.getOrDefault(false)
    }

    /**
     * Cleans up a collection of interrupted artifacts.
     * Returns the count of successfully deleted artifacts.
     */
    fun cleanAll(artifacts: List<InterruptedArtifact>): Int {
        var deletedCount = 0
        for (artifact in artifacts) {
            if (cleanArtifact(artifact)) {
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Checks if a partially copied destination file can be resumed safely from the source.
     */
    fun canResume(source: File, partialDest: File): Boolean {
        if (!source.exists() || !source.isFile) return false
        if (!partialDest.exists() || !partialDest.isFile) return false
        val srcLen = source.length()
        val dstLen = partialDest.length()
        return dstLen in 1 until srcLen
    }

    /**
     * Safely resumes an interrupted file copy, appending only the remaining bytes
     * from the source file to the partial destination file.
     */
    suspend fun resumeCopy(
        source: File,
        partialDest: File,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Boolean> = withContext(ioDispatcher) {
        runCatching {
            if (!canResume(source, partialDest)) {
                throw IllegalArgumentException("Cannot resume: Destination size is invalid or source missing")
            }

            val sourceLength = source.length()
            val existingBytes = partialDest.length()
            var currentBytes = existingBytes

            FileInputStream(source).use { inStream ->
                val skipped = inStream.skip(existingBytes)
                if (skipped != existingBytes) {
                    throw IOException("Failed seeking to resume offset $existingBytes in source file (skipped $skipped)")
                }

                FileOutputStream(partialDest, true).use { outStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        coroutineContext.ensureActive()
                        outStream.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead
                        onProgress?.invoke(currentBytes, sourceLength)
                    }
                    outStream.flush()
                }
            }

            // Sync modification timestamp
            partialDest.setLastModified(source.lastModified())
            true
        }
    }

    /**
     * Checkpoint journal management for multi-file batch operations
     */
    fun saveCheckpoint(
        operationId: String,
        totalFiles: Int,
        completedFiles: List<String>,
        pendingFiles: List<String>
    ) {
        checkpoints[operationId] = BatchCheckpoint(
            operationId = operationId,
            totalFiles = totalFiles,
            completedFiles = completedFiles,
            pendingFiles = pendingFiles
        )
    }

    fun getCheckpoint(operationId: String): BatchCheckpoint? = checkpoints[operationId]

    fun clearCheckpoint(operationId: String) {
        checkpoints.remove(operationId)
    }
}
