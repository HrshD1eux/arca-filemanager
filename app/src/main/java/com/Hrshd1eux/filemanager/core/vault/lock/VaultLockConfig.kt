package com.Hrshd1eux.filemanager.core.vault.lock

import kotlinx.serialization.Serializable

@Serializable
enum class VaultAutoLockTimeout(val durationMs: Long, val displayName: String) {
    IMMEDIATELY(0L, "Immediately"),
    ONE_MINUTE(60_000L, "1 minute"),
    FIVE_MINUTES(300_000L, "5 minutes"),
    FIFTEEN_MINUTES(900_000L, "15 minutes"),
    NEVER(-1L, "Never")
}

@Serializable
data class VaultLockSettings(
    val autoLockTimeout: VaultAutoLockTimeout = VaultAutoLockTimeout.FIVE_MINUTES,
    val lockOnBackground: Boolean = true,
    val lockOnScreenOff: Boolean = true
)
