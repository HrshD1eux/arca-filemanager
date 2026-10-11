package com.Hrshd1eux.filemanager.core.network.util

import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Universal WebDAV (RFC 4918 / RFC 2518) Protocol Helper.
 *
 * ARCHITECTURAL PHILOSOPHY:
 * The app remains 100% protocol-based, NOT account-service-based.
 * We do not embed proprietary vendor cloud SDKs or OAuth tracking.
 * Any standards-compliant WebDAV endpoint (Nextcloud, ownCloud, Synology,
 * QNAP, Apache mod_dav, Nginx, mailbox.org) is accessed through open standards.
 */
object WebDavProtocolHelper {

    /**
     * Parsed components from a user-supplied WebDAV URL or endpoint string.
     */
    data class ParsedWebDavEndpoint(
        val host: String,
        val port: Int,
        val isHttps: Boolean,
        val basePath: String,
        val username: String? = null,
        val password: String? = null
    )

    /**
     * WebDAV Resource metadata parsed from standard PROPFIND 207 Multi-Status XML.
     */
    data class WebDavResource(
        val href: String,
        val name: String,
        val isDirectory: Boolean,
        val contentLength: Long,
        val lastModified: Long,
        val contentType: String?
    )

    /**
     * Common server presets for standard WebDAV endpoints.
     */
    data class WebDavPreset(
        val displayName: String,
        val defaultPath: String,
        val defaultPort: Int,
        val description: String
    )

    val PRESETS = listOf(
        WebDavPreset(
            displayName = "Nextcloud / ownCloud",
            defaultPath = "/remote.php/dav/files/",
            defaultPort = 443,
            description = "Standard Nextcloud/ownCloud WebDAV path. Append your username."
        ),
        WebDavPreset(
            displayName = "Synology WebDAV",
            defaultPath = "/",
            defaultPort = 5006,
            description = "Synology NAS WebDAV server default secure port is 5006."
        ),
        WebDavPreset(
            displayName = "Standard RFC 4918",
            defaultPath = "/",
            defaultPort = 443,
            description = "Generic WebDAV collection root for Apache mod_dav / Nginx / TrueNAS."
        )
    )

    /**
     * Parses an input string (full URL, IP, or host) into structured WebDAV endpoint components.
     * Supports:
     * - `https://cloud.example.com:443/remote.php/dav/files/user/`
     * - `http://192.168.1.50:8080/webdav`
     * - `webdav://nas.local/share`
     * - `cloud.example.com/dav`
     * - `192.168.1.10`
     */
    fun parseWebDavUrl(rawInput: String): ParsedWebDavEndpoint? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("webdav://", ignoreCase = true) -> "http://" + trimmed.substring(9)
            trimmed.startsWith("webdavs://", ignoreCase = true) -> "https://" + trimmed.substring(10)
            else -> "https://$trimmed" // Default to secure HTTPS
        }

        return try {
            val uri = URI(withScheme)
            val host = uri.host ?: return null
            val isHttps = uri.scheme.equals("https", ignoreCase = true)
            val effectivePort = if (uri.port > 0) uri.port else if (isHttps) 443 else 80

            var user: String? = null
            var pass: String? = null
            val userInfo = uri.userInfo
            if (!userInfo.isNullOrEmpty()) {
                val parts = userInfo.split(":", limit = 2)
                user = parts.getOrNull(0)
                pass = parts.getOrNull(1)
            }

            val rawPath = uri.rawPath ?: "/"
            val normalizedPath = when {
                rawPath.isEmpty() -> "/"
                rawPath.startsWith("/") -> rawPath
                else -> "/$rawPath"
            }

            ParsedWebDavEndpoint(
                host = host,
                port = effectivePort,
                isHttps = isHttps,
                basePath = normalizedPath,
                username = user,
                password = pass
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds standard PROPFIND XML request payload per RFC 4918 §9.1.
     */
    fun buildPropfindXml(): String {
        return """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:displayname/>
                <D:getcontentlength/>
                <D:getcontenttype/>
                <D:resourcetype/>
                <D:getlastmodified/>
              </D:prop>
            </D:propfind>
        """.trimIndent()
    }

    /**
     * Builds HTTP Basic authorization header per RFC 7617.
     */
    fun buildBasicAuthHeader(user: String, pass: String): String {
        val credentials = "$user:$pass"
        val encoded = java.util.Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    /**
     * Builds HTTP Bearer authorization header per RFC 6750.
     */
    fun buildBearerAuthHeader(token: String): String {
        return "Bearer ${token.trim()}"
    }

    /**
     * Parses a WebDAV 207 Multi-Status XML response into a list of WebDavResource entries.
     */
    fun parseMultiStatusXml(xml: String): List<WebDavResource> {
        val results = mutableListOf<WebDavResource>()
        if (xml.isBlank()) return results

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            val responseList = doc.getElementsByTagName("D:response").let {
                if (it.length > 0) it else doc.getElementsByTagName("response")
            }

            for (i in 0 until responseList.length) {
                val elem = responseList.item(i) as? Element ?: continue
                val href = getChildText(elem, "D:href") ?: getChildText(elem, "href") ?: ""
                if (href.isBlank()) continue

                val displayName = getChildText(elem, "D:displayname") ?: getChildText(elem, "displayname")
                val cleanHref = href.trimEnd('/')
                val name = displayName ?: cleanHref.substringAfterLast('/').ifEmpty { "/" }

                val isCollection = elem.getElementsByTagName("D:collection").length > 0 ||
                        elem.getElementsByTagName("collection").length > 0

                val contentLengthStr = getChildText(elem, "D:getcontentlength") ?: getChildText(elem, "getcontentlength")
                val contentLength = contentLengthStr?.toLongOrNull() ?: 0L

                val contentType = getChildText(elem, "D:getcontenttype") ?: getChildText(elem, "getcontenttype")
                val lastModifiedStr = getChildText(elem, "D:getlastmodified") ?: getChildText(elem, "getlastmodified")
                val lastModified = if (lastModifiedStr != null) parseHttpDate(lastModifiedStr) else 0L

                results.add(
                    WebDavResource(
                        href = href,
                        name = name,
                        isDirectory = isCollection,
                        contentLength = contentLength,
                        lastModified = lastModified,
                        contentType = contentType
                    )
                )
            }
        } catch (_: Exception) {
            // Fallback for truncated or non-XML responses
        }

        return results
    }

    private fun getChildText(parent: Element, tagName: String): String? {
        val list = parent.getElementsByTagName(tagName)
        if (list.length > 0) {
            return list.item(0).textContent?.trim()
        }
        return null
    }

    private fun parseHttpDate(dateString: String): Long {
        return try {
            val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            format.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
