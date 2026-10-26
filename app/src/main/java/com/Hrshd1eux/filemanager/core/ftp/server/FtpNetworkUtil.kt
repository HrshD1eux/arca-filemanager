package com.Hrshd1eux.filemanager.core.ftp.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Utility for network address detection and FTP URL formatting.
 */
object FtpNetworkUtil {

    /**
     * Finds the primary local IPv4 address of the device on Wi-Fi or Mobile Hotspot.
     * Never exposes loopback (127.0.0.1) unless no network interface is active.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Priority 1: Wi-Fi / Hotspot interfaces (wlan, ap, swlan, rndis)
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("rndis")) {
                    for (addr in Collections.list(iface.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress
                            if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                                return host
                            }
                        }
                    }
                }
            }

            // Priority 2: Ethernet or any other active non-loopback IPv4 interface
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in Collections.list(iface.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignored, fallback below
        }
        return null
    }

    /**
     * Determines whether the device has an active Wi-Fi or Hotspot IP address available.
     */
    fun isNetworkAvailable(): Boolean {
        return getLocalIpAddress() != null
    }

    /**
     * Formats an FTP URL from the IP and Port.
     */
    fun formatFtpUrl(ip: String?, port: Int): String {
        val safeIp = ip ?: "127.0.0.1"
        return "ftp://$safeIp:$port/"
    }
}
