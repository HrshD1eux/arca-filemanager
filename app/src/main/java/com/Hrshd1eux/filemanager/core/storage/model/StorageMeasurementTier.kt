package com.Hrshd1eux.filemanager.core.storage.model

/**
 * Defines the five fundamental storage measurement tiers of the ARCA Storage Data Model (Version 1.3.11):
 *
 * 1. [PACKAGE]: Installed application runtime footprint (executable code, optimized dex/art, and extracted native libraries).
 * 2. [APK]: Raw APK binary file(s) residing directly on disk (standalone base APK or base + split APK components).
 * 3. [APP_DATA]: Application private user databases, preferences, and internal/external sandboxed storage (/data/data, /data/user/0).
 * 4. [CACHE]: Temporary application cache directories (internal cache, codeCache, external cache).
 * 5. [SYSTEM_STORAGE]: Operating system platform firmware, system partitions (/system, /vendor, /product, /apex), and system apps.
 *
 * Adheres strictly to the architectural constraints:
 * - Separate package, APK, app-data, cache, and system-storage measurements.
 * - Make the interface clear about whether each value is exact, approximate, stale, or unavailable.
 * - Never label an inaccessible measurement as zero; mark as Unavailable.
 */
enum class StorageMeasurementTier(
    val displayName: String,
    val description: String,
    val isSystemLevel: Boolean = false
) {
    PACKAGE(
        displayName = "Package Footprint",
        description = "Total runtime code footprint including APKs, compiled dex, and native libraries."
    ),
    APK(
        displayName = "APK Binary",
        description = "Raw standalone or split APK binary files on disk."
    ),
    APP_DATA(
        displayName = "App Data",
        description = "Application private databases, configurations, and user state."
    ),
    CACHE(
        displayName = "App Cache",
        description = "Temporary cache files and cached web/media assets."
    ),
    SYSTEM_STORAGE(
        displayName = "System Storage",
        description = "Core Android OS firmware, system partitions, and pre-installed system packages.",
        isSystemLevel = true
    )
}
