package com.Hrshd1eux.filemanager.feature.network.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyDiagnostics
import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyEvent
import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyEventType
import com.Hrshd1eux.filemanager.core.network.diagnostics.NetworkPrivacyStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NetworkPrivacyDiagnosticsViewModel(
    private val diagnostics: NetworkPrivacyDiagnostics = NetworkPrivacyDiagnostics.getInstance()
) : ViewModel() {

    private val _selectedProtocolFilter = MutableStateFlow<String?>(null)
    val selectedProtocolFilter: StateFlow<String?> = _selectedProtocolFilter.asStateFlow()

    private val _onlyAlerts = MutableStateFlow(false)
    val onlyAlerts: StateFlow<Boolean> = _onlyAlerts.asStateFlow()

    val events: StateFlow<List<NetworkPrivacyEvent>> = combine(
        diagnostics.eventsFlow,
        _selectedProtocolFilter,
        _onlyAlerts
    ) { allEvents, proto, alertsOnly ->
        allEvents
            .filter { proto == null || it.protocol.equals(proto, ignoreCase = true) }
            .filter { !alertsOnly || it.eventType == NetworkPrivacyEventType.SECURITY_ALERT }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = diagnostics.getRecentEvents(50)
    )

    fun getStats(): NetworkPrivacyStats = diagnostics.getStats()

    fun setProtocolFilter(protocol: String?) {
        _selectedProtocolFilter.value = protocol
    }

    fun toggleOnlyAlerts() {
        _onlyAlerts.value = !_onlyAlerts.value
    }

    fun clearEvents() {
        diagnostics.clear()
    }

    fun copyLogToClipboard(context: Context) {
        val log = diagnostics.exportDiagnosticsText()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ARCA-Recorded Network Connections", log)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Network connections log copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
