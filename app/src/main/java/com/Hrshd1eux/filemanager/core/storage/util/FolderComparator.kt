package com.Hrshd1eux.filemanager.core.storage.util

import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlin.math.abs

enum class ComparisonStatus {
    IDENTICAL,
    MODIFIED,
    UNIQUE_LEFT,
    UNIQUE_RIGHT,
    REQUIRES_COMPARISON,
    INACCESSIBLE;

    companion object {
        val SAME get() = IDENTICAL
        val DIFFERENT get() = MODIFIED
        val MISSING_FROM_B get() = UNIQUE_LEFT
        val MISSING_FROM_A get() = UNIQUE_RIGHT
    }
}

data class ComparisonItem(
    val name: String,
    val isDirectory: Boolean,
    val status: ComparisonStatus,
    val leftNode: StorageNode?,
    val rightNode: StorageNode?,
    val relativePath: String? = null,
    val details: String? = null,
    val presenceA: String = if (status == ComparisonStatus.INACCESSIBLE && leftNode == null) "Inaccessible" else if (leftNode != null) "Present" else "Missing",
    val presenceB: String = if (status == ComparisonStatus.INACCESSIBLE && rightNode == null) "Inaccessible" else if (rightNode != null) "Present" else "Missing"
) {
    val resultDescription: String
        get() = when (status) {
            ComparisonStatus.IDENTICAL -> "Same"
            ComparisonStatus.REQUIRES_COMPARISON -> "Requires comparison"
            ComparisonStatus.MODIFIED -> "Different"
            ComparisonStatus.UNIQUE_LEFT -> "Missing from B"
            ComparisonStatus.UNIQUE_RIGHT -> "Missing from A"
            ComparisonStatus.INACCESSIBLE -> "Inaccessible"
        }
}

data class FolderComparisonReport(
    val items: List<ComparisonItem>,
    val identicalCount: Int,
    val modifiedCount: Int,
    val leftOnlyCount: Int,
    val rightOnlyCount: Int,
    val requiresComparisonCount: Int = 0,
    val inaccessibleCount: Int = 0,
    val leftTotalBytes: Long = 0L,
    val rightTotalBytes: Long = 0L,
    val isRecursive: Boolean = false
) {
    val totalCount: Int = items.size
    val isIdentical: Boolean = modifiedCount == 0 && leftOnlyCount == 0 && rightOnlyCount == 0 && requiresComparisonCount == 0 && inaccessibleCount == 0
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

    /**
     * Non-destructive, structured comparison of two directories (Milestones 1.4.23 & 1.4.24).
     *
     * Features:
     * - Structured presence: Item | Backup A | Backup B | Result
     * - Statuses: SAME, REQUIRES_COMPARISON, DIFFERENT, MISSING_FROM_B, MISSING_FROM_A, INACCESSIBLE
     * - Never modifies files during comparison (strictly read-only).
     * - Honest distinction: detects permission-restricted folders and marks them INACCESSIBLE instead of falsely reporting missing.
     * - Detects files that changed during scanning.
     */
    fun compareDirectoriesStructured(
        dirA: java.io.File,
        dirB: java.io.File,
        verifyHashes: Boolean = false,
        recursive: Boolean = false
    ): FolderComparisonReport {
        if (!dirA.exists() || !dirA.isDirectory) throw IllegalArgumentException("Directory A not found or not a directory: ${dirA.path}")
        if (!dirB.exists() || !dirB.isDirectory) throw IllegalArgumentException("Directory B not found or not a directory: ${dirB.path}")

        val inaccessiblePathsA = mutableSetOf<String>()
        val inaccessiblePathsB = mutableSetOf<String>()

        val filesAByRel = mutableMapOf<String, java.io.File>()
        val filesBByRel = mutableMapOf<String, java.io.File>()

        if (!dirA.canRead()) {
            inaccessiblePathsA.add(dirA.absolutePath)
        } else if (!recursive) {
            val list = dirA.listFiles()
            if (list == null) {
                inaccessiblePathsA.add(dirA.absolutePath)
            } else {
                for (f in list) {
                    if (!f.canRead()) inaccessiblePathsA.add(f.absolutePath)
                    filesAByRel[f.name] = f
                }
            }
        } else {
            dirA.walkTopDown().onEnter { dir ->
                if (!dir.canRead()) {
                    inaccessiblePathsA.add(dir.absolutePath)
                    false
                } else true
            }.forEach { file ->
                if (file != dirA) {
                    val rel = file.relativeTo(dirA).path.replace('\\', '/')
                    filesAByRel[rel] = file
                }
            }
        }

        if (!dirB.canRead()) {
            inaccessiblePathsB.add(dirB.absolutePath)
        } else if (!recursive) {
            val list = dirB.listFiles()
            if (list == null) {
                inaccessiblePathsB.add(dirB.absolutePath)
            } else {
                for (f in list) {
                    if (!f.canRead()) inaccessiblePathsB.add(f.absolutePath)
                    filesBByRel[f.name] = f
                }
            }
        } else {
            dirB.walkTopDown().onEnter { dir ->
                if (!dir.canRead()) {
                    inaccessiblePathsB.add(dir.absolutePath)
                    false
                } else true
            }.forEach { file ->
                if (file != dirB) {
                    val rel = file.relativeTo(dirB).path.replace('\\', '/')
                    filesBByRel[rel] = file
                }
            }
        }

        val allRelPaths = (filesAByRel.keys + filesBByRel.keys).sortedWith(
            compareBy<String> { rel ->
                val fA = filesAByRel[rel]
                val fB = filesBByRel[rel]
                val isDir = fA?.isDirectory ?: fB?.isDirectory ?: false
                if (isDir) 0 else 1
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )

        val comparisonItems = mutableListOf<ComparisonItem>()
        var identical = 0
        var modified = 0
        var leftOnly = 0
        var rightOnly = 0
        var requiresComparison = 0
        var inaccessible = 0
        var leftBytes = 0L
        var rightBytes = 0L

        for (rel in allRelPaths) {
            val fA = filesAByRel[rel]
            val fB = filesBByRel[rel]
            if (fA != null && fA.isFile) leftBytes += fA.length()
            if (fB != null && fB.isFile) rightBytes += fB.length()

            val isAInaccessible = fA != null && (inaccessiblePathsA.contains(fA.absolutePath) || !fA.canRead())
            val isBInaccessible = fB != null && (inaccessiblePathsB.contains(fB.absolutePath) || !fB.canRead())

            when {
                isAInaccessible || isBInaccessible -> {
                    inaccessible++
                    comparisonItems.add(
                        ComparisonItem(
                            name = fA?.name ?: fB?.name ?: rel,
                            isDirectory = fA?.isDirectory ?: fB?.isDirectory ?: false,
                            status = ComparisonStatus.INACCESSIBLE,
                            leftNode = fA?.toStorageNode(),
                            rightNode = fB?.toStorageNode(),
                            relativePath = rel,
                            details = "Inaccessible due to system storage permissions"
                        )
                    )
                }
                fA != null && fB != null -> {
                    val isDir = fA.isDirectory
                    val initialLenA = fA.length()
                    val initialLenB = fB.length()

                    val status = when {
                        fA.isDirectory && fB.isDirectory -> ComparisonStatus.IDENTICAL
                        fA.isDirectory != fB.isDirectory -> ComparisonStatus.MODIFIED
                        fA.length() != fB.length() -> ComparisonStatus.MODIFIED
                        verifyHashes -> {
                            val hashA = calculateSha256Fast(fA)
                            val hashB = calculateSha256Fast(fB)
                            if (hashA == hashB) ComparisonStatus.IDENTICAL else ComparisonStatus.MODIFIED
                        }
                        else -> ComparisonStatus.REQUIRES_COMPARISON
                    }

                    // Check for changes during scan
                    val details = if (fA.isFile && (fA.length() != initialLenA || fB.length() != initialLenB)) {
                        "File changed during comparison scan"
                    } else if (status == ComparisonStatus.REQUIRES_COMPARISON) {
                        "Matching metadata (Pending hash verification)"
                    } else null

                    when (status) {
                        ComparisonStatus.IDENTICAL -> identical++
                        ComparisonStatus.REQUIRES_COMPARISON -> requiresComparison++
                        else -> modified++
                    }

                    comparisonItems.add(
                        ComparisonItem(
                            name = fA.name,
                            isDirectory = isDir,
                            status = status,
                            leftNode = fA.toStorageNode(),
                            rightNode = fB.toStorageNode(),
                            relativePath = rel,
                            details = details
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
            requiresComparisonCount = requiresComparison,
            inaccessibleCount = inaccessible,
            leftTotalBytes = leftBytes,
            rightTotalBytes = rightBytes,
            isRecursive = recursive
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
