package com.Hrshd1eux.filemanager.feature.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.security.engine.SecurityAuditEngine
import com.Hrshd1eux.filemanager.core.security.model.SecurityAuditReport
import com.Hrshd1eux.filemanager.core.security.model.SecurityClaimCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityCenterViewModel @JvmOverloads constructor(
    private val auditEngine: SecurityAuditEngine = SecurityAuditEngine()
) : ViewModel() {

    private val _auditReport = MutableStateFlow<SecurityAuditReport?>(null)
    val auditReport: StateFlow<SecurityAuditReport?> = _auditReport.asStateFlow()

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing.asStateFlow()

    private val _selectedCategory = MutableStateFlow<SecurityClaimCategory?>(null)
    val selectedCategory: StateFlow<SecurityClaimCategory?> = _selectedCategory.asStateFlow()

    fun refreshAudit(context: Context) {
        viewModelScope.launch {
            _isAuditing.value = true
            val report = withContext(Dispatchers.IO) {
                auditEngine.performAudit(context)
            }
            _auditReport.value = report
            _isAuditing.value = false
        }
    }

    fun selectCategory(category: SecurityClaimCategory?) {
        _selectedCategory.value = category
    }

    fun copyReportToClipboard(context: Context) {
        val report = _auditReport.value ?: return
        val summary = report.formatDiagnosticSummary()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ARCA Security Audit Report", summary)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, "Security audit report copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
