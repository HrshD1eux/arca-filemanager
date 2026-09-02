package com.Hrshd1eux.filemanager.feature.browser.model

/**
 * Represents a single clickable segment in the breadcrumb navigation bar.
 */
data class BreadcrumbItem(
    val name: String,
    val path: String,
    val isCurrent: Boolean = false
)
