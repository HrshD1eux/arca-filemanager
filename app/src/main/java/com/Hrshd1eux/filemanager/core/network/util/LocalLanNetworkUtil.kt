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
        "dummy", "radio", "ims", "epdg", "sit", "ip6", "bond",
        "wg", "vpn", "tailscale", "ts", "adguard", "ipsec"
    )

    private val primaryInterfacePrefixes = listOf(
        "wlan", "wifi", "ap", "softap", "swlan", "ra", "wl", "tiwlan", "eth", "wigig"
    )

    private val secondaryInterfacePrefixes = listOf(
        "rndis", "usb"
    )

    fun isExcludedInterface(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase()
        return excludedInterfacePrefixes.any { lower.startsWith(it) }
    }

    fun isPrimaryLocalInterface(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase()
        return primaryInterfacePrefixes.any { lower.startsWith(it) }
    }

    fun isSecondaryLocalInterface(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase()
        return secondaryInterfacePrefixes.any { lower.startsWith(it) }
    }

    fun isPreferredLocalInterface(interfaceName: String): Boolean {
        return isPrimaryLocalInterface(interfaceName) || isSecondaryLocalInterface(interfaceName)
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
     * Checks whether an IPv4 address belongs to private RFC 1918 space,
     * RFC 6598 carrier-grade NAT / shared address space (100.64.0.0/10 - commonly used by mobile hotspots),
     * or RFC 3927 link-local (169.254.0.0/16).
     */
    fun isPrivateOrSharedLanIpv4(host: String): Boolean {
        if (isPrivateRfc1918Ipv4(host)) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        val c = parts[2].toIntOrNull() ?: return false
        val d = parts[3].toIntOrNull() ?: return false
        if (a !in 0..255 || b !in 0..255 || c !in 0..255 || d !in 0..255) return false

        return when {
            // RFC 6598 Shared Address Space / Carrier Grade NAT (mobile hotspots)
            a == 100 && b in 64..127 -> true
            // RFC 3927 Link-Local IPv4
            a == 169 && b == 254 -> true
            else -> false
        }
    }

    /**
     * Validates whether a host address belongs to local private IPv4, loopback, private IPv6,
     * or local LAN domain suffixes (mDNS, home routers).
     */
    fun isPrivateOrLocalAddress(host: String): Boolean {
        val lower = host.lowercase().removePrefix("[").removeSuffix("]")
        if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1") return true
        if (isPrivateOrSharedLanIpv4(lower)) return true
        // IPv6 Link-Local (fe80::) or Unique Local (fc00:: / fd00::)
        if (lower.startsWith("fe80:") || lower.startsWith("fc") || lower.startsWith("fd")) return true
        // Local router domain names & mDNS
        if (lower.endsWith(".local") || lower.endsWith(".lan") || lower.endsWith(".home") ||
            lower.endsWith(".home.arpa") || lower.endsWith(".localdomain") || lower.endsWith(".wifi")
        ) return true
        return false
    }

    /**
     * Selects the best candidate IPv4 address from pre-inspected interface descriptors.
     * Tiered selection guarantees Wi-Fi / Hotspot interfaces are prioritized over USB tethering.
     */
    internal fun selectBestLanIp(candidates: List<Pair<String, String>>): String? {
        val usable = candidates.filter { (iface, ip) ->
            !isExcludedInterface(iface) &&
                ip.isNotBlank() &&
                !ip.startsWith("127.") &&
                !ip.startsWith("169.254.")
        }

        // Pass 1: Primary Wi-Fi / Hotspot / Ethernet interface with RFC 1918 or RFC 6598 IP
        usable.firstOrNull { (iface, ip) ->
            isPrimaryLocalInterface(iface) && isPrivateOrSharedLanIpv4(ip)
        }?.let { return it.second }

        // Pass 2: Secondary interface (USB/RNDIS) with RFC 1918 or RFC 6598 IP
        usable.firstOrNull { (iface, ip) ->
            isSecondaryLocalInterface(iface) && isPrivateOrSharedLanIpv4(ip)
        }?.let { return it.second }

        // Pass 3: Any non-excluded interface with private/shared LAN IP
        usable.firstOrNull { (_, ip) ->
            isPrivateOrSharedLanIpv4(ip)
        }?.let { return it.second }

        // Pass 4: Primary interface with any active IPv4
        usable.firstOrNull { (iface, _) ->
            isPrimaryLocalInterface(iface)
        }?.let { return it.second }

        // Pass 5: Secondary interface with any active IPv4
        usable.firstOrNull { (iface, _) ->
            isSecondaryLocalInterface(iface)
        }?.let { return it.second }

        // Pass 6: Fallback to any remaining active IPv4
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
                    val ifaceName = iface.name ?: continue
                    if (isExcludedInterface(ifaceName)) continue
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
