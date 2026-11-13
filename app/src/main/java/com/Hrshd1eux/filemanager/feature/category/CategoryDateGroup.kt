package com.Hrshd1eux.filemanager.feature.category

import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Identifies the temporal category of a date group.
 */
enum class DateBucketType {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    MONTH_YEAR,
    YEAR,
    UNKNOWN
}

/**
 * Represents a group of files classified under a common chronological date header.
 */
data class CategoryDateGroup(
    val id: String,
    val type: DateBucketType,
    val title: String,
    val items: List<StorageNode>,
    val totalSizeBytes: Long = items.sumOf { it.size },
    val fileCount: Int = items.size
)

/**
 * Partitions storage nodes into human-readable chronological date groups
 * (e.g., Today, Yesterday, This Week, This Month, Month Year, Year).
 */
object CategoryDateGrouper {

    fun groupItems(
        items: List<StorageNode>,
        sortDirection: SortDirection = SortDirection.DESCENDING,
        locale: Locale = Locale.getDefault(),
        nowProvider: () -> Long = { System.currentTimeMillis() }
    ): List<CategoryDateGroup> {
        if (items.isEmpty()) return emptyList()

        val now = nowProvider()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }

        val currentYear = calendar.get(Calendar.YEAR)

        // Today start: 00:00:00.000 today
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Yesterday start: 00:00:00.000 yesterday
        val yesterdayStart = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis

        // This week start: 7 days before todayStart
        val thisWeekStart = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis

        // This month start: 1st day of current month at 00:00:00.000
        val thisMonthStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val monthYearFormat = SimpleDateFormat("MMMM yyyy", locale)
        val monthIdFormat = SimpleDateFormat("yyyy-MM", Locale.US)

        val groupsMap = LinkedHashMap<String, GroupBuilder>()

        fun getBuilder(
            key: String,
            type: DateBucketType,
            title: String,
            sortKey: Long
        ): GroupBuilder {
            return groupsMap.getOrPut(key) {
                GroupBuilder(id = key, type = type, title = title, sortKey = sortKey)
            }
        }

        for (item in items) {
            val timestamp = item.lastModified
            if (timestamp <= 0L) {
                getBuilder(
                    key = "unknown",
                    type = DateBucketType.UNKNOWN,
                    title = "Unknown Date",
                    sortKey = Long.MIN_VALUE
                ).items.add(item)
                continue
            }

            if (timestamp >= todayStart) {
                getBuilder(
                    key = "today",
                    type = DateBucketType.TODAY,
                    title = "Today",
                    sortKey = Long.MAX_VALUE
                ).items.add(item)
            } else if (timestamp >= yesterdayStart) {
                getBuilder(
                    key = "yesterday",
                    type = DateBucketType.YESTERDAY,
                    title = "Yesterday",
                    sortKey = Long.MAX_VALUE - 1
                ).items.add(item)
            } else if (timestamp >= thisWeekStart) {
                getBuilder(
                    key = "this_week",
                    type = DateBucketType.THIS_WEEK,
                    title = "This Week",
                    sortKey = Long.MAX_VALUE - 2
                ).items.add(item)
            } else if (timestamp >= thisMonthStart) {
                getBuilder(
                    key = "this_month",
                    type = DateBucketType.THIS_MONTH,
                    title = "This Month",
                    sortKey = Long.MAX_VALUE - 3
                ).items.add(item)
            } else {
                val itemCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                val itemYear = itemCal.get(Calendar.YEAR)

                if (itemYear == currentYear) {
                    val dateObj = Date(timestamp)
                    val monthId = monthIdFormat.format(dateObj)
                    val monthTitle = monthYearFormat.format(dateObj)
                    val monthStartCal = Calendar.getInstance().apply {
                        timeInMillis = timestamp
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    getBuilder(
                        key = monthId,
                        type = DateBucketType.MONTH_YEAR,
                        title = monthTitle,
                        sortKey = monthStartCal.timeInMillis
                    ).items.add(item)
                } else {
                    val yearStr = itemYear.toString()
                    val yearStartCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, itemYear)
                        set(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    getBuilder(
                        key = yearStr,
                        type = DateBucketType.YEAR,
                        title = yearStr,
                        sortKey = yearStartCal.timeInMillis
                    ).items.add(item)
                }
            }
        }

        val sortedBuilders = if (sortDirection == SortDirection.ASCENDING) {
            groupsMap.values.sortedBy { it.sortKey }
        } else {
            groupsMap.values.sortedByDescending { it.sortKey }
        }

        return sortedBuilders.map { builder ->
            CategoryDateGroup(
                id = builder.id,
                type = builder.type,
                title = builder.title,
                items = builder.items
            )
        }
    }

    private class GroupBuilder(
        val id: String,
        val type: DateBucketType,
        val title: String,
        val sortKey: Long,
        val items: MutableList<StorageNode> = mutableListOf()
    )
}
