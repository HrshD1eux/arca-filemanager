package com.Hrshd1eux.filemanager.core.version

/**
 * Encapsulates dynamic version metadata extracted directly from Android package metadata.
 *
 * @property versionName The human-readable version string (e.g., "1.2.32").
 * @property versionCode The integer/long version code (e.g., 10232L).
 * @property applicationId The package identifier of the running application (e.g., "com.Hrshd1eux.filemanager").
 * @property buildIdentifier Optional build tag, commit SHA, or build type (e.g., "debug", "release", "beta").
 */
data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long,
    val applicationId: String,
    val buildIdentifier: String? = null
) {
    /**
     * Formatted display version string, e.g., "Version 1.2.32".
     */
    val displayVersion: String
        get() = "Version $versionName"

    /**
     * Formatted display build code string, e.g., "Build 10232".
     */
    val displayBuild: String
        get() = "Build $versionCode"

    /**
     * Formatted optional build identifier clearly labeled, e.g., "Build ID: debug", or null if none.
     */
    val displayBuildIdentifier: String?
        get() = buildIdentifier?.takeIf { it.isNotBlank() }?.let { "Build ID: $it" }

    /**
     * Full one-line formatted version summary for logging or display.
     * E.g. "Version 1.2.32 (Build 10232 • Build ID: debug)" or "Version 1.2.32 (Build 10232)".
     */
    val formattedSummary: String
        get() = if (displayBuildIdentifier != null) {
            "$displayVersion ($displayBuild • $displayBuildIdentifier)"
        } else {
            "$displayVersion ($displayBuild)"
        }
}
