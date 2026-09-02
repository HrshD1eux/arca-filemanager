package com.Hrshd1eux.filemanager.core.display.util

import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode

object NodeSorter {

    fun sort(nodes: List<StorageNode>, order: SortOrder): List<StorageNode> {
        val baseComparator = when (order.field) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.SIZE -> compareBy<StorageNode> { it.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.DATE -> compareBy<StorageNode> { it.lastModified }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortField.TYPE -> compareBy<StorageNode> { it.extension() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }

        val directedComparator = if (order.direction == SortDirection.DESCENDING) {
            baseComparator.reversed()
        } else {
            baseComparator
        }

        return if (order.foldersFirst) {
            val (folders, files) = nodes.partition { it.isDirectory }
            folders.sortedWith(directedComparator) + files.sortedWith(directedComparator)
        } else {
            nodes.sortedWith(directedComparator)
        }
    }

    private fun StorageNode.extension(): String {
        return if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
    }
}
