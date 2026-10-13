package com.Hrshd1eux.filemanager.core.search.model

enum class CategoryFilter(val displayName: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    DOWNLOADS("Downloads"),
    ARCHIVES("Archives"),
    APKS("APKs"),
    OTHER("Other")
}

enum class SizeFilter(val displayName: String) {
    ANY("Any Size"),
    LESS_THAN_1MB("< 1 MB"),
    BETWEEN_1MB_AND_100MB("1 MB - 100 MB"),
    GREATER_THAN_100MB("> 100 MB")
}

enum class DateFilter(val displayName: String) {
    ANY("Any Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year")
}

data class SearchCriteria(
    val query: String = "",
    val rootPath: String = "",
    val category: CategoryFilter = CategoryFilter.ALL,
    val sizeFilter: SizeFilter = SizeFilter.ANY,
    val dateFilter: DateFilter = DateFilter.ANY,
    val includeHidden: Boolean = false
)
