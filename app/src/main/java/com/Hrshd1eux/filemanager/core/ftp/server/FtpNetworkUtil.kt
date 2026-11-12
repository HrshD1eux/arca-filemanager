package com.Hrshd1eux.filemanager.core.ftp.server

import com.Hrshd1eux.filemanager.core.network.util.LocalLanNetworkUtil

/**
 * Utility for network address detection and FTP URL formatting.
 */
object FtpNetworkUtil {

    /**
     * Finds the primary local IPv4 address of the device on Wi-Fi or Mobile Hotspot.
     * Never exposes loopback (127.0.0.1) unless no network interface is active.
     */
    fun getLocalIpAddress(): String? = LocalLanNetworkUtil.getLocalIpAddress()

    /**
     * Determines whether the device has an active Wi-Fi or Hotspot IP address available.
     */
    fun isNetworkAvailable(): Boolean = getLocalIpAddress() != null

    /**
     * Formats an FTP URL from the IP and Port.
     */
    fun formatFtpUrl(ip: String?, port: Int): String {
        val safeIp = ip ?: "127.0.0.1"
        return "ftp://$safeIp:$port/"
    }
}
