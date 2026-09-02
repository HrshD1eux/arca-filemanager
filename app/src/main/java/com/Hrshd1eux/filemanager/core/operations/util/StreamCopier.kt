package com.Hrshd1eux.filemanager.core.operations.util

import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * High-performance, constant-memory stream copier with coroutine cancellation checks.
 */
object StreamCopier {

    const val BUFFER_SIZE = 64 * 1024 // 64 KB

    suspend fun copyStream(
        source: InputStream,
        destination: OutputStream,
        onBytesCopied: (bytesChunk: Int) -> Unit = {}
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytesRead = 0L
        var bytesRead: Int

        while (source.read(buffer).also { bytesRead = it } != -1) {
            coroutineContext.ensureActive()
            destination.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            onBytesCopied(bytesRead)
        }

        destination.flush()
        return totalBytesRead
    }
}
