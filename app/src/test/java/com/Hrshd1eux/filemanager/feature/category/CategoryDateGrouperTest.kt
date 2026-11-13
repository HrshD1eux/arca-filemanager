package com.Hrshd1eux.filemanager.feature.category

import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class CategoryDateGrouperTest {

    private fun createNode(name: String, lastModified: Long, size: Long = 1024L): StorageNode {
        return StorageNode(
            id = name,
            name = name,
            path = "/storage/emulated/0/Download/$name",
            size = size,
            lastModified = lastModified,
            isDirectory = false
        )
    }

    @Test
    fun groupItems_emptyListReturnsEmpty() {
        val groups = CategoryDateGrouper.groupItems(emptyList())
        assertTrue(groups.isEmpty())
    }

    @Test
    fun groupItems_classifiesBucketsAccurately() {
        // Fixed reference time: 2026-11-13 14:00:00 UTC
        val fixedNow = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 13, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 1. Today (e.g. 2 hours earlier)
        val todayTime = fixedNow - 2 * 3600 * 1000L
        val todayFile = createNode("today.jpg", todayTime, 1000L)

        // 2. Yesterday (e.g. 26 hours earlier)
        val yesterdayTime = fixedNow - 26 * 3600 * 1000L
        val yesterdayFile = createNode("yesterday.jpg", yesterdayTime, 2000L)

        // 3. This Week (e.g. 4 days earlier)
        val thisWeekTime = fixedNow - 4 * 86400 * 1000L
        val thisWeekFile = createNode("this_week.jpg", thisWeekTime, 3000L)

        // 4. This Month (e.g. Nov 2, 2026 -> 11 days earlier)
        val thisMonthTime = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 2, 10, 0, 0)
        }.timeInMillis
        val thisMonthFile = createNode("this_month.jpg", thisMonthTime, 4000L)

        // 5. Month & Year earlier in current year (e.g. Oct 15, 2026)
        val octoberTime = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15, 10, 0, 0)
        }.timeInMillis
        val octoberFile = createNode("october.jpg", octoberTime, 5000L)

        // 6. Year in previous calendar year (e.g. June 10, 2025)
        val year2025Time = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 10, 10, 0, 0)
        }.timeInMillis
        val year2025File = createNode("year2025.jpg", year2025Time, 6000L)

        // 7. Unknown date (0L)
        val unknownFile = createNode("unknown.jpg", 0L, 7000L)

        val items = listOf(
            todayFile,
            yesterdayFile,
            thisWeekFile,
            thisMonthFile,
            octoberFile,
            year2025File,
            unknownFile
        )

        val groups = CategoryDateGrouper.groupItems(
            items = items,
            sortDirection = SortDirection.DESCENDING,
            locale = Locale.US,
            nowProvider = { fixedNow }
        )

        assertEquals(7, groups.size)

        // Verify group 0: Today
        assertEquals(DateBucketType.TODAY, groups[0].type)
        assertEquals("Today", groups[0].title)
        assertEquals(1, groups[0].fileCount)
        assertEquals(1000L, groups[0].totalSizeBytes)

        // Verify group 1: Yesterday
        assertEquals(DateBucketType.YESTERDAY, groups[1].type)
        assertEquals("Yesterday", groups[1].title)
        assertEquals(1, groups[1].fileCount)

        // Verify group 2: This Week
        assertEquals(DateBucketType.THIS_WEEK, groups[2].type)
        assertEquals("This Week", groups[2].title)
        assertEquals(1, groups[2].fileCount)

        // Verify group 3: This Month
        assertEquals(DateBucketType.THIS_MONTH, groups[3].type)
        assertEquals("This Month", groups[3].title)
        assertEquals(1, groups[3].fileCount)

        // Verify group 4: October 2026
        assertEquals(DateBucketType.MONTH_YEAR, groups[4].type)
        assertEquals("October 2026", groups[4].title)
        assertEquals(1, groups[4].fileCount)

        // Verify group 5: 2025
        assertEquals(DateBucketType.YEAR, groups[5].type)
        assertEquals("2025", groups[5].title)
        assertEquals(1, groups[5].fileCount)

        // Verify group 6: Unknown Date
        assertEquals(DateBucketType.UNKNOWN, groups[6].type)
        assertEquals("Unknown Date", groups[6].title)
        assertEquals(1, groups[6].fileCount)
    }

    @Test
    fun groupItems_ascendingSortDirectionInvertsOrder() {
        val fixedNow = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 13, 14, 0, 0)
        }.timeInMillis

        val todayFile = createNode("today.jpg", fixedNow - 3600 * 1000L)
        val year2024File = createNode("year2024.jpg", Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 1, 10, 0, 0)
        }.timeInMillis)

        val groups = CategoryDateGrouper.groupItems(
            items = listOf(todayFile, year2024File),
            sortDirection = SortDirection.ASCENDING,
            locale = Locale.US,
            nowProvider = { fixedNow }
        )

        assertEquals(2, groups.size)
        assertEquals(DateBucketType.YEAR, groups[0].type)
        assertEquals("2024", groups[0].title)
        assertEquals(DateBucketType.TODAY, groups[1].type)
        assertEquals("Today", groups[1].title)
    }
}
