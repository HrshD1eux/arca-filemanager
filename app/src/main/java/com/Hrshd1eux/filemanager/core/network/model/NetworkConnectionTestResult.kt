package com.Hrshd1eux.filemanager.core.network.model

/**
 * Result returned by a connection pre-flight test.
 */
data class NetworkConnectionTestResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val serverBanner: String? = null,
    val detectedDialectOrVersion: String? = null,
    val errorMessage: String? = null
)
