package com.Hrshd1eux.filemanager.core.ftp.server

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

@OptIn(ExperimentalCoroutinesApi::class)
class ArcaFtpServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var server: ArcaFtpServer
    private var testPort = 22121

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("ftp_root")
        File(rootDir, "test_document.txt").writeText("Hello from ARCA FTP Server!")
        File(rootDir, "SubFolder").mkdir()
        File(rootDir, "SubFolder/nested.txt").writeText("Nested file content")
    }

    @After
    fun tearDown() = runBlocking {
        if (::server.isInitialized && server.isRunning) {
            server.stop()
        }
    }

    @Test
    fun ftpConfig_defaultsAndRootDisplayName_areCorrect() {
        val config = FtpServerConfig(rootDirectoryPath = rootDir.absolutePath)
        assertEquals(FtpServerConfig.DEFAULT_PORT, config.port)
        assertEquals("arca", config.username)
        assertEquals("1234", config.password)
        assertFalse(config.isAnonymousAllowed)
        assertFalse(config.isReadOnly)
        assertTrue(config.shareApkEnabled)
        assertEquals(rootDir.name, config.rootDisplayName)
    }

    @Test
    fun ftpNetworkUtil_formatFtpUrl_formatsValidUri() {
        val url = FtpNetworkUtil.formatFtpUrl("192.168.1.100", 2121)
        assertEquals("ftp://192.168.1.100:2121/", url)
    }

    @Test
    fun server_startAndStop_updatesRunningState() = runBlocking {
        val config = FtpServerConfig(
            port = testPort,
            rootDirectoryPath = rootDir.absolutePath
        )
        server = ArcaFtpServer(config)
        assertFalse(server.isRunning)

        val result = server.start()
        assertTrue("Server must start successfully", result.isSuccess)
        assertTrue(server.isRunning)

        server.stop()
        assertFalse(server.isRunning)
    }

    @Test
    fun server_pathTraversal_isBlocked() {
        val config = FtpServerConfig(
            port = testPort,
            rootDirectoryPath = rootDir.absolutePath
        )
        server = ArcaFtpServer(config)

        // Valid paths within root
        val validFile = server.resolvePhysicalFile("/test_document.txt")
        assertNotNull(validFile)
        assertTrue(validFile!!.exists())

        val validNested = server.resolvePhysicalFile("/SubFolder/nested.txt")
        assertNotNull(validNested)
        assertTrue(validNested!!.exists())

        // Path traversal attempts
        val traversal1 = server.resolvePhysicalFile("/../../etc/passwd")
        // Canonical check prevents traversing above rootDir
        assertTrue(traversal1 == null || traversal1.canonicalPath.startsWith(rootDir.canonicalPath))

        val traversal2 = server.resolvePhysicalFile("../")
        assertTrue(traversal2 == null || traversal2.canonicalPath.startsWith(rootDir.canonicalPath))
    }

    @Test
    fun client_authenticate_validCredentials_succeeds() = runBlocking {
        val config = FtpServerConfig(
            port = testPort,
            username = "admin",
            password = "secretPassword",
            isAnonymousAllowed = false,
            rootDirectoryPath = rootDir.absolutePath
        )
        server = ArcaFtpServer(config)
        server.start()

        val socket = Socket("127.0.0.1", testPort)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

        fun readLine(): String = reader.readLine()
        fun send(cmd: String) {
            writer.write("$cmd\r\n")
            writer.flush()
        }

        val welcome = readLine()
        assertTrue("Welcome banner must be 220", welcome.startsWith("220"))

        // 1. Wrong password
        send("USER admin")
        val userResp = readLine()
        assertTrue("USER response must be 331", userResp.startsWith("331"))

        send("PASS wrongPassword")
        val passFailResp = readLine()
        assertTrue("PASS with wrong password must be 530", passFailResp.startsWith("530"))

        // 2. Correct password
        send("USER admin")
        readLine()
        send("PASS secretPassword")
        val passOkResp = readLine()
        assertTrue("PASS with correct password must be 230", passOkResp.startsWith("230"))

        // 3. PWD command
        send("PWD")
        val pwdResp = readLine()
        assertTrue("PWD response must be 257", pwdResp.startsWith("257"))

        // 4. SYST command
        send("SYST")
        val systResp = readLine()
        assertTrue("SYST response must be 215", systResp.startsWith("215"))

        // 5. QUIT
        send("QUIT")
        val quitResp = readLine()
        assertTrue("QUIT response must be 221", quitResp.startsWith("221"))

        socket.close()
    }

    @Test
    fun client_anonymousLogin_allowedWhenEnabled() = runBlocking {
        val config = FtpServerConfig(
            port = testPort + 1,
            isAnonymousAllowed = true,
            rootDirectoryPath = rootDir.absolutePath
        )
        server = ArcaFtpServer(config)
        server.start()

        val socket = Socket("127.0.0.1", testPort + 1)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

        val welcome = reader.readLine()
        assertTrue(welcome.startsWith("220"))

        writer.write("USER anonymous\r\n")
        writer.flush()
        val userResp = reader.readLine()
        assertTrue("Anonymous USER must immediately be 230", userResp.startsWith("230"))

        writer.write("PWD\r\n")
        writer.flush()
        val pwdResp = reader.readLine()
        assertTrue(pwdResp.startsWith("257"))

        writer.write("QUIT\r\n")
        writer.flush()
        socket.close()
    }

    @Test
    fun readOnlyMode_blocksFileModificationAndDeletion() = runBlocking {
        val config = FtpServerConfig(
            port = testPort + 2,
            isAnonymousAllowed = true,
            isReadOnly = true,
            rootDirectoryPath = rootDir.absolutePath
        )
        server = ArcaFtpServer(config)
        server.start()

        val socket = Socket("127.0.0.1", testPort + 2)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

        reader.readLine() // 220
        writer.write("USER anonymous\r\n")
        writer.flush()
        reader.readLine() // 230

        // Attempt DELE
        writer.write("DELE test_document.txt\r\n")
        writer.flush()
        val deleResp = reader.readLine()
        assertTrue("DELE in read-only mode must be 550 Permission Denied", deleResp.startsWith("550"))
        assertTrue("File must NOT have been deleted", File(rootDir, "test_document.txt").exists())

        // Attempt MKD
        writer.write("MKD NewFolder\r\n")
        writer.flush()
        val mkdResp = reader.readLine()
        assertTrue("MKD in read-only mode must be 550 Permission Denied", mkdResp.startsWith("550"))
        assertFalse("Directory must NOT have been created", File(rootDir, "NewFolder").exists())

        writer.write("QUIT\r\n")
        writer.flush()
        socket.close()
    }

    @Test
    fun busyPort_automaticallySwitchesToNextAvailablePort() = runBlocking {
        val busyPort = testPort + 10
        // Occupy busyPort with an existing ServerSocket
        val blocker = java.net.ServerSocket(busyPort)
        try {
            val config = FtpServerConfig(
                port = busyPort,
                isAnonymousAllowed = true,
                rootDirectoryPath = rootDir.absolutePath
            )
            server = ArcaFtpServer(config)
            val result = server.start()
            assertTrue("Server must start despite primary port being busy", result.isSuccess)
            assertTrue("Server must be running", server.isRunning)
            assertEquals("Must have switched to busyPort + 1", busyPort + 1, server.effectivePort)
        } finally {
            blocker.close()
        }
    }
}
