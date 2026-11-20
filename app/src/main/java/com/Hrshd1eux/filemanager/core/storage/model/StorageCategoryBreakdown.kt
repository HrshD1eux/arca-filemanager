package com.Hrshd1eux.filemanager.core.storage.model

import androidx.compose.ui.graphics.Color
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter

enum class StorageCategory(
    val displayName: String,
    val color: Color,
    val searchCategory: CategoryFilter?
) {
    IMAGES("Images", Color(0xFF2563EB), CategoryFilter.IMAGES),
    VIDEOS("Videos", Color(0xFF7C3AED), CategoryFilter.VIDEOS),
    AUDIO("Audio", Color(0xFFEA580C), CategoryFilter.AUDIO),
    DOCUMENTS("Documents", Color(0xFF0D9488), CategoryFilter.DOCUMENTS),
    ARCHIVES("Archives", Color(0xFFD97706), CategoryFilter.ARCHIVES),
    APPS("Apps", Color(0xFF10B981), null),
    SYSTEM("System", Color(0xFF6366F1), null),
    OTHER("Other", Color(0xFF64748B), CategoryFilter.OTHER)
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
