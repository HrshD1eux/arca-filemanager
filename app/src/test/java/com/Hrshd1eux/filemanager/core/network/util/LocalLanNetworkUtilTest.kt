package com.Hrshd1eux.filemanager.core.network.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLanNetworkUtilTest {

    @Test
    fun isExcludedInterface_blocksCellularAndVpnPrefixes() {
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("rmnet_data0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("ccmni1"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("tun0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("p2p-wlan0-0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("v4-rmnet0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("dummy0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("radio0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("ims0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("wg0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("vpn0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("tailscale0"))
        assertTrue(LocalLanNetworkUtil.isExcludedInterface("adguard"))

        assertFalse(LocalLanNetworkUtil.isExcludedInterface("wlan0"))
        assertFalse(LocalLanNetworkUtil.isExcludedInterface("ap0"))
        assertFalse(LocalLanNetworkUtil.isExcludedInterface("swlan0"))
        assertFalse(LocalLanNetworkUtil.isExcludedInterface("eth0"))
        assertFalse(LocalLanNetworkUtil.isExcludedInterface("rndis0"))
    }

    @Test
    fun isPrivateRfc1918Ipv4_validatesStandardLanRanges() {
        assertTrue(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("192.168.1.42"))
        assertTrue(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("10.0.2.15"))
        assertTrue(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("172.16.0.1"))
        assertTrue(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("172.31.255.254"))

        assertFalse(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("172.15.255.255"))
        assertFalse(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("172.32.0.1"))
        assertFalse(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("8.8.8.8"))
        assertFalse(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("100.64.0.1"))
        assertFalse(LocalLanNetworkUtil.isPrivateRfc1918Ipv4("not-an-ip"))
    }

    @Test
    fun isPrivateOrLocalAddress_acceptsLoopbackPrivateIpv4AndLinkLocalIpv6() {
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("localhost"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("127.0.0.1"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("::1"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("[::1]"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("192.168.1.100"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("10.0.0.1"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("fe80::1ff:fe23:4567:890a"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("[fe80::1]"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("fc00::1"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("fd12:3456:789a::1"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("100.64.0.1"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("169.254.1.1"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("arca.local"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("router.lan"))
        assertTrue(LocalLanNetworkUtil.isPrivateOrLocalAddress("phone.home"))

        assertFalse(LocalLanNetworkUtil.isPrivateOrLocalAddress("8.8.8.8"))
        assertFalse(LocalLanNetworkUtil.isPrivateOrLocalAddress("1.1.1.1"))
        assertFalse(LocalLanNetworkUtil.isPrivateOrLocalAddress("2001:4860:4860::8888"))
        assertFalse(LocalLanNetworkUtil.isPrivateOrLocalAddress("attacker.com"))
    }

    @Test
    fun selectBestLanIp_prefersWlanHotspotRfc1918OverCellularAndPublicIps() {
        val candidates = listOf(
            "rmnet_data0" to "100.70.12.5",
            "tun0" to "10.8.0.2",
            "dummy0" to "203.0.113.9",
            "eth0" to "172.20.10.4",
            "wlan0" to "192.168.43.1"
        )
        assertEquals("172.20.10.4", LocalLanNetworkUtil.selectBestLanIp(candidates))
    }

    @Test
    fun selectBestLanIp_prefersWlanOverUsbTetheringRndis() {
        val candidates = listOf(
            "rndis0" to "192.168.42.129",
            "wlan0" to "192.168.1.100"
        )
        assertEquals("192.168.1.100", LocalLanNetworkUtil.selectBestLanIp(candidates))
    }

    @Test
    fun selectBestLanIp_excludesLoopbackAndLinkLocalAndCellularOnly() {
        val onlyCellularAndLoopback = listOf(
            "lo" to "127.0.0.1",
            "wlan0" to "169.254.12.34",
            "rmnet0" to "10.120.4.8"
        )
        assertNull(LocalLanNetworkUtil.selectBestLanIp(onlyCellularAndLoopback))
    }
}
