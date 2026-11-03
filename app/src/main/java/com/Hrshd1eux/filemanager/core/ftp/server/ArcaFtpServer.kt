package com.Hrshd1eux.filemanager.core.ftp.server

import android.content.Context
import com.Hrshd1eux.filemanager.core.storage.apk.ApkShareManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Activity log entry emitted by ArcaFtpServer.
 */
data class FtpActivityLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val clientIp: String,
    val message: String,
    val isError: Boolean = false
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Lightweight, high-performance RFC 959 / RFC 3659 compliant embedded FTP Server for Android.
 *
 * Adheres strictly to security and platform constraints:
 * 1. Sandboxed path canonicalization: Strictly confines clients to the configured root folder.
 * 2. Enforces authentication (User/Password or Anonymous) and Read-Only permissions.
 * 3. Supports Passive (PASV, EPSV) and Active (PORT) data transfer modes.
 * 4. Virtual APK Directory: Seamlessly exposes ARCA's own APK and shared packages for PC/mobile download.
 * 5. Multi-client session handling with live telemetry (clients, bytes sent/received, activity log).
 */
class ArcaFtpServer(
    val config: FtpServerConfig,
    private val appContext: Context? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onStateChanged: ((running: Boolean, port: Int, clients: Int, sent: Long, received: Long) -> Unit)? = null,
    private val onLog: ((FtpActivityLog) -> Unit)? = null
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val activeClients = ConcurrentHashMap<String, Socket>()
    private val clientCount = AtomicInteger(0)
    private val bytesSent = AtomicLong(0L)
    private val bytesReceived = AtomicLong(0L)

    val effectivePort: Int
        get() = serverSocket?.localPort ?: config.port

    val isRunning: Boolean
        get() = serverSocket != null && serverSocket?.isClosed == false && (acceptJob == null || acceptJob?.isActive == true)

    /**
     * Starts the FTP Server listening on the configured port.
     */
    suspend fun start(): Result<Unit> = withContext(dispatcher) {
        runCatching {
            if (isRunning) return@withContext Result.success(Unit)

            val root = config.rootFile
            if (!root.exists()) {
                root.mkdirs()
            }

            var chosenPort = config.port
            var socket = ServerSocket()
            socket.reuseAddress = true

            try {
                socket.bind(java.net.InetSocketAddress(chosenPort))
            } catch (e: java.net.BindException) {
                // Port config.port is already in use by another app or in TIME_WAIT.
                // Automatically find the next available port (e.g. port + 1, port + 2, etc.)
                socket.close()
                var bound = false
                for (offset in 1..10) {
                    val candidate = config.port + offset
                    if (candidate > 65535) break
                    val testSocket = ServerSocket()
                    testSocket.reuseAddress = true
                    try {
                        testSocket.bind(java.net.InetSocketAddress(candidate))
                        socket = testSocket
                        chosenPort = candidate
                        bound = true
                        emitLog("Port ${config.port} was busy. Automatically switched to port $chosenPort.")
                        break
                    } catch (_: java.net.BindException) {
                        testSocket.close()
                    }
                }
                if (!bound) {
                    throw java.net.BindException("Port ${config.port} is already in use by another app. Please change the port in Settings.")
                }
            }

            serverSocket = socket
            val activeSocket = socket

            val job = CoroutineScope(dispatcher).launch {
                while (isActive && !activeSocket.isClosed) {
                    try {
                        val clientSocket = activeSocket.accept()
                        val clientKey = "${clientSocket.inetAddress.hostAddress}:${clientSocket.port}"
                        activeClients[clientKey] = clientSocket
                        val count = clientCount.incrementAndGet()
                        emitLog("Client connected: ${clientSocket.inetAddress.hostAddress} (Active: $count)")
                        notifyState()

                        launch {
                            handleClientSession(clientSocket, clientKey)
                        }
                    } catch (e: SocketException) {
                        // Socket closed gracefully
                        break
                    } catch (e: Exception) {
                        emitLog("Error accepting connection: ${e.message}", isError = true)
                    }
                }
            }
            acceptJob = job

            emitLog("FTP Server started on port $chosenPort (Root: ${config.rootDisplayName})")
            notifyState()
        }
    }

    /**
     * Stops the FTP Server and disconnects all active clients.
     */
    fun stop() {
        try {
            acceptJob?.cancel()
            acceptJob = null

            for ((_, client) in activeClients) {
                runCatching { client.close() }
            }
            activeClients.clear()
            clientCount.set(0)

            runCatching { serverSocket?.close() }
            serverSocket = null

            emitLog("FTP Server stopped.")
            notifyState()
        } catch (e: Exception) {
            emitLog("Error stopping FTP Server: ${e.message}", isError = true)
        }
    }

    private fun handleClientSession(clientSocket: Socket, clientKey: String) {
        val clientIp = clientSocket.inetAddress.hostAddress ?: "Unknown"
        var isAuthenticated = config.isAnonymousAllowed
        var currentUser: String? = null
        var currentWorkingDir = "/" // relative to rootFile
        var isBinaryMode = true
        var restOffset = 0L
        var renameFromTarget: File? = null

        var passiveServerSocket: ServerSocket? = null
        var activeDataAddress: InetAddress? = null
        var activeDataPort: Int? = null

        fun closeDataServer() {
            runCatching { passiveServerSocket?.close() }
            passiveServerSocket = null
            activeDataAddress = null
            activeDataPort = null
        }

        try {
            clientSocket.soTimeout = 180_000 // 3 minutes timeout
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(clientSocket.getOutputStream(), Charsets.UTF_8))

            fun sendReply(code: Int, text: String) {
                writer.write("$code $text\r\n")
                writer.flush()
            }

            fun sendReplyMultiline(code: Int, lines: List<String>) {
                if (lines.isEmpty()) {
                    sendReply(code, "OK")
                    return
                }
                for (i in lines.indices) {
                    if (i == lines.lastIndex) {
                        writer.write("$code ${lines[i]}\r\n")
                    } else if (i == 0) {
                        writer.write("$code-${lines[i]}\r\n")
                    } else {
                        writer.write(" ${lines[i]}\r\n")
                    }
                }
                writer.flush()
            }

            // Welcome banner
            sendReply(220, "ARCA FTP Server Ready")

            while (isRunning && !clientSocket.isClosed) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val parts = trimmed.split(Regex("\\s+"), limit = 2)
                val command = parts[0].uppercase(Locale.ROOT)
                val arg = if (parts.size > 1) parts[1] else ""

                // 1. Authentication Commands
                when (command) {
                    "USER" -> {
                        currentUser = arg
                        if (config.isAnonymousAllowed && (arg.equals("anonymous", ignoreCase = true) || arg.equals("guest", ignoreCase = true))) {
                            isAuthenticated = true
                            sendReply(230, "Guest login ok, proceed.")
                            emitLog("User '$arg' logged in anonymously ($clientIp)")
                        } else if (arg == config.username) {
                            sendReply(331, "User name okay, need password.")
                        } else {
                            // If username doesn't match and not anonymous
                            sendReply(331, "User name okay, need password.")
                        }
                        continue
                    }
                    "PASS" -> {
                        if (config.isAnonymousAllowed && (currentUser.equals("anonymous", ignoreCase = true) || currentUser.equals("guest", ignoreCase = true))) {
                            isAuthenticated = true
                            sendReply(230, "User logged in, proceed.")
                        } else if (currentUser == config.username && arg == config.password) {
                            isAuthenticated = true
                            sendReply(230, "User logged in, proceed.")
                            emitLog("User '$currentUser' authenticated successfully ($clientIp)")
                        } else {
                            isAuthenticated = false
                            sendReply(530, "Login incorrect.")
                            emitLog("Authentication failed for '$currentUser' ($clientIp)", isError = true)
                        }
                        continue
                    }
                    "QUIT" -> {
                        sendReply(221, "Service closing control connection.")
                        break
                    }
                    "NOOP" -> {
                        sendReply(200, "OK")
                        continue
                    }
                    "SYST" -> {
                        sendReply(215, "UNIX Type: L8")
                        continue
                    }
                    "FEAT" -> {
                        sendReplyMultiline(211, listOf(
                            "Features:",
                            "UTF8",
                            "SIZE",
                            "MDTM",
                            "REST STREAM",
                            "MLST type*;size*;modify*;",
                            "MLSD",
                            "PASV",
                            "EPSV",
                            "End"
                        ))
                        continue
                    }
                    "OPTS" -> {
                        if (arg.uppercase(Locale.ROOT).startsWith("UTF8")) {
                            sendReply(200, "UTF8 mode is ON")
                        } else {
                            sendReply(502, "Command not implemented.")
                        }
                        continue
                    }
                    "AUTH" -> {
                        sendReply(502, "SSL/TLS not supported on cleartext FTP port.")
                        continue
                    }
                }

                // Guard: Must be authenticated
                if (!isAuthenticated) {
                    sendReply(530, "Please login with USER and PASS.")
                    continue
                }

                // 2. Navigation & Path Commands
                when (command) {
                    "PWD" -> {
                        sendReply(257, "\"$currentWorkingDir\" is current directory.")
                    }
                    "CWD" -> {
                        val newPath = resolveRelativePath(currentWorkingDir, arg)
                        if (newPath == "/APKs" && config.shareApkEnabled) {
                            currentWorkingDir = "/APKs"
                            sendReply(250, "Directory changed to /APKs")
                        } else {
                            val targetFile = resolvePhysicalFile(newPath)
                            if (targetFile != null && targetFile.exists() && targetFile.isDirectory) {
                                currentWorkingDir = newPath
                                sendReply(250, "Directory changed to $currentWorkingDir")
                            } else {
                                sendReply(550, "Failed to change directory.")
                            }
                        }
                    }
                    "CDUP" -> {
                        currentWorkingDir = if (currentWorkingDir == "/APKs") {
                            "/"
                        } else {
                            val parent = currentWorkingDir.substringBeforeLast('/').ifEmpty { "/" }
                            parent
                        }
                        sendReply(250, "Directory changed to $currentWorkingDir")
                    }
                    "TYPE" -> {
                        val mode = arg.uppercase(Locale.ROOT)
                        if (mode == "I" || mode == "L 8") {
                            isBinaryMode = true
                            sendReply(200, "Type set to I (Binary).")
                        } else if (mode == "A") {
                            isBinaryMode = false
                            sendReply(200, "Type set to A (ASCII).")
                        } else {
                            sendReply(504, "Command not implemented for that parameter.")
                        }
                    }
                    "PASV" -> {
                        closeDataServer()
                        val pServer = ServerSocket(0)
                        passiveServerSocket = pServer
                        val localPort = pServer.localPort
                        val clientLocal = clientSocket.localAddress
                        val localIp = when {
                            clientLocal is java.net.Inet4Address -> clientLocal.hostAddress
                            clientLocal != null && clientLocal.hostAddress?.startsWith("::ffff:") == true -> clientLocal.hostAddress?.removePrefix("::ffff:")
                            else -> FtpNetworkUtil.getLocalIpAddress() ?: "127.0.0.1"
                        } ?: "127.0.0.1"
                        val ipParts = localIp.split(".").take(4)
                        val ipStr = if (ipParts.size == 4) ipParts.joinToString(",") else "127,0,0,1"
                        val p1 = localPort / 256
                        val p2 = localPort % 256
                        sendReply(227, "Entering Passive Mode ($ipStr,$p1,$p2)")
                    }
                    "EPSV" -> {
                        closeDataServer()
                        val pServer = ServerSocket(0)
                        passiveServerSocket = pServer
                        val localPort = pServer.localPort
                        sendReply(229, "Entering Extended Passive Mode (|||$localPort|)")
                    }
                    "PORT" -> {
                        closeDataServer()
                        val partsArr = arg.split(",")
                        if (partsArr.size == 6) {
                            try {
                                val host = "${partsArr[0]}.${partsArr[1]}.${partsArr[2]}.${partsArr[3]}"
                                val p = (partsArr[4].toInt() shl 8) or partsArr[5].toInt()
                                activeDataAddress = InetAddress.getByName(host)
                                activeDataPort = p
                                sendReply(200, "PORT command successful.")
                            } catch (_: Exception) {
                                sendReply(501, "Syntax error in parameters.")
                            }
                        } else {
                            sendReply(501, "Syntax error in parameters.")
                        }
                    }
                    "REST" -> {
                        val offset = arg.toLongOrNull() ?: 0L
                        restOffset = offset.coerceAtLeast(0L)
                        sendReply(350, "Restarting at $restOffset. Send STORE or RETRIEVE.")
                    }

                    // 3. Directory Listings (LIST, NLST, MLSD)
                    "LIST", "NLST", "MLSD" -> {
                        val targetDir = if (arg.isNotBlank() && !arg.startsWith("-")) {
                            resolveRelativePath(currentWorkingDir, arg)
                        } else {
                            currentWorkingDir
                        }

                        sendReply(150, "Opening ASCII mode data connection for file list.")
                        val dataSocket = openDataConnection(passiveServerSocket, activeDataAddress, activeDataPort)
                        closeDataServer()

                        if (dataSocket == null) {
                            sendReply(425, "Can't open data connection.")
                            continue
                        }

                        try {
                            val dWriter = BufferedWriter(OutputStreamWriter(dataSocket.getOutputStream(), Charsets.UTF_8))
                            val entries = listDirectoryVirtualAndPhysical(targetDir)

                            val isMlsd = command == "MLSD"
                            val isNlst = command == "NLST"

                            for (item in entries) {
                                val lineOut = when {
                                    isMlsd -> item.toMlsdString()
                                    isNlst -> "${item.name}\r\n"
                                    else -> item.toUnixListString()
                                }
                                dWriter.write(lineOut)
                            }
                            dWriter.flush()
                            dataSocket.close()
                            sendReply(226, "Closing data connection. Transfer complete.")
                        } catch (e: Exception) {
                            runCatching { dataSocket.close() }
                            sendReply(451, "Transfer failed: ${e.message}")
                        }
                    }

                    // 4. File Retrieval / Download (RETR)
                    "RETR" -> {
                        val filePath = resolveRelativePath(currentWorkingDir, arg)
                        val virtualApk = getVirtualApkFile(filePath)
                        val targetFile = virtualApk ?: resolvePhysicalFile(filePath)

                        if (targetFile == null || !targetFile.exists() || targetFile.isDirectory) {
                            sendReply(550, "File not found or is a directory.")
                            closeDataServer()
                            continue
                        }

                        val totalSize = targetFile.length()
                        sendReply(150, "Opening binary mode data connection for ${targetFile.name} ($totalSize bytes).")

                        val dataSocket = openDataConnection(passiveServerSocket, activeDataAddress, activeDataPort)
                        closeDataServer()

                        if (dataSocket == null) {
                            sendReply(425, "Can't open data connection.")
                            continue
                        }

                        try {
                            val inStream = FileInputStream(targetFile)
                            if (restOffset > 0L && restOffset < totalSize) {
                                inStream.skip(restOffset)
                            }
                            restOffset = 0L

                            val outStream = dataSocket.getOutputStream()
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var transferred = 0L

                            while (inStream.read(buffer).also { read = it } != -1) {
                                outStream.write(buffer, 0, read)
                                transferred += read
                                bytesSent.addAndGet(read.toLong())
                            }
                            outStream.flush()
                            inStream.close()
                            dataSocket.close()

                            emitLog("Sent file: ${targetFile.name} ($transferred bytes to $clientIp)")
                            notifyState()
                            sendReply(226, "Transfer complete.")
                        } catch (e: Exception) {
                            runCatching { dataSocket.close() }
                            sendReply(451, "Transfer aborted: ${e.message}")
                        }
                    }

                    // 5. File Upload (STOR, APPE)
                    "STOR", "APPE" -> {
                        if (config.isReadOnly) {
                            sendReply(550, "Permission denied: Server is running in Read-Only mode.")
                            closeDataServer()
                            continue
                        }

                        val filePath = resolveRelativePath(currentWorkingDir, arg)
                        if (filePath.startsWith("/APKs")) {
                            sendReply(550, "Cannot upload to virtual APKs directory.")
                            closeDataServer()
                            continue
                        }

                        val targetFile = resolvePhysicalFile(filePath)
                        if (targetFile == null) {
                            sendReply(550, "Invalid file destination.")
                            closeDataServer()
                            continue
                        }

                        sendReply(150, "Ready to receive data for ${targetFile.name}.")

                        val dataSocket = openDataConnection(passiveServerSocket, activeDataAddress, activeDataPort)
                        closeDataServer()

                        if (dataSocket == null) {
                            sendReply(425, "Can't open data connection.")
                            continue
                        }

                        try {
                            val append = command == "APPE" || restOffset > 0L
                            val outStream = FileOutputStream(targetFile, append)
                            val inStream = dataSocket.getInputStream()
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var transferred = 0L

                            while (inStream.read(buffer).also { read = it } != -1) {
                                outStream.write(buffer, 0, read)
                                transferred += read
                                bytesReceived.addAndGet(read.toLong())
                            }
                            outStream.flush()
                            outStream.close()
                            dataSocket.close()
                            restOffset = 0L

                            emitLog("Received file: ${targetFile.name} ($transferred bytes from $clientIp)")
                            notifyState()
                            sendReply(226, "Transfer complete.")
                        } catch (e: Exception) {
                            runCatching { dataSocket.close() }
                            sendReply(451, "Upload failed: ${e.message}")
                        }
                    }

                    // 6. File & Directory Management (DELE, MKD, RMD, RNFR, RNTO)
                    "DELE" -> {
                        if (config.isReadOnly) {
                            sendReply(550, "Permission denied: Server is running in Read-Only mode.")
                            continue
                        }
                        val filePath = resolveRelativePath(currentWorkingDir, arg)
                        val targetFile = resolvePhysicalFile(filePath)
                        if (targetFile != null && targetFile.exists() && targetFile.isFile && targetFile.delete()) {
                            emitLog("Deleted file: ${targetFile.name} ($clientIp)")
                            sendReply(250, "File deleted successfully.")
                        } else {
                            sendReply(550, "Delete operation failed.")
                        }
                    }
                    "MKD" -> {
                        if (config.isReadOnly) {
                            sendReply(550, "Permission denied: Server is running in Read-Only mode.")
                            continue
                        }
                        val dirPath = resolveRelativePath(currentWorkingDir, arg)
                        val targetDir = resolvePhysicalFile(dirPath)
                        if (targetDir != null && (targetDir.exists() || targetDir.mkdirs())) {
                            emitLog("Created directory: ${targetDir.name} ($clientIp)")
                            sendReply(257, "\"$dirPath\" created.")
                        } else {
                            sendReply(550, "Failed to create directory.")
                        }
                    }
                    "RMD" -> {
                        if (config.isReadOnly) {
                            sendReply(550, "Permission denied: Server is running in Read-Only mode.")
                            continue
                        }
                        val dirPath = resolveRelativePath(currentWorkingDir, arg)
                        val targetDir = resolvePhysicalFile(dirPath)
                        if (targetDir != null && targetDir.exists() && targetDir.isDirectory && targetDir.delete()) {
                            emitLog("Removed directory: ${targetDir.name} ($clientIp)")
                            sendReply(250, "Directory removed successfully.")
                        } else {
                            sendReply(550, "Failed to remove directory.")
                        }
                    }
                    "RNFR" -> {
                        if (config.isReadOnly) {
                            sendReply(550, "Permission denied: Server is running in Read-Only mode.")
                            continue
                        }
                        val filePath = resolveRelativePath(currentWorkingDir, arg)
                        val targetFile = resolvePhysicalFile(filePath)
                        if (targetFile != null && targetFile.exists()) {
                            renameFromTarget = targetFile
                            sendReply(350, "File exists, ready for destination name.")
                        } else {
                            renameFromTarget = null
                            sendReply(550, "File not found.")
                        }
                    }
                    "RNTO" -> {
                        val fromFile = renameFromTarget
                        renameFromTarget = null
                        if (fromFile == null) {
                            sendReply(503, "Bad sequence of commands.")
                            continue
                        }
                        if (config.isReadOnly) {
                            sendReply(550, "Permission denied: Server is running in Read-Only mode.")
                            continue
                        }
                        val toPath = resolveRelativePath(currentWorkingDir, arg)
                        val toFile = resolvePhysicalFile(toPath)
                        if (toFile != null && fromFile.renameTo(toFile)) {
                            emitLog("Renamed: ${fromFile.name} -> ${toFile.name} ($clientIp)")
                            sendReply(250, "File renamed successfully.")
                        } else {
                            sendReply(550, "Rename failed.")
                        }
                    }

                    // 7. Metadata Queries (SIZE, MDTM, MLST)
                    "SIZE" -> {
                        val filePath = resolveRelativePath(currentWorkingDir, arg)
                        val virtualApk = getVirtualApkFile(filePath)
                        val targetFile = virtualApk ?: resolvePhysicalFile(filePath)
                        if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                            sendReply(213, targetFile.length().toString())
                        } else {
                            sendReply(550, "File not found or is not a plain file.")
                        }
                    }
                    "MDTM" -> {
                        val filePath = resolveRelativePath(currentWorkingDir, arg)
                        val virtualApk = getVirtualApkFile(filePath)
                        val targetFile = virtualApk ?: resolvePhysicalFile(filePath)
                        if (targetFile != null && targetFile.exists()) {
                            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                            sendReply(213, sdf.format(Date(targetFile.lastModified())))
                        } else {
                            sendReply(550, "File not found.")
                        }
                    }
                    "MLST" -> {
                        val targetPath = if (arg.isNotBlank()) resolveRelativePath(currentWorkingDir, arg) else currentWorkingDir
                        val entries = listDirectoryVirtualAndPhysical(targetPath)
                        val target = entries.firstOrNull()
                        if (target != null) {
                            sendReplyMultiline(250, listOf(
                                "Listing $targetPath",
                                target.toMlsdString().trimEnd(),
                                "End"
                            ))
                        } else {
                            sendReply(550, "File not found.")
                        }
                    }

                    else -> {
                        sendReply(502, "Command '$command' not implemented.")
                    }
                }
            }
        } catch (e: Exception) {
            // Client error / disconnect
        } finally {
            closeDataServer()
            activeClients.remove(clientKey)
            runCatching { clientSocket.close() }
            val count = clientCount.decrementAndGet().coerceAtLeast(0)
            emitLog("Client disconnected: $clientIp (Active: $count)")
            notifyState()
        }
    }

    private fun openDataConnection(
        passiveServer: ServerSocket?,
        activeAddress: InetAddress?,
        activePort: Int?
    ): Socket? {
        return try {
            if (passiveServer != null) {
                passiveServer.soTimeout = 15_000
                passiveServer.accept()
            } else if (activeAddress != null && activePort != null) {
                Socket(activeAddress, activePort)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves a virtual path safely within the configured root directory.
     * Prevents directory traversal attacks by checking canonical paths.
     */
    fun resolvePhysicalFile(virtualPath: String): File? {
        val root = config.rootFile
        val cleanVirtual = virtualPath.trim().removeSurrounding("\"").removeSurrounding("'").trim().removePrefix("/").ifEmpty { "" }
        val target = if (cleanVirtual.isEmpty()) root else File(root, cleanVirtual)
        return try {
            val rootCanonical = root.canonicalPath
            val targetCanonical = target.canonicalPath
            if (targetCanonical.startsWith(rootCanonical)) {
                target
            } else {
                null // Blocked directory traversal!
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveRelativePath(currentWorkingDir: String, target: String): String {
        val trimmed = target.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (trimmed.startsWith("/")) {
            return normalizeVirtualPath(trimmed)
        }
        val combined = "$currentWorkingDir/$trimmed"
        return normalizeVirtualPath(combined)
    }

    private fun normalizeVirtualPath(path: String): String {
        val segments = path.split("/").filter { it.isNotBlank() && it != "." }
        val stack = ArrayList<String>()
        for (seg in segments) {
            if (seg == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            } else {
                stack.add(seg)
            }
        }
        return "/" + stack.joinToString("/")
    }

    /**
     * Virtual APK file provider. Allows download of ARCA's own APK or exported APKs.
     */
    private fun getVirtualApkFile(virtualPath: String): File? {
        if (!config.shareApkEnabled) return null
        if (!virtualPath.startsWith("/APKs/")) return null
        val fileName = virtualPath.removePrefix("/APKs/")

        // 1. ARCA's own APK binary
        if (fileName.startsWith("ARCA_") && fileName.endsWith(".apk")) {
            val sourceDir = appContext?.applicationInfo?.sourceDir
            if (!sourceDir.isNullOrBlank()) {
                val f = File(sourceDir)
                if (f.exists() && f.canRead()) return f
            }
        }

        // 2. Exported APKs in cache
        val sharedDir = File(appContext?.cacheDir, ApkShareManager.SHARED_APKS_DIR_NAME)
        if (sharedDir.exists() && sharedDir.isDirectory) {
            val target = File(sharedDir, fileName)
            if (target.exists() && target.canRead()) return target
        }
        return null
    }

    private data class FtpEntryInfo(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    ) {
        fun toUnixListString(): String {
            val perms = if (isDirectory) "drwxr-xr-x" else "-rw-r--r--"
            val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)
            val dateStr = sdf.format(Date(lastModified))
            return "$perms 1 owner group $size $dateStr $name\r\n"
        }

        fun toMlsdString(): String {
            val typeStr = if (isDirectory) "dir" else "file"
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val dateStr = sdf.format(Date(lastModified))
            return "type=$typeStr;size=$size;modify=$dateStr; $name\r\n"
        }
    }

    private fun listDirectoryVirtualAndPhysical(virtualPath: String): List<FtpEntryInfo> {
        val result = ArrayList<FtpEntryInfo>()

        // Virtual /APKs folder listing
        if (config.shareApkEnabled && virtualPath == "/APKs") {
            // ARCA app itself
            val sourceDir = appContext?.applicationInfo?.sourceDir
            if (!sourceDir.isNullOrBlank()) {
                val apkFile = File(sourceDir)
                if (apkFile.exists()) {
                    val ver = appContext.packageManager?.getPackageInfo(appContext.packageName, 0)?.versionName ?: "latest"
                    result.add(FtpEntryInfo("ARCA_FileManager_v$ver.apk", false, apkFile.length(), apkFile.lastModified()))
                }
            }
            // Exported shared APKs
            val sharedDir = File(appContext?.cacheDir, ApkShareManager.SHARED_APKS_DIR_NAME)
            if (sharedDir.exists()) {
                sharedDir.listFiles()?.forEach { file ->
                    result.add(FtpEntryInfo(file.name, file.isDirectory, file.length(), file.lastModified()))
                }
            }
            return result
        }

        // Physical directory listing
        val dir = resolvePhysicalFile(virtualPath)
        if (dir != null && dir.exists() && dir.isDirectory) {
            // Include /APKs virtual folder entry when at root "/"
            if (config.shareApkEnabled && virtualPath == "/") {
                result.add(FtpEntryInfo("APKs", true, 4096L, System.currentTimeMillis()))
            }

            val files = dir.listFiles()
            if (files != null) {
                // Directories first, then files sorted by name
                val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                for (f in sorted) {
                    if (f.name.startsWith(".")) continue // Skip hidden Unix files
                    result.add(FtpEntryInfo(f.name, f.isDirectory, if (f.isDirectory) 4096L else f.length(), f.lastModified()))
                }
            }
        }
        return result
    }

    private fun emitLog(message: String, isError: Boolean = false) {
        val entry = FtpActivityLog(
            clientIp = "Server",
            message = message,
            isError = isError
        )
        onLog?.invoke(entry)
    }

    private fun notifyState() {
        onStateChanged?.invoke(isRunning, effectivePort, clientCount.get(), bytesSent.get(), bytesReceived.get())
    }
}
