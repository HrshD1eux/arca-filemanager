package com.Hrshd1eux.filemanager.core.archive.split

import java.io.File
import java.util.Locale

sealed class SplitResolution {
    data class Success(
        val baseName: String,
        val parts: List<File>,
        val totalSize: Long,
        val isSplit: Boolean
    ) : SplitResolution()

    data class MissingParts(
        val missingPartName: String,
        val expectedPartIndex: Int,
        val existingParts: List<File>
    ) : SplitResolution()

    data class NotSplit(val file: File) : SplitResolution()
}

class MissingSplitVolumeException(message: String) : Exception(message)

object SplitArchiveResolver {
    private val SEVEN_Z_SPLIT_REGEX = Regex("""^(.*\.7z)\.(\d+)$""", RegexOption.IGNORE_CASE)
    private val GENERIC_SPLIT_REGEX = Regex("""^(.*)\.(\d{3,})$""", RegexOption.IGNORE_CASE)
    private val ZIP_SPLIT_PART_REGEX = Regex("""^(.*)\.z(\d{2})$""", RegexOption.IGNORE_CASE)

    fun isSplitArchiveName(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        return SEVEN_Z_SPLIT_REGEX.matches(lower) ||
                ZIP_SPLIT_PART_REGEX.matches(lower) ||
                GENERIC_SPLIT_REGEX.matches(lower)
    }

    fun is7zSplit(fileName: String): Boolean {
        return SEVEN_Z_SPLIT_REGEX.matches(fileName.lowercase(Locale.ROOT))
    }

    /**
     * Resolves all contiguous parts of a split archive from any of its member files.
     * Validates that part 1 exists and there are no missing intermediate volumes.
     */
    fun resolveSplitParts(targetFile: File): SplitResolution {
        val parentDir = targetFile.parentFile ?: return SplitResolution.NotSplit(targetFile)
        val name = targetFile.name

        val sevenZMatch = SEVEN_Z_SPLIT_REGEX.matchEntire(name)
        if (sevenZMatch != null) {
            val basePrefix = sevenZMatch.groupValues[1] // e.g. "backup.7z"
            val digitsLength = sevenZMatch.groupValues[2].length.coerceAtLeast(3)

            val siblingFiles = parentDir.listFiles { _, siblingName ->
                val m = SEVEN_Z_SPLIT_REGEX.matchEntire(siblingName)
                m != null && m.groupValues[1].equals(basePrefix, ignoreCase = true)
            }?.toList().orEmpty()

            if (siblingFiles.isEmpty()) {
                return SplitResolution.NotSplit(targetFile)
            }

            val partMap = mutableMapOf<Int, File>()
            for (f in siblingFiles) {
                val m = SEVEN_Z_SPLIT_REGEX.matchEntire(f.name)
                if (m != null) {
                    val partNum = m.groupValues[2].toIntOrNull()
                    if (partNum != null) {
                        partMap[partNum] = f
                    }
                }
            }

            if (!partMap.containsKey(1)) {
                val expectedFirstPart = String.format(Locale.ROOT, "%s.%0${digitsLength}d", basePrefix, 1)
                return SplitResolution.MissingParts(expectedFirstPart, 1, siblingFiles.sortedBy { it.name })
            }

            val maxPart = partMap.keys.maxOrNull() ?: 1
            val orderedParts = mutableListOf<File>()
            var totalSize = 0L

            for (i in 1..maxPart) {
                val partFile = partMap[i]
                if (partFile == null) {
                    val missingName = String.format(Locale.ROOT, "%s.%0${digitsLength}d", basePrefix, i)
                    return SplitResolution.MissingParts(missingName, i, orderedParts)
                }
                orderedParts.add(partFile)
                totalSize += partFile.length()
            }

            return SplitResolution.Success(
                baseName = basePrefix,
                parts = orderedParts,
                totalSize = totalSize,
                isSplit = orderedParts.size > 1 || SEVEN_Z_SPLIT_REGEX.matches(name)
            )
        }

        // Check generic split (e.g. archive.001)
        val genericMatch = GENERIC_SPLIT_REGEX.matchEntire(name)
        if (genericMatch != null) {
            val basePrefix = genericMatch.groupValues[1]
            val digitsLength = genericMatch.groupValues[2].length.coerceAtLeast(3)

            val siblingFiles = parentDir.listFiles { _, siblingName ->
                val m = GENERIC_SPLIT_REGEX.matchEntire(siblingName)
                m != null && m.groupValues[1].equals(basePrefix, ignoreCase = true)
            }?.toList().orEmpty()

            val partMap = mutableMapOf<Int, File>()
            for (f in siblingFiles) {
                val m = GENERIC_SPLIT_REGEX.matchEntire(f.name)
                if (m != null) {
                    val partNum = m.groupValues[2].toIntOrNull()
                    if (partNum != null) {
                        partMap[partNum] = f
                    }
                }
            }

            if (partMap.containsKey(1)) {
                val maxPart = partMap.keys.maxOrNull() ?: 1
                val orderedParts = mutableListOf<File>()
                var totalSize = 0L

                for (i in 1..maxPart) {
                    val partFile = partMap[i]
                    if (partFile == null) {
                        val missingName = String.format(Locale.ROOT, "%s.%0${digitsLength}d", basePrefix, i)
                        return SplitResolution.MissingParts(missingName, i, orderedParts)
                    }
                    orderedParts.add(partFile)
                    totalSize += partFile.length()
                }

                return SplitResolution.Success(
                    baseName = basePrefix,
                    parts = orderedParts,
                    totalSize = totalSize,
                    isSplit = orderedParts.size > 1
                )
            }
        }

        return SplitResolution.NotSplit(targetFile)
    }

    /**
     * Formats the split volume name for a given 1-based index (e.g. backup.7z.001, backup.7z.002).
     */
    fun getSplitVolumeName(baseFile: File, volumeIndex: Int, digits: Int = 3): File {
        val parent = baseFile.parentFile
        var baseName = baseFile.name
        if (baseName.lowercase(Locale.ROOT).endsWith(".7z")) {
            baseName = baseName.substring(0, baseName.length - 3)
        }
        val fileName = String.format(Locale.ROOT, "%s.7z.%0${digits}d", baseName, volumeIndex)
        return if (parent != null) File(parent, fileName) else File(fileName)
    }
}
