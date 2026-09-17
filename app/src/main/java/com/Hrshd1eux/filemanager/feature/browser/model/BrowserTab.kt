package com.Hrshd1eux.filemanager.feature.browser.model

import java.util.UUID

enum class TabType {
    INTERNAL,
    SD_CARD,
    USB,
    CUSTOM
}

/**
 * Represents an independent browsing tab in the file manager.
 * Power-user feature (1.2.8): Allows quick switching between [ Internal ], [ SD Card ], [ USB ], and custom tabs.
 */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val path: String,
    val type: TabType = TabType.CUSTOM,
    val isAvailable: Boolean = true,
    val isClosable: Boolean = true
)
