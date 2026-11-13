package com.Hrshd1eux.filemanager.core.network.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Shared local LAN IPv4 discovery utility without requiring ACCESS_NETWORK_STATE or ACCESS_WIFI_STATE.
 * Explicitly excludes cellular/VPN/tunneled interfaces and prefers private RFC 1918 Wi-Fi/Hotspot interfaces.
 */
object LocalLanNetworkUtil {

    private val excludedInterfacePrefixes = listOf(
        "rmnet", "ccmni", "pdp", "clat", "tun", "tap", "ppp", "p2p", "wwan", "v4-",
        "dummy", "radio", "ims", "epdg", "sit", "ip6", "bond"
    )

    private val preferredInterfacePrefixes = listOf(
        "wlan", "wifi", "ap", "softap", "swlan", "ra", "wl", "tiwlan", "eth", "rndis", "usb", "wigig"
    )

    fun isExcludedInterface(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase()
        return excludedInterfacePrefixes.any { lower.startsWith(it) }
    }

    fun isPreferredLocalInterface(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase()
        return preferredInterfacePrefixes.any { lower.startsWith(it) }
    }

    /**
     * Checks whether an IPv4 address belongs to RFC 1918 private LAN space:
     * 10.0.0.0/8, 172.16.0.0/12, or 192.168.0.0/16.
     */
    fun isPrivateRfc1918Ipv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        val c = parts[2].toIntOrNull() ?: return false
        val d = parts[3].toIntOrNull() ?: return false
        if (a !in 0..255 || b !in 0..255 || c !in 0..255 || d !in 0..255) return false

        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            else -> false
        }
    }

    /**
     * Validates whether a host address belongs to local private IPv4, loopback, or private IPv6.
     */
    fun isPrivateOrLocalAddress(host: String): Boolean {
        val lower = host.lowercase().removePrefix("[").removeSuffix("]")
        if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1") return true
        if (isPrivateRfc1918Ipv4(lower)) return true
        // IPv6 Link-Local (fe80::) or Unique Local (fc00:: / fd00::)
        if (lower.startsWith("fe80:") || lower.startsWith("fc") || lower.startsWith("fd")) return true
        return false
    }

    /**
     * Selects the best candidate IPv4 address from pre-inspected interface descriptors.
     * Separated for deterministic unit testing without mocking Java NetworkInterface.
     */
    internal fun selectBestLanIp(candidates: List<Pair<String, String>>): String? {
        val usable = candidates.filter { (iface, ip) ->
            !isExcludedInterface(iface) &&
                ip.isNotBlank() &&
                !ip.startsWith("127.") &&
                !ip.startsWith("169.254.")
        }

        // Pass 1: Preferred Wi-Fi / Hotspot / USB tether interface with RFC 1918 IP
        usable.firstOrNull { (iface, ip) ->
            isPreferredLocalInterface(iface) && isPrivateRfc1918Ipv4(ip)
        }?.let { return it.second }

        // Pass 2: Any non-excluded interface with RFC 1918 IP
        usable.firstOrNull { (_, ip) ->
            isPrivateRfc1918Ipv4(ip)
        }?.let { return it.second }

        // Pass 3: Preferred interface with any non-loopback IPv4
        usable.firstOrNull { (iface, _) ->
            isPreferredLocalInterface(iface)
        }?.let { return it.second }

        // Pass 4: Fallback to any remaining active IPv4
        return usable.firstOrNull()?.second
    }

    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val candidates = mutableListOf<Pair<String, String>>()
            for (iface in interfaces) {
                try {
                    val isLoopback = runCatching { iface.isLoopback }.getOrDefault(false)
                    if (isLoopback) continue
                    val isUp = runCatching { iface.isUp }.getOrDefault(true)
                    if (!isUp) continue
                    val ifaceName = iface.name ?: continue
                    for (addr in Collections.list(iface.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress
                            if (!host.isNullOrBlank()) {
                                candidates.add(ifaceName to host)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Skip problematic individual interface and continue inspecting others
                }
            }
            selectBestLanIp(candidates)
        } catch (_: Exception) {
            null
        }
    }
}
