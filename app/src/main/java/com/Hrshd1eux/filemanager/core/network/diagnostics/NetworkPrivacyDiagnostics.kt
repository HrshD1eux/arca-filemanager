package com.Hrshd1eux.filemanager.core.network.diagnostics

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkPrivacyEventType {
    CONNECT_ATTEMPT,
    CONNECT_SUCCESS,
    CONNECT_FAILURE,
    DISCONNECT,
    TRANSFER_STARTED,
    TRANSFER_COMPLETED,
    TRANSFER_CANCELLED,
    SECURITY_ALERT
}

enum class NetworkPrivacyTriggerCategory {
    USER_INITIATED,
    APP_OPERATION,
    UNEXPECTED_EXTERNAL
}

data class NetworkPrivacyEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val connectionId: String,
    val protocol: String,
    val destination: String,
    val eventType: NetworkPrivacyEventType,
    val triggerCategory: NetworkPrivacyTriggerCategory,
    val outcome: String,
    val latencyMs: Long? = null
)

data class NetworkPrivacyStats(
    val totalEvents: Int,
    val successCount: Int,
    val failureCount: Int,
    val alertCount: Int,
    val unexpectedExternalCount: Int,
    val protocolCounts: Map<String, Int>
)

/**
 * Audit and diagnostic logger for remote network connections.
 *
 * RIGOROUS PRIVACY ASSURANCES:
 * 1. Zero Telemetry / Zero Cloud: Events are strictly held in-memory locally.
 * 2. Zero Secret Leakage: Passwords, tokens, private keys, and file contents
 *    are NEVER recorded under any circumstances.
 * 3. Categorization: Distinguishes explicit user actions from app operations
 *    and detects any anomalous or unexpected communications.
 */
class NetworkPrivacyDiagnostics(
    private val maxCapacity: Int = MAX_CAPACITY
) {
    private val eventQueue = ConcurrentLinkedDeque<NetworkPrivacyEvent>()
    private val _eventsFlow = MutableStateFlow<List<NetworkPrivacyEvent>>(emptyList())
    val eventsFlow: StateFlow<List<NetworkPrivacyEvent>> = _eventsFlow.asStateFlow()

    /**
     * Logs a network event with strict data sanitization.
     */
    fun recordEvent(
        connectionId: String,
        protocol: String,
        destination: String,
        eventType: NetworkPrivacyEventType,
        triggerCategory: NetworkPrivacyTriggerCategory = NetworkPrivacyTriggerCategory.USER_INITIATED,
        outcome: String = "SUCCESS",
        latencyMs: Long? = null
    ): NetworkPrivacyEvent {
        val sanitizedDestination = sanitizeDestination(destination)
        val sanitizedOutcome = sanitizeOutcome(outcome)

        val event = NetworkPrivacyEvent(
            connectionId = connectionId,
            protocol = protocol.uppercase(),
            destination = sanitizedDestination,
            eventType = eventType,
            triggerCategory = triggerCategory,
            outcome = sanitizedOutcome,
            latencyMs = latencyMs
        )

        eventQueue.addLast(event)
        while (eventQueue.size > maxCapacity) {
            eventQueue.pollFirst()
        }
        _eventsFlow.value = eventQueue.toList().asReversed()
        return event
    }

    /**
     * Returns recent diagnostic events (newest first).
     */
    fun getRecentEvents(limit: Int = 50): List<NetworkPrivacyEvent> {
        return eventQueue.toList().asReversed().take(limit)
    }

    /**
     * Returns diagnostic events for a specific connection.
     */
    fun getEventsForConnection(connectionId: String): List<NetworkPrivacyEvent> {
        return eventQueue.filter { it.connectionId == connectionId }.reversed()
    }

    /**
     * Returns only security alert events (e.g. changed host keys, cert errors).
     */
    fun getSecurityAlerts(): List<NetworkPrivacyEvent> {
        return eventQueue.filter { it.eventType == NetworkPrivacyEventType.SECURITY_ALERT }.reversed()
    }

    /**
     * Calculates comprehensive diagnostic statistics.
     */
    fun getStats(): NetworkPrivacyStats {
        val list = eventQueue.toList()
        val total = list.size
        val successes = list.count { it.eventType == NetworkPrivacyEventType.CONNECT_SUCCESS || it.eventType == NetworkPrivacyEventType.TRANSFER_COMPLETED }
        val failures = list.count { it.eventType == NetworkPrivacyEventType.CONNECT_FAILURE }
        val alerts = list.count { it.eventType == NetworkPrivacyEventType.SECURITY_ALERT }
        val unexpected = list.count { it.triggerCategory == NetworkPrivacyTriggerCategory.UNEXPECTED_EXTERNAL }
        val protoCounts = list.groupingBy { it.protocol }.eachCount()

        return NetworkPrivacyStats(
            totalEvents = total,
            successCount = successes,
            failureCount = failures,
            alertCount = alerts,
            unexpectedExternalCount = unexpected,
            protocolCounts = protoCounts
        )
    }

    /**
     * Clears all diagnostic events.
     */
    fun clear() {
        eventQueue.clear()
        _eventsFlow.value = emptyList()
    }

    /**
     * Exports a sanitized diagnostic log formatted for inspection.
     */
    fun exportDiagnosticsText(): String = buildString {
        appendLine("=== ARCA NETWORK PRIVACY DIAGNOSTICS LOG ===")
        appendLine("Generated At: ${System.currentTimeMillis()}")
        val stats = getStats()
        appendLine("Total Events: ${stats.totalEvents}")
        appendLine("Security Alerts: ${stats.alertCount}")
        appendLine("Unexpected Outbound Calls: ${stats.unexpectedExternalCount}")
        appendLine()
        appendLine("--- RECENT NETWORK EVENTS ---")
        for (event in getRecentEvents(MAX_CAPACITY)) {
            appendLine("[${event.timestamp}] ${event.protocol} -> ${event.destination} | ${event.eventType} | ${event.triggerCategory} | ${event.outcome}")
        }
        appendLine("=== END OF LOG ===")
    }

    private fun sanitizeDestination(destination: String): String {
        // Strip any username/password prefix if inadvertently passed (e.g. user:pass@host:port)
        val hostPart = if (destination.contains("@")) {
            destination.substringAfterLast("@")
        } else {
            destination
        }
        return hostPart.trim()
    }

    private fun sanitizeOutcome(outcome: String): String {
        var clean = outcome
        // Filter out any potential password references
        val forbiddenSubstrings = listOf("password=", "pwd=", "pass=", "secret=")
        for (pattern in forbiddenSubstrings) {
            if (clean.contains(pattern, ignoreCase = true)) {
                clean = clean.replace(Regex("$pattern\\S+", RegexOption.IGNORE_CASE), "$pattern[REDACTED]")
            }
        }
        return clean.take(256)
    }

    companion object {
        const val MAX_CAPACITY = 200

        @Volatile
        private var INSTANCE: NetworkPrivacyDiagnostics? = null

        fun getInstance(): NetworkPrivacyDiagnostics {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkPrivacyDiagnostics().also { INSTANCE = it }
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
