package com.Hrshd1eux.filemanager.core.storage.model

/**
 * Filter criteria for installed applications.
 */
enum class AppTypeFilter(val displayName: String) {
    ALL("All Apps"),
    USER("User Apps"),
    SYSTEM("System Apps")
}

/**
 * Sorting options for installed applications in Storage Analyzer.
 */
enum class InstalledAppSortOrder(val displayName: String) {
    NAME_ASC("Name (A → Z)"),
    NAME_DESC("Name (Z → A)"),
    SIZE_DESC("Total Size (Largest first)"),
    SIZE_ASC("Total Size (Smallest first)"),
    CODE_SIZE_DESC("APK / Code Size (Largest first)"),
    DATA_SIZE_DESC("Data Size (Largest first)"),
    CACHE_SIZE_DESC("Cache Size (Largest first)"),
    RECENTLY_UPDATED("Recently Updated"),
    INSTALL_DATE("Install Date"),
    PACKAGE_NAME_ASC("Package Name (A → Z)")
}
