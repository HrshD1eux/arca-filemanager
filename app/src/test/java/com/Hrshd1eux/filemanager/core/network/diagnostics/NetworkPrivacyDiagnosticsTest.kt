package com.Hrshd1eux.filemanager.core.network.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Rigorous Unit & Privacy Test Suite for Version 1.5.11 – 1.5.15:
 * Privacy Diagnostics and Network-Event Visibility.
 */
class NetworkPrivacyDiagnosticsTest {

    private lateinit var diagnostics: NetworkPrivacyDiagnostics

    @Before
    fun setUp() {
        NetworkPrivacyDiagnostics.resetInstance()
        diagnostics = NetworkPrivacyDiagnostics(maxCapacity = 10)
    }

    // =========================================================================
    // 1.5.11 Event Recording & Capacity Management
    // =========================================================================

    @Test
    fun recordEvent_appendsAndEnforcesFifoCapacityLimit() {
        for (i in 1..15) {
            diagnostics.recordEvent(
                connectionId = "conn_$i",
                protocol = "sftp",
                destination = "192.168.1.$i",
                eventType = NetworkPrivacyEventType.CONNECT_SUCCESS
            )
        }

        val events = diagnostics.getRecentEvents(50)
        assertEquals("Capacity must cap at 10 events", 10, events.size)

        // Newest event (conn_15) should be first in recent events list
        assertEquals("conn_15", events.first().connectionId)
        // Oldest retained event should be conn_6 (1..5 evicted)
        assertEquals("conn_6", events.last().connectionId)
    }

    // =========================================================================
    // 1.5.12 Strict Credential Sanitization
    // =========================================================================

    @Test
    fun destinationSanitization_stripsUserAndPlaintextPasswordCredentials() {
        val event = diagnostics.recordEvent(
            connectionId = "test_conn",
            protocol = "ftp",
            destination = "admin:super_secret_password123@192.168.1.100:21",
            eventType = NetworkPrivacyEventType.CONNECT_ATTEMPT
        )

        assertEquals("192.168.1.100:21", event.destination)
        assertFalse("Destination must never leak password", event.destination.contains("super_secret_password123"))
        assertFalse("Destination must never leak username prefix", event.destination.contains("admin:"))
    }

    @Test
    fun outcomeSanitization_redactsPasswordAndSecretSubstrings() {
        val event = diagnostics.recordEvent(
            connectionId = "test_conn",
            protocol = "webdav",
            destination = "cloud.example.com",
            eventType = NetworkPrivacyEventType.CONNECT_FAILURE,
            outcome = "Authentication failed: password=MySecretVaultKey123 and pass=AnotherSecret"
        )

        assertFalse("Outcome must never contain plaintext password", event.outcome.contains("MySecretVaultKey123"))
        assertFalse("Outcome must never contain plaintext pass", event.outcome.contains("AnotherSecret"))
        assertTrue("Outcome must redact password pattern", event.outcome.contains("password=[REDACTED]"))
        assertTrue("Outcome must redact pass pattern", event.outcome.contains("pass=[REDACTED]"))
    }

    // =========================================================================
    // 1.5.13 Security Alert Filtering & Health Analytics
    // =========================================================================

    @Test
    fun getSecurityAlerts_filtersOnlyAlertEvents() {
        diagnostics.recordEvent(
            connectionId = "sftp_1",
            protocol = "SFTP",
            destination = "10.0.0.1",
            eventType = NetworkPrivacyEventType.CONNECT_SUCCESS
        )

        diagnostics.recordEvent(
            connectionId = "sftp_2",
            protocol = "SFTP",
            destination = "10.0.0.2",
            eventType = NetworkPrivacyEventType.SECURITY_ALERT,
            outcome = "Host key mismatch for server 10.0.0.2"
        )

        diagnostics.recordEvent(
            connectionId = "ftps_1",
            protocol = "FTPS",
            destination = "10.0.0.3",
            eventType = NetworkPrivacyEventType.SECURITY_ALERT,
            outcome = "Self-signed TLS certificate untrusted"
        )

        val alerts = diagnostics.getSecurityAlerts()
        assertEquals(2, alerts.size)
        assertTrue(alerts.all { it.eventType == NetworkPrivacyEventType.SECURITY_ALERT })
    }

    @Test
    fun getStats_aggregatesProtocolAndOutcomeCountsCorrectly() {
        diagnostics.recordEvent("1", "SFTP", "host1", NetworkPrivacyEventType.CONNECT_SUCCESS)
        diagnostics.recordEvent("2", "SFTP", "host2", NetworkPrivacyEventType.CONNECT_FAILURE)
        diagnostics.recordEvent("3", "SMB", "host3", NetworkPrivacyEventType.CONNECT_SUCCESS)
        diagnostics.recordEvent("4", "FTPS", "host4", NetworkPrivacyEventType.SECURITY_ALERT)

        val stats = diagnostics.getStats()
        assertEquals(4, stats.totalEvents)
        assertEquals(2, stats.successCount)
        assertEquals(1, stats.failureCount)
        assertEquals(1, stats.alertCount)
        assertEquals(0, stats.unexpectedExternalCount)

        assertEquals(2, stats.protocolCounts["SFTP"])
        assertEquals(1, stats.protocolCounts["SMB"])
        assertEquals(1, stats.protocolCounts["FTPS"])
    }

    // =========================================================================
    // 1.5.14 Export Diagnostics & Local Privacy Invariants
    // =========================================================================

    @Test
    fun exportDiagnosticsText_generatesCleanSanitizedSummary() {
        diagnostics.recordEvent("1", "SFTP", "192.168.1.5", NetworkPrivacyEventType.CONNECT_SUCCESS)
        val text = diagnostics.exportDiagnosticsText()

        assertTrue(text.contains("=== ARCA NETWORK PRIVACY DIAGNOSTICS LOG ==="))
        assertTrue(text.contains("SFTP -> 192.168.1.5"))
        assertTrue(text.contains("=== END OF LOG ==="))

        assertFalse(text.contains("password="))
        assertFalse(text.contains("private_key"))
    }

    @Test
    fun clear_removesAllEventsAndResetsFlow() {
        diagnostics.recordEvent("1", "SFTP", "192.168.1.5", NetworkPrivacyEventType.CONNECT_SUCCESS)
        assertEquals(1, diagnostics.getRecentEvents().size)

        diagnostics.clear()
        assertEquals(0, diagnostics.getRecentEvents().size)
        assertEquals(0, diagnostics.eventsFlow.value.size)
        assertEquals(0, diagnostics.getStats().totalEvents)
    }
}
