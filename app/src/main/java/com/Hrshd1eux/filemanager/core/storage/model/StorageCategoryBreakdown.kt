package com.Hrshd1eux.filemanager.core.storage.model

import androidx.compose.ui.graphics.Color
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter

enum class StorageCategory(
    val displayName: String,
    val color: Color,
    val searchCategory: CategoryFilter?
) {
    IMAGES("Images", Color(0xFF26A69A), CategoryFilter.IMAGES),
    VIDEOS("Videos", Color(0xFFAB47BC), CategoryFilter.VIDEOS),
    AUDIO("Audio", Color(0xFFFF7043), CategoryFilter.AUDIO),
    DOCUMENTS("Documents", Color(0xFF42A5F5), CategoryFilter.DOCUMENTS),
    ARCHIVES("Archives", Color(0xFFFFA726), CategoryFilter.ARCHIVES),
    OTHER("Other", Color(0xFF78909C), null)
}

data class CategorySize(
    val category: StorageCategory,
    val byteCount: Long,
    val fileCount: Int = 0
)

data class StorageBreakdown(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val categories: List<CategorySize> = emptyList(),
    val isCalculating: Boolean = false
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
