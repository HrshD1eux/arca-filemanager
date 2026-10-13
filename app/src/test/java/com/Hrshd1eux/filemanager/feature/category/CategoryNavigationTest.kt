package com.Hrshd1eux.filemanager.feature.category

import com.Hrshd1eux.filemanager.core.navigation.Screen
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryNavigationTest {

    @Test
    fun categoryScreen_allCategoriesCreateValidDestination() {
        val categories = listOf(
            CategoryFilter.IMAGES,
            CategoryFilter.VIDEOS,
            CategoryFilter.AUDIO,
            CategoryFilter.DOCUMENTS,
            CategoryFilter.DOWNLOADS,
            CategoryFilter.ARCHIVES,
            CategoryFilter.APKS,
            CategoryFilter.OTHER
        )

        categories.forEach { filter ->
            val screen: Screen = Screen.Category(categoryName = filter.name)
            assertEquals(filter.name, (screen as Screen.Category).categoryName)
        }
    }

    @Test
    fun categoryRouting_isDistinctFromSearchRouting() {
        val categoryScreen: Screen = Screen.Category(categoryName = CategoryFilter.IMAGES.name)
        val searchScreen: Screen = Screen.Search(categoryName = CategoryFilter.IMAGES.name)

        assertNotEquals(categoryScreen::class, searchScreen::class)
        assertEquals(Screen.Category::class, categoryScreen::class)
        assertEquals(Screen.Search::class, searchScreen::class)
    }

    @Test
    fun categoryFilter_containsAllRequiredCategories() {
        val expected = setOf("ALL", "IMAGES", "VIDEOS", "AUDIO", "DOCUMENTS", "DOWNLOADS", "ARCHIVES", "APKS", "OTHER")
        val actual = CategoryFilter.entries.map { it.name }.toSet()

        assertTrue(actual.containsAll(expected))
    }

    @Test
    fun categoryFilter_displayNamesAreAccurate() {
        assertEquals("Images", CategoryFilter.IMAGES.displayName)
        assertEquals("Videos", CategoryFilter.VIDEOS.displayName)
        assertEquals("Audio", CategoryFilter.AUDIO.displayName)
        assertEquals("Documents", CategoryFilter.DOCUMENTS.displayName)
        assertEquals("Downloads", CategoryFilter.DOWNLOADS.displayName)
        assertEquals("Archives", CategoryFilter.ARCHIVES.displayName)
        assertEquals("APKs", CategoryFilter.APKS.displayName)
        assertEquals("Other", CategoryFilter.OTHER.displayName)
    }
}
