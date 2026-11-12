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
    fun selectBestLanIp_excludesLoopbackAndLinkLocalAndCellularOnly() {
        val onlyCellularAndLoopback = listOf(
            "lo" to "127.0.0.1",
            "wlan0" to "169.254.12.34",
            "rmnet0" to "10.120.4.8"
        )
        assertNull(LocalLanNetworkUtil.selectBestLanIp(onlyCellularAndLoopback))
    }
}
