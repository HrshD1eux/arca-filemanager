package com.Hrshd1eux.filemanager.core.drop.model

import android.net.Uri
import java.io.File

/**
 * Represents any file, folder-expanded entry, or staged URI shared over ARCA Drop.
 */
data class DropTransferItem(
    val id: String,
    val name: String,
    val relativePath: String,
    val mimeType: String,
    val size: Long,
    val file: File? = null,
    val uri: Uri? = null,
    val staged: Boolean = false
)

data class DropReceivedFile(
    val name: String,
    val savedPath: String,
    val sizeBytes: Long,
    val timestampMs: Long = System.currentTimeMillis()
)

data class DropReceivedText(
    val id: String,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class DropLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false
)

data class ArcaDropState(
    val isRunning: Boolean = false,
    val ipAddress: String? = null,
    val port: Int = 0,
    val pin: String = "",
    val receiveDirectoryPath: String = "",
    val sharedItems: List<DropTransferItem> = emptyList(),
    val sharedText: String? = null,
    val receivedFiles: List<DropReceivedFile> = emptyList(),
    val receivedTexts: List<DropReceivedText> = emptyList(),
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val activeConnections: Int = 0,
    val logs: List<DropLogEntry> = emptyList()
) {
    val baseUrl: String?
        get() = if (isRunning && ipAddress != null && port > 0) {
            "http://$ipAddress:$port"
        } else {
            null
        }

    val qrUrl: String?
        get() = baseUrl?.let { url ->
            if (pin.isNotEmpty()) "$url/?pin=$pin" else "$url/"
        }
}
