package com.Hrshd1eux.filemanager.core.network.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavProtocolHelperTest {

    @Test
    fun parseWebDavUrl_fullNextcloudUrl_parsedAccurately() {
        val input = "https://cloud.example.com/remote.php/dav/files/alice/"
        val parsed = WebDavProtocolHelper.parseWebDavUrl(input)

        assertNotNull(parsed)
        assertEquals("cloud.example.com", parsed?.host)
        assertEquals(443, parsed?.port)
        assertTrue(parsed?.isHttps == true)
        assertEquals("/remote.php/dav/files/alice/", parsed?.basePath)
        assertNull(parsed?.username)
        assertNull(parsed?.password)
    }

    @Test
    fun parseWebDavUrl_httpUrlWithCustomPort_parsedAccurately() {
        val input = "http://192.168.1.100:8080/webdav/share"
        val parsed = WebDavProtocolHelper.parseWebDavUrl(input)

        assertNotNull(parsed)
        assertEquals("192.168.1.100", parsed?.host)
        assertEquals(8080, parsed?.port)
        assertFalse(parsed?.isHttps == true)
        assertEquals("/webdav/share", parsed?.basePath)
    }

    @Test
    fun parseWebDavUrl_customSchemeWebdavs_mapsToHttps() {
        val input = "webdavs://synology.local:5006/home/data"
        val parsed = WebDavProtocolHelper.parseWebDavUrl(input)

        assertNotNull(parsed)
        assertEquals("synology.local", parsed?.host)
        assertEquals(5006, parsed?.port)
        assertTrue(parsed?.isHttps == true)
        assertEquals("/home/data", parsed?.basePath)
    }

    @Test
    fun parseWebDavUrl_customSchemeWebdav_mapsToHttp() {
        val input = "webdav://192.168.1.20:5005/public"
        val parsed = WebDavProtocolHelper.parseWebDavUrl(input)

        assertNotNull(parsed)
        assertEquals("192.168.1.20", parsed?.host)
        assertEquals(5005, parsed?.port)
        assertFalse(parsed?.isHttps == true)
        assertEquals("/public", parsed?.basePath)
    }

    @Test
    fun parseWebDavUrl_embeddedCredentials_extractedProperly() {
        val input = "https://admin:mySecretPass@vault.example.com:443/dav"
        val parsed = WebDavProtocolHelper.parseWebDavUrl(input)

        assertNotNull(parsed)
        assertEquals("vault.example.com", parsed?.host)
        assertEquals(443, parsed?.port)
        assertTrue(parsed?.isHttps == true)
        assertEquals("admin", parsed?.username)
        assertEquals("mySecretPass", parsed?.password)
        assertEquals("/dav", parsed?.basePath)
    }

    @Test
    fun parseWebDavUrl_plainHostWithoutScheme_defaultsToHttps() {
        val input = "cloud.internal.org"
        val parsed = WebDavProtocolHelper.parseWebDavUrl(input)

        assertNotNull(parsed)
        assertEquals("cloud.internal.org", parsed?.host)
        assertEquals(443, parsed?.port)
        assertTrue(parsed?.isHttps == true)
        assertEquals("/", parsed?.basePath)
    }

    @Test
    fun parseWebDavUrl_emptyInput_returnsNull() {
        assertNull(WebDavProtocolHelper.parseWebDavUrl(""))
        assertNull(WebDavProtocolHelper.parseWebDavUrl("   "))
    }

    @Test
    fun buildPropfindXml_containsRfc4918Properties() {
        val xml = WebDavProtocolHelper.buildPropfindXml()
        assertTrue(xml.contains("<D:propfind xmlns:D=\"DAV:\">"))
        assertTrue(xml.contains("<D:displayname/>"))
        assertTrue(xml.contains("<D:getcontentlength/>"))
        assertTrue(xml.contains("<D:getcontenttype/>"))
        assertTrue(xml.contains("<D:resourcetype/>"))
        assertTrue(xml.contains("<D:getlastmodified/>"))
    }

    @Test
    fun buildBasicAuthHeader_createsStandardBase64Header() {
        val header = WebDavProtocolHelper.buildBasicAuthHeader("aladdin", "open sesame")
        // "aladdin:open sesame" base64 is "YWxhZGRpbjpvcGVuIHNlc2FtZQ=="
        assertEquals("Basic YWxhZGRpbjpvcGVuIHNlc2FtZQ==", header)
    }

    @Test
    fun buildBearerAuthHeader_createsStandardBearerHeader() {
        val header = WebDavProtocolHelper.buildBearerAuthHeader("sec_token_987654321")
        assertEquals("Bearer sec_token_987654321", header)
    }

    @Test
    fun parseMultiStatusXml_extractsCollectionsAndFiles() {
        val multiStatusXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
                <D:response>
                    <D:href>/remote.php/dav/files/alice/</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:displayname>alice</D:displayname>
                            <D:resourcetype>
                                <D:collection/>
                            </D:resourcetype>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
                <D:response>
                    <D:href>/remote.php/dav/files/alice/Photos/</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:displayname>Photos</D:displayname>
                            <D:resourcetype>
                                <D:collection/>
                            </D:resourcetype>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
                <D:response>
                    <D:href>/remote.php/dav/files/alice/notes.txt</D:href>
                    <D:propstat>
                        <D:prop>
                            <D:displayname>notes.txt</D:displayname>
                            <D:getcontentlength>1024</D:getcontentlength>
                            <D:getcontenttype>text/plain</D:getcontenttype>
                            <D:resourcetype/>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
            </D:multistatus>
        """.trimIndent()

        val resources = WebDavProtocolHelper.parseMultiStatusXml(multiStatusXml)
        assertEquals(3, resources.size)

        // Root collection
        assertEquals("/remote.php/dav/files/alice/", resources[0].href)
        assertEquals("alice", resources[0].name)
        assertTrue(resources[0].isDirectory)

        // Sub collection
        assertEquals("/remote.php/dav/files/alice/Photos/", resources[1].href)
        assertEquals("Photos", resources[1].name)
        assertTrue(resources[1].isDirectory)

        // File
        assertEquals("/remote.php/dav/files/alice/notes.txt", resources[2].href)
        assertEquals("notes.txt", resources[2].name)
        assertFalse(resources[2].isDirectory)
        assertEquals(1024L, resources[2].contentLength)
        assertEquals("text/plain", resources[2].contentType)
    }

    @Test
    fun presets_configuredCorrectly() {
        val presets = WebDavProtocolHelper.PRESETS
        assertEquals(3, presets.size)

        val nextcloud = presets.first { it.displayName.startsWith("Nextcloud") }
        assertEquals("/remote.php/dav/files/", nextcloud.defaultPath)
        assertEquals(443, nextcloud.defaultPort)

        val synology = presets.first { it.displayName.startsWith("Synology") }
        assertEquals(5006, synology.defaultPort)
    }
}
