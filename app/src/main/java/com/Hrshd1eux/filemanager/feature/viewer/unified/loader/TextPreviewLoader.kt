package com.Hrshd1eux.filemanager.feature.viewer.unified.loader

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Memory-safe loader for reading text, code, markdown, logs, and structured data files.
 */
object TextPreviewLoader {

    const val MAX_PREVIEW_LINES = 5000
    const val MAX_PREVIEW_BYTES = 1024 * 1024L // 1 MB

    data class TextResult(
        val lines: List<String>,
        val isTruncated: Boolean,
        val totalLinesCount: Int,
        val totalSizeBytes: Long
    )

    suspend fun load(
        file: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Result<TextResult> = withContext(dispatcher) {
        runCatching {
            if (!file.exists() || !file.canRead()) {
                throw IllegalArgumentException("File does not exist or is not readable: ${file.path}")
            }

            val totalBytes = file.length()
            val lines = mutableListOf<String>()
            var isTruncated = false
            var lineCount = 0

            // Try UTF-8 first
            FileInputStream(file).use { fis ->
                val reader = BufferedReader(InputStreamReader(fis, StandardCharsets.UTF_8))
                var currentBytesRead = 0L

                var line = reader.readLine()
                while (line != null) {
                    lineCount++
                    currentBytesRead += line.toByteArray(StandardCharsets.UTF_8).size + 1

                    if (lines.size < MAX_PREVIEW_LINES && currentBytesRead <= MAX_PREVIEW_BYTES) {
                        lines.add(line)
                    } else {
                        isTruncated = true
                        // Fast count remaining lines
                        while (reader.readLine() != null) {
                            lineCount++
                        }
                        break
                    }
                    line = reader.readLine()
                }
            }

            TextResult(
                lines = lines,
                isTruncated = isTruncated,
                totalLinesCount = lineCount,
                totalSizeBytes = totalBytes
            )
        }
    }
}
