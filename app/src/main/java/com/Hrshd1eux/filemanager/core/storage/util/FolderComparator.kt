package com.Hrshd1eux.filemanager.core.storage.util

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlin.math.abs

enum class ComparisonStatus {
    IDENTICAL,
    MODIFIED,
    UNIQUE_LEFT,
    UNIQUE_RIGHT
}

data class ComparisonItem(
    val name: String,
    val isDirectory: Boolean,
    val status: ComparisonStatus,
    val leftNode: StorageNode?,
    val rightNode: StorageNode?
)

data class FolderComparisonReport(
    val items: List<ComparisonItem>,
    val identicalCount: Int,
    val modifiedCount: Int,
    val leftOnlyCount: Int,
    val rightOnlyCount: Int
) {
    val totalCount: Int = items.size
    val isIdentical: Boolean = modifiedCount == 0 && leftOnlyCount == 0 && rightOnlyCount == 0
}

object FolderComparator {

    /**
     * Compares two sets of storage nodes (e.g. from Left and Right Dual-Pane folders).
     * Matches items by filename and compares size, type, and timestamp.
     */
    fun compareFolders(
        leftItems: List<StorageNode>,
        rightItems: List<StorageNode>
    ): FolderComparisonReport {
        val leftByName = leftItems.associateBy { it.name }
        val rightByName = rightItems.associateBy { it.name }

        val allNames = (leftByName.keys + rightByName.keys).sortedWith(
            compareBy<String> { name ->
                val leftNode = leftByName[name]
                val rightNode = rightByName[name]
                val isDir = leftNode?.isDirectory ?: rightNode?.isDirectory ?: false
                if (isDir) 0 else 1
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )

        val comparisonItems = mutableListOf<ComparisonItem>()
        var identical = 0
        var modified = 0
        var leftOnly = 0
        var rightOnly = 0

        for (name in allNames) {
            val left = leftByName[name]
            val right = rightByName[name]

            when {
                left != null && right != null -> {
                    val status = if (left.isDirectory && right.isDirectory) {
                        ComparisonStatus.IDENTICAL
                    } else if (left.isDirectory != right.isDirectory) {
                        ComparisonStatus.MODIFIED
                    } else if (left.size == right.size && abs(left.lastModified - right.lastModified) < 2000L) {
                        ComparisonStatus.IDENTICAL
                    } else {
                        ComparisonStatus.MODIFIED
                    }

                    if (status == ComparisonStatus.IDENTICAL) identical++ else modified++
                    comparisonItems.add(
                        ComparisonItem(
                            name = name,
                            isDirectory = left.isDirectory,
                            status = status,
                            leftNode = left,
                            rightNode = right
                        )
                    )
                }
                left != null -> {
                    leftOnly++
                    comparisonItems.add(
                        ComparisonItem(
                            name = name,
                            isDirectory = left.isDirectory,
                            status = ComparisonStatus.UNIQUE_LEFT,
                            leftNode = left,
                            rightNode = null
                        )
                    )
                }
                right != null -> {
                    rightOnly++
                    comparisonItems.add(
                        ComparisonItem(
                            name = name,
                            isDirectory = right.isDirectory,
                            status = ComparisonStatus.UNIQUE_RIGHT,
                            leftNode = null,
                            rightNode = right
                        )
                    )
                }
            }
        }

        return FolderComparisonReport(
            items = comparisonItems,
            identicalCount = identical,
            modifiedCount = modified,
            leftOnlyCount = leftOnly,
            rightOnlyCount = rightOnly
        )
    }
}
