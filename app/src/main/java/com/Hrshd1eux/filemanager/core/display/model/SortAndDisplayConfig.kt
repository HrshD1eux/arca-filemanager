package com.Hrshd1eux.filemanager.core.display.model

enum class SortField(val displayName: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date"),
    TYPE("Type")
}

enum class SortDirection(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class ViewMode(val displayName: String) {
    LIST("List"),
    COMPACT("Compact"),
    GRID("Grid")
}

data class SortOrder(
    val field: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING,
    val foldersFirst: Boolean = true
)
