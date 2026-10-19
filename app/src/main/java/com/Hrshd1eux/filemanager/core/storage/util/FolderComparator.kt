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
    val rightNode: StorageNode?,
    val relativePath: String? = null,
    val details: String? = null
)

data class FolderComparisonReport(
    val items: List<ComparisonItem>,
    val identicalCount: Int,
    val modifiedCount: Int,
    val leftOnlyCount: Int,
    val rightOnlyCount: Int,
    val leftTotalBytes: Long = 0L,
    val rightTotalBytes: Long = 0L,
    val isRecursive: Boolean = false
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
        var leftBytes = 0L
        var rightBytes = 0L

        for (name in allNames) {
            val left = leftByName[name]
            val right = rightByName[name]
            if (left != null && !left.isDirectory) leftBytes += left.size
            if (right != null && !right.isDirectory) rightBytes += right.size

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
            rightOnlyCount = rightOnly,
            leftTotalBytes = leftBytes,
            rightTotalBytes = rightBytes,
            isRecursive = false
        )
    }

    /**
     * Non-destructive comparison of two directories on the filesystem.
     * Supports both shallow and deep recursive directory tree comparison,
     * with optional cryptographic checksum hashing for identical file sizes.
     */
    fun compareDirectories(
        dirA: java.io.File,
        dirB: java.io.File,
        recursive: Boolean = false,
        compareHashes: Boolean = false
    ): FolderComparisonReport {
        if (!dirA.exists() || !dirA.isDirectory) throw IllegalArgumentException("Directory A not found or not a directory: ${dirA.path}")
        if (!dirB.exists() || !dirB.isDirectory) throw IllegalArgumentException("Directory B not found or not a directory: ${dirB.path}")

        if (!recursive) {
            val leftNodes = (dirA.listFiles() ?: emptyArray()).map { it.toStorageNode() }
            val rightNodes = (dirB.listFiles() ?: emptyArray()).map { it.toStorageNode() }
            return compareFolders(leftNodes, rightNodes)
        }

        // Recursive tree comparison
        val leftFilesByRel = mutableMapOf<String, java.io.File>()
        val rightFilesByRel = mutableMapOf<String, java.io.File>()

        dirA.walkTopDown().forEach { file ->
            if (file != dirA) {
                val rel = file.relativeTo(dirA).path.replace('\\', '/')
                leftFilesByRel[rel] = file
            }
        }

        dirB.walkTopDown().forEach { file ->
            if (file != dirB) {
                val rel = file.relativeTo(dirB).path.replace('\\', '/')
                rightFilesByRel[rel] = file
            }
        }

        val allRelPaths = (leftFilesByRel.keys + rightFilesByRel.keys).sortedWith(
            compareBy<String> { rel ->
                val fA = leftFilesByRel[rel]
                val fB = rightFilesByRel[rel]
                val isDir = fA?.isDirectory ?: fB?.isDirectory ?: false
                if (isDir) 0 else 1
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )

        val comparisonItems = mutableListOf<ComparisonItem>()
        var identical = 0
        var modified = 0
        var leftOnly = 0
        var rightOnly = 0
        var leftBytes = 0L
        var rightBytes = 0L

        for (rel in allRelPaths) {
            val fA = leftFilesByRel[rel]
            val fB = rightFilesByRel[rel]
            if (fA != null && fA.isFile) leftBytes += fA.length()
            if (fB != null && fB.isFile) rightBytes += fB.length()

            when {
                fA != null && fB != null -> {
                    val isDir = fA.isDirectory
                    val status = when {
                        fA.isDirectory && fB.isDirectory -> ComparisonStatus.IDENTICAL
                        fA.isDirectory != fB.isDirectory -> ComparisonStatus.MODIFIED
                        fA.length() != fB.length() -> ComparisonStatus.MODIFIED
                        compareHashes -> {
                            if (calculateSha256Fast(fA) == calculateSha256Fast(fB)) {
                                ComparisonStatus.IDENTICAL
                            } else {
                                ComparisonStatus.MODIFIED
                            }
                        }
                        abs(fA.lastModified() - fB.lastModified()) < 2000L -> ComparisonStatus.IDENTICAL
                        else -> ComparisonStatus.MODIFIED
                    }

                    if (status == ComparisonStatus.IDENTICAL) identical++ else modified++
                    comparisonItems.add(
                        ComparisonItem(
                            name = fA.name,
                            isDirectory = isDir,
                            status = status,
                            leftNode = fA.toStorageNode(),
                            rightNode = fB.toStorageNode(),
                            relativePath = rel,
                            details = if (status == ComparisonStatus.MODIFIED) "Size or content mismatch" else null
                        )
                    )
                }
                fA != null -> {
                    leftOnly++
                    comparisonItems.add(
                        ComparisonItem(
                            name = fA.name,
                            isDirectory = fA.isDirectory,
                            status = ComparisonStatus.UNIQUE_LEFT,
                            leftNode = fA.toStorageNode(),
                            rightNode = null,
                            relativePath = rel
                        )
                    )
                }
                fB != null -> {
                    rightOnly++
                    comparisonItems.add(
                        ComparisonItem(
                            name = fB.name,
                            isDirectory = fB.isDirectory,
                            status = ComparisonStatus.UNIQUE_RIGHT,
                            leftNode = null,
                            rightNode = fB.toStorageNode(),
                            relativePath = rel
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
            rightOnlyCount = rightOnly,
            leftTotalBytes = leftBytes,
            rightTotalBytes = rightBytes,
            isRecursive = true
        )
    }

    private fun java.io.File.toStorageNode(): StorageNode {
        return StorageNode(
            id = absolutePath,
            name = name,
            path = absolutePath,
            size = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            isDirectory = isDirectory
        )
    }

    private fun calculateSha256Fast(file: java.io.File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        java.io.FileInputStream(file).use { fis ->
            val buf = ByteArray(16 * 1024)
            var len: Int
            while (fis.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
