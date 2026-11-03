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

enum class MatchType(val displayName: String) {
    CONTAINS("Contains"),
    STARTS_WITH("Starts With"),
    EXACT("Exact Match"),
    WILDCARD("Wildcard / Pattern")
}

enum class FileTypeScope(val displayName: String) {
    ALL("All Types"),
    FILES_ONLY("Files Only"),
    FOLDERS_ONLY("Folders Only")
}

enum class SearchSortBy(val displayName: String) {
    RELEVANCE("Relevance"),
    NAME_ASC("Name (A - Z)"),
    NAME_DESC("Name (Z - A)"),
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

data class SearchCriteria(
    val query: String = "",
    val rootPath: String = "",
    val category: CategoryFilter = CategoryFilter.ALL,
    val sizeFilter: SizeFilter = SizeFilter.ANY,
    val dateFilter: DateFilter = DateFilter.ANY,
    val matchType: MatchType = MatchType.CONTAINS,
    val matchCase: Boolean = false,
    val fileTypeScope: FileTypeScope = FileTypeScope.ALL,
    val extensionFilter: String? = null,
    val sortBy: SearchSortBy = SearchSortBy.RELEVANCE,
    val searchEverywhere: Boolean = false,
    val includeHidden: Boolean = false
)
