package com.Hrshd1eux.filemanager.core.drop.server

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import com.Hrshd1eux.filemanager.core.drop.model.DropTransferItem
import com.Hrshd1eux.filemanager.core.network.util.LocalLanNetworkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zero-cloud embedded HTTP ServerSocket engine for ARCA Drop.
 * Streams files, folder ZIP trees, and browser uploads in bounded 64 KB buffers with
 * DNS-rebinding host validation and per-session 6-digit PIN authentication.
 */
class ArcaDropServer(
    private val preferredPort: Int = DEFAULT_PORT,
    private val idleTimeoutMs: Long = IDLE_TIMEOUT_MS,
    private val pinOverride: String? = null,
    private val context: Context? = null,
    private val contentResolver: ContentResolver? = context?.contentResolver,
    private val receiveDirectoryProvider: () -> File,
    private val itemsProvider: () -> List<DropTransferItem>,
    private val sharedTextProvider: () -> String?,
    private val ipAddressProvider: () -> String? = { LocalLanNetworkUtil.getLocalIpAddress() },
    private val onIpChanged: (String?) -> Unit = {},
    private val onFileReceived: (name: String, savedPath: String, sizeBytes: Long) -> Unit = { _, _, _ -> },
    private val onTextReceived: (text: String) -> Unit = {},
    private val onTransferProgress: (sentDelta: Long, receivedDelta: Long) -> Unit = { _, _ -> },
    private val onActiveConnectionsChanged: (Int) -> Unit = {},
    private val onLog: (message: String, isError: Boolean) -> Unit = { _, _ -> },
    private val onIdleTimeout: () -> Unit = {}
) {
    companion object {
        const val DEFAULT_PORT = 8765
        const val IDLE_TIMEOUT_MS = 15 * 60 * 1000L
        private const val BUFFER_SIZE = 65_536
        private const val SOCKET_TIMEOUT_MS = 60_000
        private const val MAX_HEADER_LINE_BYTES = 16_384
        private const val MAX_TEXT_BODY_BYTES = 262_144L
        private const val COOKIE_NAME = "arca_drop_pin"

        fun generatePin(): String {
            val random = SecureRandom()
            val num = random.nextInt(1_000_000)
            return num.toString().padStart(6, '0')
        }

        fun sanitizeUploadFilename(rawName: String?): String {
            val trimmed = rawName?.trim()?.replace('\\', '/') ?: ""
            val leaf = trimmed.substringAfterLast('/')
                .replace("..", "")
                .replace(Regex("[\\r\\n\\u0000/:*?\"<>|]"), "_")
                .trim(' ', '.')
            return leaf.ifEmpty { "upload_${System.currentTimeMillis()}" }
        }

        fun sanitizeZipEntryPath(rawPath: String, fallbackName: String): String {
            val parts = rawPath.replace('\\', '/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "." && it != ".." }
            val joined = parts.joinToString("/")
            return joined.ifEmpty { sanitizeUploadFilename(fallbackName) }
        }
    }

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var effectivePort: Int = 0
        private set

    val sessionPin: String = pinOverride ?: generatePin()

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workerPool: ExecutorService? = null
    private var monitorScope: CoroutineScope? = null
    private val activeConnections = AtomicInteger(0)
    private val lastActivityMs = AtomicLong(System.currentTimeMillis())

    @Synchronized
    fun start() {
        if (isRunning) return

        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(preferredPort))
            }
        } catch (_: IOException) {
            ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(0))
            }
        }

        serverSocket = socket
        effectivePort = socket.localPort
        isRunning = true
        lastActivityMs.set(System.currentTimeMillis())
        workerPool = Executors.newCachedThreadPool()

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        monitorScope = scope
        startMonitorLoop(scope)

        val acceptLoop = Thread({
            onLog("ARCA Drop HTTP server listening on port $effectivePort", false)
            while (isRunning && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    lastActivityMs.set(System.currentTimeMillis())
                    workerPool?.execute { handleClient(client) }
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    if (isRunning) {
                        onLog("Accept error: ${e.message ?: "Socket error"}", true)
                    }
                }
            }
        }, "ArcaDropAcceptThread")
        acceptLoop.isDaemon = true
        acceptLoop.start()
        acceptThread = acceptLoop
    }

    @Synchronized
    fun stop() {
        if (!isRunning && serverSocket == null) return
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        monitorScope?.cancel()
        monitorScope = null
        workerPool?.shutdownNow()
        workerPool = null
        acceptThread?.interrupt()
        acceptThread = null
        effectivePort = 0
        activeConnections.set(0)
        onActiveConnectionsChanged(0)
        onLog("ARCA Drop server stopped", false)
    }

    private fun startMonitorLoop(scope: CoroutineScope) {
        scope.launch {
            var lastKnownIp = ipAddressProvider()
            onIpChanged(lastKnownIp)
            while (isActive && isRunning) {
                delay(2_000L)
                if (!isRunning) break

                val currentIp = ipAddressProvider()
                if (currentIp != lastKnownIp) {
                    lastKnownIp = currentIp
                    onIpChanged(currentIp)
                    onLog(
                        if (currentIp != null) "Network interface updated: $currentIp"
                        else "No local Wi-Fi or Hotspot IP detected",
                        false
                    )
                }

                val idleFor = System.currentTimeMillis() - lastActivityMs.get()
                if (idleTimeoutMs > 0L && idleFor >= idleTimeoutMs && activeConnections.get() == 0) {
                    onLog("Auto-stopped after 15 minutes of inactivity", false)
                    stop()
                    onIdleTimeout()
                    break
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val count = activeConnections.incrementAndGet()
        onActiveConnectionsChanged(count)
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val input = BufferedInputStream(socket.getInputStream(), BUFFER_SIZE)
            val output = BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE)

            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                sendSimpleResponse(output, 400, "Bad Request", "Malformed HTTP request")
                return
            }

            val method = parts[0].uppercase()
            val rawTarget = parts[1]
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            lastActivityMs.set(System.currentTimeMillis())

            // DNS Rebinding Guard
            val hostHeader = headers["host"]
            if (!isAllowedHostHeader(hostHeader)) {
                sendSimpleResponse(output, 403, "Forbidden", "Host not permitted")
                return
            }

            val qIdx = rawTarget.indexOf('?')
            val rawPath = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
            val path = if (rawPath.isEmpty()) "/" else rawPath
            val queryParams = if (qIdx >= 0) parseQueryParams(rawTarget.substring(qIdx + 1)) else emptyMap()

            // CORS preflight handling without wildcard origin
            if (method == "OPTIONS") {
                handleCorsPreflight(headers["origin"], output)
                return
            }

            // Open favicon & logo routes so browser tab and login screen always display ARCA's original logo
            if (method == "GET" && (path == "/arca-logo.png" || path == "/favicon.png" || path == "/favicon.ico" || path == "/favicon.svg")) {
                val pngBytes = ArcaDropHtmlPortal.ARCA_LOGO_PNG_BYTES
                if (path != "/favicon.svg" && pngBytes != null) {
                    sendBytes(
                        output = output,
                        statusCode = 200,
                        statusText = "OK",
                        contentType = "image/png",
                        body = pngBytes,
                        extraHeaders = listOf("Cache-Control: public, max-age=3600")
                    )
                } else {
                    sendBytes(
                        output = output,
                        statusCode = 200,
                        statusText = "OK",
                        contentType = "image/svg+xml; charset=utf-8",
                        body = ArcaDropHtmlPortal.ARCA_FAVICON_BYTES,
                        extraHeaders = listOf("Cache-Control: public, max-age=3600")
                    )
                }
                return
            }

            val suppliedPin = extractPin(queryParams, headers)
            val isAuthenticated = suppliedPin != null && constantTimeEquals(suppliedPin, sessionPin)

            if (!isAuthenticated) {
                if (method == "GET" && (path == "/" || path == "/index.html")) {
                    val wrongPinAttempted = queryParams.containsKey("pin")
                    val html = ArcaDropHtmlPortal.renderPinGateHtml(
                        errorMessage = if (wrongPinAttempted) "Incorrect PIN. Check the 6-digit code in ARCA." else null
                    )
                    sendBytes(
                        output = output,
                        statusCode = if (wrongPinAttempted) 401 else 200,
                        statusText = if (wrongPinAttempted) "Unauthorized" else "OK",
                        contentType = "text/html; charset=utf-8",
                        body = html.toByteArray(Charsets.UTF_8)
                    )
                } else {
                    sendSimpleResponse(output, 401, "Unauthorized", "Valid 6-digit PIN required")
                }
                return
            }

            // Set HttpOnly SameSite=Strict cookie when authenticated via query param/header on landing page
            val setCookieHeader = if (queryParams["pin"] == sessionPin) {
                listOf("Set-Cookie: $COOKIE_NAME=$sessionPin; HttpOnly; SameSite=Strict; Path=/")
            } else {
                emptyList()
            }

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    val items = itemsProvider()
                    val folderName = receiveDirectoryProvider().name.ifEmpty { "ARCA Drop" }
                    val html = ArcaDropHtmlPortal.renderPortalHtml(
                        items = items,
                        sharedText = sharedTextProvider(),
                        receiveFolderName = folderName,
                        pinToken = sessionPin
                    )
                    sendBytes(
                        output = output,
                        statusCode = 200,
                        statusText = "OK",
                        contentType = "text/html; charset=utf-8",
                        body = html.toByteArray(Charsets.UTF_8),
                        extraHeaders = setCookieHeader
                    )
                }

                method == "GET" && path == "/api/status" -> {
                    val items = itemsProvider()
                    val json = """{"running":true,"itemCount":${items.size}}"""
                    sendBytes(
                        output = output,
                        statusCode = 200,
                        statusText = "OK",
                        contentType = "application/json; charset=utf-8",
                        body = json.toByteArray(Charsets.UTF_8)
                    )
                }

                method == "GET" && path.startsWith("/file/") -> {
                    val id = decodeUrlComponent(path.removePrefix("/file/"))
                    val inline = queryParams["inline"] == "1"
                    handleServeFile(id, inline, headers["range"], output)
                }

                method == "GET" && path.startsWith("/thumb/") -> {
                    val id = decodeUrlComponent(path.removePrefix("/thumb/"))
                    handleServeThumbnail(id, output)
                }

                method == "GET" && path == "/download-all" -> {
                    handleDownloadAllZip(output)
                }

                method == "POST" && path == "/upload" -> {
                    val contentLength = headers["content-length"]?.toLongOrNull()
                    if (contentLength == null || contentLength < 0L) {
                        sendSimpleResponse(output, 411, "Length Required", "Valid Content-Length required")
                    } else {
                        val filename = queryParams["filename"]
                        handleUpload(filename, contentLength, input, output)
                    }
                }

                method == "POST" && path == "/api/text" -> {
                    val contentLength = headers["content-length"]?.toLongOrNull()
                    if (contentLength == null || contentLength < 0L || contentLength > MAX_TEXT_BODY_BYTES) {
                        sendSimpleResponse(output, 400, "Bad Request", "Invalid text payload length")
                    } else {
                        handleReceiveText(contentLength, input, output)
                    }
                }

                else -> {
                    sendSimpleResponse(output, 404, "Not Found", "Endpoint not found")
                }
            }
        } catch (_: Exception) {
            // Connection closed or socket timeout
        } finally {
            runCatching { socket.close() }
            val remaining = activeConnections.decrementAndGet().coerceAtLeast(0)
            onActiveConnectionsChanged(remaining)
        }
    }

    private fun handleServeFile(
        itemId: String,
        inlineRequested: Boolean,
        rangeHeader: String?,
        output: BufferedOutputStream
    ) {
        val item = itemsProvider().firstOrNull { it.id == itemId }
        if (item == null) {
            sendSimpleResponse(output, 404, "Not Found", "File not found")
            return
        }

        val file = item.file
        val uri = item.uri
        val resolver = contentResolver

        val totalSize: Long = when {
            file != null && file.exists() -> file.length()
            uri != null && resolver != null -> item.size
            else -> -1L
        }
        if (totalSize < 0L) {
            sendSimpleResponse(output, 404, "Not Found", "File unreadable")
            return
        }

        var startByte = 0L
        var endByte = if (totalSize > 0L) totalSize - 1L else -1L
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && totalSize > 0L && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val spec = rangeHeader.substring(6).trim()
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val sPart = spec.substring(0, dash).trim()
                val ePart = spec.substring(dash + 1).trim()
                val parsedStart = sPart.toLongOrNull()
                val parsedEnd = ePart.toLongOrNull()
                if (parsedStart != null) {
                    startByte = parsedStart.coerceIn(0L, totalSize - 1L)
                    endByte = (parsedEnd ?: (totalSize - 1L)).coerceIn(startByte, totalSize - 1L)
                    isPartial = true
                } else if (parsedEnd != null && parsedEnd > 0L) {
                    startByte = (totalSize - parsedEnd).coerceAtLeast(0L)
                    endByte = totalSize - 1L
                    isPartial = true
                }
            }
        }

        val lengthToSend = if (totalSize == 0L) 0L else (endByte - startByte + 1L)
        val safeDispositionName = item.name.replace("\"", "_").replace("\r", "").replace("\n", "")
        val lowerName = item.name.lowercase()
        val forceAttachment = lowerName.endsWith(".html") ||
            lowerName.endsWith(".htm") ||
            lowerName.endsWith(".svg") ||
            lowerName.endsWith(".apk") ||
            lowerName.endsWith(".exe") ||
            lowerName.endsWith(".sh") ||
            !inlineRequested

        val disposition = if (forceAttachment) {
            "attachment; filename=\"$safeDispositionName\""
        } else {
            "inline; filename=\"$safeDispositionName\""
        }

        val statusLine = if (isPartial) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"
        val headersBuilder = StringBuilder()
            .append(statusLine).append("\r\n")
            .append("Content-Type: ").append(item.mimeType.ifBlank { "application/octet-stream" }).append("\r\n")
            .append("Content-Length: ").append(lengthToSend).append("\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Disposition: ").append(disposition).append("\r\n")
            .append("X-Content-Type-Options: nosniff\r\n")
            .append("Connection: close\r\n")
        if (isPartial) {
            headersBuilder.append("Content-Range: bytes ").append(startByte).append('-').append(endByte)
                .append('/').append(totalSize).append("\r\n")
        }
        headersBuilder.append("\r\n")

        output.write(headersBuilder.toString().toByteArray(Charsets.US_ASCII))

        val inputStream: InputStream? = when {
            file != null && file.exists() -> FileInputStream(file)
            uri != null && resolver != null -> resolver.openInputStream(uri)
            else -> null
        }

        if (inputStream == null) {
            output.flush()
            return
        }

        inputStream.use { rawIn ->
            skipFully(rawIn, startByte)
            val copied = copyStreamWithLimit(rawIn, output, lengthToSend) { delta ->
                lastActivityMs.set(System.currentTimeMillis())
                onTransferProgress(delta, 0L)
            }
            output.flush()
            if (copied > 0L) {
                onLog("Sent ${item.name} ($copied bytes)", false)
            }
        }
    }

    private fun handleServeThumbnail(itemId: String, output: BufferedOutputStream) {
        val item = itemsProvider().firstOrNull { it.id == itemId }
        val file = item?.file
        if (item == null || file == null || !file.exists() || !item.mimeType.startsWith("image/", ignoreCase = true)) {
            sendSimpleResponse(output, 404, "Not Found", "Thumbnail unavailable")
            return
        }

        val jpegBytes = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sampleSize = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sampleSize > 360) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return@runCatching null
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                baos.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()

        if (jpegBytes == null || jpegBytes.isEmpty()) {
            sendSimpleResponse(output, 404, "Not Found", "Thumbnail unavailable")
        } else {
            sendBytes(
                output = output,
                statusCode = 200,
                statusText = "OK",
                contentType = "image/jpeg",
                body = jpegBytes,
                extraHeaders = listOf("Cache-Control: private, max-age=300")
            )
        }
    }

    private fun handleDownloadAllZip(output: BufferedOutputStream) {
        val items = itemsProvider()
        if (items.isEmpty()) {
            sendSimpleResponse(output, 404, "Not Found", "No shared items")
            return
        }

        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/zip\r\n")
            append("Content-Disposition: attachment; filename=\"ARCA_Drop_Files.zip\"\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.flush()

        val usedEntryNames = HashSet<String>()
        val buffer = ByteArray(BUFFER_SIZE)
        ZipOutputStream(output).use { zos ->
            for (item in items) {
                val rawEntry = sanitizeZipEntryPath(item.relativePath, item.name)
                var uniqueEntry = rawEntry
                var counter = 1
                while (!usedEntryNames.add(uniqueEntry)) {
                    val dot = rawEntry.lastIndexOf('.')
                    uniqueEntry = if (dot > 0) {
                        "${rawEntry.substring(0, dot)}_$counter${rawEntry.substring(dot)}"
                    } else {
                        "${rawEntry}_$counter"
                    }
                    counter++
                }

                val stream: InputStream? = when {
                    item.file != null && item.file.exists() -> FileInputStream(item.file)
                    item.uri != null && contentResolver != null -> contentResolver.openInputStream(item.uri)
                    else -> null
                }

                if (stream != null) {
                    zos.putNextEntry(ZipEntry(uniqueEntry))
                    stream.use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            zos.write(buffer, 0, read)
                            lastActivityMs.set(System.currentTimeMillis())
                            onTransferProgress(read.toLong(), 0L)
                        }
                    }
                    zos.closeEntry()
                }
            }
            zos.finish()
        }
        output.flush()
        onLog("Streamed ${items.size} file(s) as ZIP", false)
    }

    private fun handleUpload(
        rawFilename: String?,
        contentLength: Long,
        input: BufferedInputStream,
        output: BufferedOutputStream
    ) {
        val targetDir = receiveDirectoryProvider()
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            sendSimpleResponse(output, 500, "Internal Server Error", "Cannot create destination folder")
            return
        }

        val cleanName = sanitizeUploadFilename(rawFilename)
        val finalFile = resolveUniqueDestinationFile(targetDir, cleanName)
        val tempFile = File(targetDir, ".arca_drop_tmp_${UUID.randomUUID()}")

        var receivedBytes = 0L
        try {
            FileOutputStream(tempFile).use { fos ->
                receivedBytes = copyStreamWithLimit(input, fos, contentLength) { delta ->
                    lastActivityMs.set(System.currentTimeMillis())
                    onTransferProgress(0L, delta)
                }
                fos.fd.sync()
            }

            if (receivedBytes != contentLength) {
                tempFile.delete()
                onLog("Upload incomplete for $cleanName ($receivedBytes / $contentLength)", true)
                sendSimpleResponse(output, 400, "Bad Request", "Incomplete upload stream")
                return
            }

            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            if (context != null) {
                runCatching {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalFile.absolutePath),
                        null,
                        null
                    )
                }
            }

            onFileReceived(finalFile.name, finalFile.absolutePath, receivedBytes)
            onLog("Received ${finalFile.name} ($receivedBytes bytes)", false)
            sendBytes(
                output = output,
                statusCode = 200,
                statusText = "OK",
                contentType = "application/json; charset=utf-8",
                body = """{"saved":true,"name":"${finalFile.name.replace("\"", "\\\"")}"}""".toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            tempFile.delete()
            onLog("Upload error for $cleanName: ${e.message ?: "I/O error"}", true)
            sendSimpleResponse(output, 500, "Internal Server Error", "Upload failed")
        }
    }

    private fun handleReceiveText(
        contentLength: Long,
        input: BufferedInputStream,
        output: BufferedOutputStream
    ) {
        val baos = ByteArrayOutputStream(contentLength.toInt().coerceAtMost(65_536))
        val read = copyStreamWithLimit(input, baos, contentLength) { delta ->
            lastActivityMs.set(System.currentTimeMillis())
            onTransferProgress(0L, delta)
        }
        if (read != contentLength) {
            sendSimpleResponse(output, 400, "Bad Request", "Incomplete text payload")
            return
        }
        val text = baos.toString(Charsets.UTF_8.name()).trim()
        if (text.isNotEmpty()) {
            onTextReceived(text)
            onLog("Received text snippet (${text.length} chars)", false)
        }
        sendBytes(
            output = output,
            statusCode = 200,
            statusText = "OK",
            contentType = "application/json; charset=utf-8",
            body = """{"received":true}""".toByteArray(Charsets.UTF_8)
        )
    }

    private fun handleCorsPreflight(originHeader: String?, output: BufferedOutputStream) {
        if (originHeader.isNullOrBlank()) {
            sendSimpleResponse(output, 204, "No Content", "")
            return
        }
        val originHost = extractHostFromUrl(originHeader)
        if (!isAllowedHostValue(originHost)) {
            sendSimpleResponse(output, 403, "Forbidden", "Cross-origin access denied")
            return
        }
        val response = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            append("Access-Control-Allow-Origin: ").append(originHeader.replace("\r", "").replace("\n", "")).append("\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, X-Arca-Drop-Pin\r\n")
            append("Vary: Origin\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(response.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    internal fun isAllowedHostHeader(hostHeader: String?): Boolean {
        if (hostHeader.isNullOrBlank()) return false
        val cleanHost = stripPortFromHost(hostHeader)
        return isAllowedHostValue(cleanHost)
    }

    private fun isAllowedHostValue(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lower = host.lowercase()
        if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1" || lower == "[::1]") {
            return true
        }
        val currentLan = ipAddressProvider()
        if (currentLan != null && lower == currentLan.lowercase()) {
            return true
        }
        return LocalLanNetworkUtil.isPrivateOrLocalAddress(host)
    }

    private fun stripPortFromHost(hostHeader: String): String {
        val trimmed = hostHeader.trim()
        if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            return if (closeIdx > 0) trimmed.substring(1, closeIdx) else trimmed
        }
        val colonIdx = trimmed.indexOf(':')
        return if (colonIdx > 0) trimmed.substring(0, colonIdx) else trimmed
    }

    private fun extractHostFromUrl(url: String): String? {
        val schemeSep = url.indexOf("://")
        val afterScheme = if (schemeSep >= 0) url.substring(schemeSep + 3) else url
        val slash = afterScheme.indexOf('/')
        val authority = if (slash >= 0) afterScheme.substring(0, slash) else afterScheme
        return stripPortFromHost(authority).takeIf { it.isNotBlank() }
    }

    private fun extractPin(queryParams: Map<String, String>, headers: Map<String, String>): String? {
        queryParams["pin"]?.takeIf { it.isNotBlank() }?.let { return it }
        headers["x-arca-drop-pin"]?.takeIf { it.isNotBlank() }?.let { return it }

        val cookieHeader = headers["cookie"] ?: return null
        for (pair in cookieHeader.split(';')) {
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val name = pair.substring(0, eq).trim()
                if (name == COOKIE_NAME) {
                    return pair.substring(eq + 1).trim()
                }
            }
        }
        return null
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    private fun resolveUniqueDestinationFile(dir: File, filename: String): File {
        var candidate = File(dir, filename)
        if (!candidate.exists()) return candidate

        val dotIdx = filename.lastIndexOf('.')
        val baseName = if (dotIdx > 0) filename.substring(0, dotIdx) else filename
        val extension = if (dotIdx > 0) filename.substring(dotIdx) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${baseName}_$counter$extension")
            counter++
        }
        return candidate
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream(256)
        var prevWasCr = false
        while (baos.size() < MAX_HEADER_LINE_BYTES) {
            val b = input.read()
            if (b == -1) {
                return if (baos.size() == 0) null else baos.toString(Charsets.US_ASCII.name())
            }
            if (b == '\n'.code) {
                val bytes = baos.toByteArray()
                val len = if (prevWasCr && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
                return String(bytes, 0, len, Charsets.US_ASCII)
            }
            prevWasCr = (b == '\r'.code)
            baos.write(b)
        }
        return baos.toString(Charsets.US_ASCII.name())
    }

    private fun parseQueryParams(rawQuery: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val k = decodeUrlComponent(pair.substring(0, eq))
                val v = decodeUrlComponent(pair.substring(eq + 1))
                result[k] = v
            } else {
                result[decodeUrlComponent(pair)] = ""
            }
        }
        return result
    }

    private fun decodeUrlComponent(raw: String): String {
        return runCatching { java.net.URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val discarded = input.read()
                if (discarded == -1) break
                remaining--
            }
        }
    }

    private fun copyStreamWithLimit(
        input: InputStream,
        output: OutputStream,
        limitBytes: Long,
        onChunk: (Long) -> Unit = {}
    ): Long {
        if (limitBytes <= 0L) return 0L
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = limitBytes
        var total = 0L
        while (remaining > 0L) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
            total += read
            onChunk(read.toLong())
        }
        return total
    }

    private fun sendSimpleResponse(
        output: BufferedOutputStream,
        statusCode: Int,
        statusText: String,
        message: String
    ) {
        sendBytes(
            output = output,
            statusCode = statusCode,
            statusText = statusText,
            contentType = "text/plain; charset=utf-8",
            body = message.toByteArray(Charsets.UTF_8)
        )
    }

    private fun sendBytes(
        output: BufferedOutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: List<String> = emptyList()
    ) {
        val header = buildString {
            append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            for (h in extraHeaders) {
                append(h).append("\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }
}
