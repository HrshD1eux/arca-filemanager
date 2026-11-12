package com.Hrshd1eux.filemanager.core.drop.server

import com.Hrshd1eux.filemanager.core.drop.model.DropTransferItem
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Socket
import java.util.zip.ZipInputStream

class ArcaDropServerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var receiveDir: File
    private lateinit var shareDir: File
    private var sharedItems: List<DropTransferItem> = emptyList()
    private var sharedText: String? = null
    private val receivedFiles = mutableListOf<Triple<String, String, Long>>()
    private val receivedTexts = mutableListOf<String>()

    private lateinit var server: ArcaDropServer

    @Before
    fun setUp() {
        receiveDir = tempFolder.newFolder("receive")
        shareDir = tempFolder.newFolder("share")
        server = ArcaDropServer(
            preferredPort = 0,
            pinOverride = "482910",
            receiveDirectoryProvider = { receiveDir },
            itemsProvider = { sharedItems },
            sharedTextProvider = { sharedText },
            ipAddressProvider = { "192.168.1.50" },
            onFileReceived = { name, path, size -> receivedFiles.add(Triple(name, path, size)) },
            onTextReceived = { text -> receivedTexts.add(text) }
        )
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun pinAuthenticationGate_andSessionCookieFlow() {
        // 1. Unauthenticated GET / shows PIN form and no portal payload
        val gateResp = sendRawHttp("GET / HTTP/1.1\r\nHost: 127.0.0.1:${server.effectivePort}\r\nConnection: close\r\n\r\n")
        assertEquals(200, gateResp.statusCode)
        val gateHtml = gateResp.body.toString(Charsets.UTF_8)
        assertTrue(gateHtml.contains("ARCA Drop"))
        assertTrue(gateHtml.contains("Enter the 6-digit PIN shown on your Android device"))

        // 2. Unauthenticated API call returns 401
        val apiUnauth = sendRawHttp("GET /api/status HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        assertEquals(401, apiUnauth.statusCode)

        // 3. Authenticated GET /?pin=482910 sets HttpOnly; SameSite=Strict cookie & light-theme portal with vector SVG logo
        sharedText = "Hello <script>alert(1)</script> & friends"
        val authed = sendRawHttp("GET /?pin=482910 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        assertEquals(200, authed.statusCode)
        val setCookie = authed.headers["set-cookie"] ?: ""
        assertTrue(setCookie.contains("arca_drop_pin=482910"))
        assertTrue(setCookie.contains("HttpOnly"))
        assertTrue(setCookie.contains("SameSite=Strict"))

        val portalHtml = authed.body.toString(Charsets.UTF_8)
        assertTrue("Portal should use light theme background #F8FAFC", portalHtml.contains("#F8FAFC"))
        assertTrue("Shared text must be XSS-escaped", portalHtml.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse("Raw unescaped script tag must not appear", portalHtml.contains("<script>alert(1)</script>"))

        // 4. Subsequent request using Cookie alone succeeds
        val withCookie = sendRawHttp(
            "GET /api/status HTTP/1.1\r\nHost: localhost\r\nCookie: arca_drop_pin=482910\r\nConnection: close\r\n\r\n"
        )
        assertEquals(200, withCookie.statusCode)
        assertTrue(withCookie.body.toString(Charsets.UTF_8).contains("\"running\":true"))

        // 5. Open logo & favicon routes serve original ARCA PNG logo without auth
        val faviconPng = sendRawHttp("GET /favicon.png HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        assertEquals(200, faviconPng.statusCode)
        assertEquals("image/png", faviconPng.headers["content-type"])
        assertTrue(faviconPng.body.size > 1000)
        assertEquals(0x89.toByte(), faviconPng.body[0])
        assertEquals('P'.code.toByte(), faviconPng.body[1])
        assertEquals('N'.code.toByte(), faviconPng.body[2])
        assertEquals('G'.code.toByte(), faviconPng.body[3])

        val faviconIco = sendRawHttp("GET /favicon.ico HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        assertEquals(200, faviconIco.statusCode)
        assertEquals("image/png", faviconIco.headers["content-type"])

        val faviconSvg = sendRawHttp("GET /favicon.svg HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
        assertEquals(200, faviconSvg.statusCode)
        assertTrue(faviconSvg.body.toString(Charsets.UTF_8).contains("<svg"))
    }

    @Test
    fun dnsRebindingHostAndOriginProtection_blocksExternalHosts() {
        val evilHost = sendRawHttp(
            "GET /?pin=482910 HTTP/1.1\r\nHost: attacker.example.com:8765\r\nConnection: close\r\n\r\n"
        )
        assertEquals(403, evilHost.statusCode)

        val evilOptions = sendRawHttp(
            "OPTIONS /upload HTTP/1.1\r\nHost: 192.168.1.50:8765\r\nOrigin: https://evil.com\r\nConnection: close\r\n\r\n"
        )
        assertEquals(403, evilOptions.statusCode)

        val localOptions = sendRawHttp(
            "OPTIONS /upload HTTP/1.1\r\nHost: 192.168.1.50:8765\r\nOrigin: http://192.168.1.50:8765\r\nConnection: close\r\n\r\n"
        )
        assertEquals(204, localOptions.statusCode)
        assertFalse(localOptions.headers["access-control-allow-origin"] == "*")
    }

    @Test
    fun fileRangeDownload_andRecursiveFolderZipStreaming() {
        val subFolder = File(shareDir, "Project/nested").apply { mkdirs() }
        val file1 = File(shareDir, "notes.txt").apply { writeText("0123456789ABCDEF") }
        val file2 = File(subFolder, "readme.md").apply { writeText("Nested folder file") }

        sharedItems = listOf(
            DropTransferItem(
                id = "item-1",
                name = "notes.txt",
                relativePath = "notes.txt",
                mimeType = "text/plain",
                size = file1.length(),
                file = file1
            ),
            DropTransferItem(
                id = "item-2",
                name = "readme.md",
                relativePath = "Project/nested/readme.md",
                mimeType = "text/markdown",
                size = file2.length(),
                file = file2
            )
        )

        // Partial Range request (bytes=4-9 => "456789")
        val partial = sendRawHttp(
            "GET /file/item-1 HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Arca-Drop-Pin: 482910\r\nRange: bytes=4-9\r\nConnection: close\r\n\r\n"
        )
        assertEquals(206, partial.statusCode)
        assertEquals("456789", partial.body.toString(Charsets.UTF_8))

        // Streaming ZIP /download-all preserves relative folder tree
        val zipResp = sendRawHttp(
            "GET /download-all HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Arca-Drop-Pin: 482910\r\nConnection: close\r\n\r\n"
        )
        assertEquals(200, zipResp.statusCode)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipResp.body)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        assertEquals("0123456789ABCDEF", entries["notes.txt"])
        assertEquals("Nested folder file", entries["Project/nested/readme.md"])
    }

    @Test
    fun uploadFileAtomicWithCollisionAndTraversalSanitization_andReceiveText() {
        val payload1 = "First file bytes".toByteArray(Charsets.UTF_8)
        val upload1 = sendRawHttp(
            header = "POST /upload?filename=../../report.txt HTTP/1.1\r\nHost: localhost\r\nX-Arca-Drop-Pin: 482910\r\nContent-Length: ${payload1.size}\r\nConnection: close\r\n\r\n",
            body = payload1
        )
        assertEquals(200, upload1.statusCode)

        val expectedFirst = File(receiveDir, "report.txt")
        assertTrue("Traversal must be stripped so file lands directly in receiveDir", expectedFirst.exists())
        assertArrayEquals(payload1, expectedFirst.readBytes())

        // Duplicate filename should save as report_1.txt without overwriting report.txt
        val payload2 = "Second file bytes".toByteArray(Charsets.UTF_8)
        val upload2 = sendRawHttp(
            header = "POST /upload?filename=report.txt HTTP/1.1\r\nHost: localhost\r\nX-Arca-Drop-Pin: 482910\r\nContent-Length: ${payload2.size}\r\nConnection: close\r\n\r\n",
            body = payload2
        )
        assertEquals(200, upload2.statusCode)
        val expectedSecond = File(receiveDir, "report_1.txt")
        assertTrue(expectedSecond.exists())
        assertArrayEquals(payload2, expectedSecond.readBytes())
        assertArrayEquals(payload1, expectedFirst.readBytes())
        assertEquals(2, receivedFiles.size)

        // Receive text snippet
        val textBody = "Clipboard snippet from browser".toByteArray(Charsets.UTF_8)
        val textResp = sendRawHttp(
            header = "POST /api/text HTTP/1.1\r\nHost: localhost\r\nX-Arca-Drop-Pin: 482910\r\nContent-Length: ${textBody.size}\r\nConnection: close\r\n\r\n",
            body = textBody
        )
        assertEquals(200, textResp.statusCode)
        assertEquals(listOf("Clipboard snippet from browser"), receivedTexts)
    }

    private data class RawHttpResponse(
        val statusCode: Int,
        val headers: Map<String, String>,
        val body: ByteArray
    )

    private fun sendRawHttp(header: String, body: ByteArray = ByteArray(0)): RawHttpResponse {
        Socket("127.0.0.1", server.effectivePort).use { socket ->
            socket.soTimeout = 5000
            val out = socket.getOutputStream()
            out.write(header.toByteArray(Charsets.US_ASCII))
            if (body.isNotEmpty()) out.write(body)
            out.flush()

            val raw = socket.getInputStream().readBytes()
            val sep = findHeaderSeparator(raw)
            val headerBytes = if (sep >= 0) raw.copyOfRange(0, sep) else raw
            val bodyBytes = if (sep >= 0) raw.copyOfRange(sep + 4, raw.size) else ByteArray(0)

            val headerLines = headerBytes.toString(Charsets.US_ASCII).split("\r\n")
            val statusCode = headerLines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: 0
            val headersMap = LinkedHashMap<String, String>()
            for (line in headerLines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim().lowercase()
                    val v = line.substring(colon + 1).trim()
                    headersMap[k] = if (headersMap.containsKey(k)) "${headersMap[k]}; $v" else v
                }
            }
            return RawHttpResponse(statusCode, headersMap, bodyBytes)
        }
    }

    private fun findHeaderSeparator(bytes: ByteArray): Int {
        for (i in 0..(bytes.size - 4)) {
            if (bytes[i] == '\r'.code.toByte() &&
                bytes[i + 1] == '\n'.code.toByte() &&
                bytes[i + 2] == '\r'.code.toByte() &&
                bytes[i + 3] == '\n'.code.toByte()
            ) {
                return i
            }
        }
        return -1
    }
}
