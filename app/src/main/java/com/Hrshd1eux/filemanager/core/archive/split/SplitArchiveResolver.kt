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

class MissingSplitVolumeException(
    message: String,
    val missingPartName: String? = null,
    val expectedPartIndex: Int? = null
) : Exception(message)

object SplitArchiveResolver {
    private val SEVEN_Z_SPLIT_REGEX = Regex("""^(.*\.7z)\.(\d+)$""", RegexOption.IGNORE_CASE)
    private val GENERIC_SPLIT_REGEX = Regex("""^(.*)\.(\d{3,})$""", RegexOption.IGNORE_CASE)
    private val RAR_PART_SPLIT_REGEX = Regex("""^(.*?)\.part(\d+)\.rar$""", RegexOption.IGNORE_CASE)
    private val RAR_OLD_SPLIT_REGEX = Regex("""^(.*?)\.r(\d{2,})$""", RegexOption.IGNORE_CASE)
    private val ZIP_SPLIT_PART_REGEX = Regex("""^(.*?)\.z(\d{2,})$""", RegexOption.IGNORE_CASE)

    fun isSplitArchiveName(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        return SEVEN_Z_SPLIT_REGEX.matches(lower) ||
                ZIP_SPLIT_PART_REGEX.matches(lower) ||
                RAR_PART_SPLIT_REGEX.matches(lower) ||
                RAR_OLD_SPLIT_REGEX.matches(lower) ||
                GENERIC_SPLIT_REGEX.matches(lower)
    }

    fun is7zSplit(fileName: String): Boolean {
        return SEVEN_Z_SPLIT_REGEX.matches(fileName.lowercase(Locale.ROOT))
    }

    fun isRarSplit(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        return RAR_PART_SPLIT_REGEX.matches(lower) || RAR_OLD_SPLIT_REGEX.matches(lower)
    }

    fun isZipSplit(fileName: String): Boolean {
        return ZIP_SPLIT_PART_REGEX.matches(fileName.lowercase(Locale.ROOT))
    }

    /**
     * Resolves all contiguous parts of a split archive from any of its member files.
     * Validates that part 1 exists and there are no missing intermediate volumes.
     * Supported split patterns:
     * - 7-Zip: archive.7z.001, archive.7z.002, ...
     * - Generic: archive.001, archive.002, ...
     * - RAR (New Style): archive.part1.rar, archive.part2.rar (or .part01.rar)
     * - RAR (Old Style): archive.rar, archive.r00, archive.r01, ...
     * - ZIP: archive.z01, archive.z02, ... archive.zip
     */
    fun resolveSplitParts(targetFile: File): SplitResolution {
        val parentDir = targetFile.parentFile ?: return SplitResolution.NotSplit(targetFile)
        val name = targetFile.name

        // 1. 7-Zip split volumes (archive.7z.001, archive.7z.002, ...)
        val sevenZMatch = SEVEN_Z_SPLIT_REGEX.matchEntire(name)
        if (sevenZMatch != null) {
            val basePrefix = sevenZMatch.groupValues[1]
            val digitsLength = sevenZMatch.groupValues[2].length.coerceAtLeast(3)

            val siblingFiles = parentDir.listFiles { _, siblingName ->
                val m = SEVEN_Z_SPLIT_REGEX.matchEntire(siblingName)
                m != null && m.groupValues[1].equals(basePrefix, ignoreCase = true)
            }?.toList().orEmpty()

            if (siblingFiles.isEmpty()) return SplitResolution.NotSplit(targetFile)

            val partMap = mutableMapOf<Int, File>()
            for (f in siblingFiles) {
                val m = SEVEN_Z_SPLIT_REGEX.matchEntire(f.name)
                val partNum = m?.groupValues?.get(2)?.toIntOrNull()
                if (partNum != null) partMap[partNum] = f
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

        // 2. RAR New Style split volumes (archive.part1.rar, archive.part2.rar, archive.part01.rar, ...)
        val rarPartMatch = RAR_PART_SPLIT_REGEX.matchEntire(name)
        if (rarPartMatch != null) {
            val basePrefix = rarPartMatch.groupValues[1]
            val digitsLength = rarPartMatch.groupValues[2].length

            val siblingFiles = parentDir.listFiles { _, siblingName ->
                val m = RAR_PART_SPLIT_REGEX.matchEntire(siblingName)
                m != null && m.groupValues[1].equals(basePrefix, ignoreCase = true)
            }?.toList().orEmpty()

            if (siblingFiles.isNotEmpty()) {
                val partMap = mutableMapOf<Int, File>()
                for (f in siblingFiles) {
                    val m = RAR_PART_SPLIT_REGEX.matchEntire(f.name)
                    val partNum = m?.groupValues?.get(2)?.toIntOrNull()
                    if (partNum != null) partMap[partNum] = f
                }

                if (!partMap.containsKey(1)) {
                    val expectedFirstPart = String.format(Locale.ROOT, "%s.part%0${digitsLength}d.rar", basePrefix, 1)
                    return SplitResolution.MissingParts(expectedFirstPart, 1, siblingFiles.sortedBy { it.name })
                }

                val maxPart = partMap.keys.maxOrNull() ?: 1
                val orderedParts = mutableListOf<File>()
                var totalSize = 0L

                for (i in 1..maxPart) {
                    val partFile = partMap[i]
                    if (partFile == null) {
                        val missingName = String.format(Locale.ROOT, "%s.part%0${digitsLength}d.rar", basePrefix, i)
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

        // 3. RAR Old Style split volumes (archive.rar = part 1, archive.r00 = part 2, archive.r01 = part 3, ...)
        val rarOldMatch = RAR_OLD_SPLIT_REGEX.matchEntire(name)
        val isBaseRar = name.lowercase(Locale.ROOT).endsWith(".rar")
        if (rarOldMatch != null || isBaseRar) {
            val basePrefix = if (rarOldMatch != null) rarOldMatch.groupValues[1] else name.substring(0, name.length - 4)
            val baseRarFile = File(parentDir, "$basePrefix.rar")

            val oldSiblings = parentDir.listFiles { _, siblingName ->
                val m = RAR_OLD_SPLIT_REGEX.matchEntire(siblingName)
                m != null && m.groupValues[1].equals(basePrefix, ignoreCase = true)
            }?.toList().orEmpty()

            if (oldSiblings.isNotEmpty()) {
                val partMap = mutableMapOf<Int, File>()
                if (baseRarFile.exists()) {
                    partMap[1] = baseRarFile
                } else {
                    return SplitResolution.MissingParts("$basePrefix.rar", 1, oldSiblings.sortedBy { it.name })
                }

                for (f in oldSiblings) {
                    val m = RAR_OLD_SPLIT_REGEX.matchEntire(f.name)
                    val rIndex = m?.groupValues?.get(2)?.toIntOrNull()
                    if (rIndex != null) {
                        partMap[rIndex + 2] = f
                    }
                }

                val maxPart = partMap.keys.maxOrNull() ?: 1
                val orderedParts = mutableListOf<File>()
                var totalSize = 0L

                for (i in 1..maxPart) {
                    val partFile = partMap[i]
                    if (partFile == null) {
                        val missingName = String.format(Locale.ROOT, "%s.r%02d", basePrefix, i - 2)
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

        // 4. ZIP split volumes (archive.z01, archive.z02, ... archive.zip)
        val zipPartMatch = ZIP_SPLIT_PART_REGEX.matchEntire(name)
        val isBaseZip = name.lowercase(Locale.ROOT).endsWith(".zip")
        if (zipPartMatch != null || isBaseZip) {
            val basePrefix = if (zipPartMatch != null) zipPartMatch.groupValues[1] else name.substring(0, name.length - 4)
            val baseZipFile = File(parentDir, "$basePrefix.zip")

            val zipParts = parentDir.listFiles { _, siblingName ->
                val m = ZIP_SPLIT_PART_REGEX.matchEntire(siblingName)
                m != null && m.groupValues[1].equals(basePrefix, ignoreCase = true)
            }?.toList().orEmpty()

            if (zipParts.isNotEmpty()) {
                val partMap = mutableMapOf<Int, File>()
                for (f in zipParts) {
                    val m = ZIP_SPLIT_PART_REGEX.matchEntire(f.name)
                    val zNum = m?.groupValues?.get(2)?.toIntOrNull()
                    if (zNum != null) {
                        partMap[zNum] = f
                    }
                }

                if (!partMap.containsKey(1)) {
                    val expectedFirstPart = String.format(Locale.ROOT, "%s.z01", basePrefix)
                    return SplitResolution.MissingParts(expectedFirstPart, 1, zipParts.sortedBy { it.name })
                }

                val maxZPart = partMap.keys.maxOrNull() ?: 1
                val orderedParts = mutableListOf<File>()
                var totalSize = 0L

                for (i in 1..maxZPart) {
                    val partFile = partMap[i]
                    if (partFile == null) {
                        val missingName = String.format(Locale.ROOT, "%s.z%02d", basePrefix, i)
                        return SplitResolution.MissingParts(missingName, i, orderedParts)
                    }
                    orderedParts.add(partFile)
                    totalSize += partFile.length()
                }

                if (!baseZipFile.exists()) {
                    return SplitResolution.MissingParts(baseZipFile.name, maxZPart + 1, orderedParts)
                }
                orderedParts.add(baseZipFile)
                totalSize += baseZipFile.length()

                return SplitResolution.Success(
                    baseName = basePrefix,
                    parts = orderedParts,
                    totalSize = totalSize,
                    isSplit = true
                )
            }
        }

        // 5. Generic split volumes (archive.001, archive.002, ...)
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
                val partNum = m?.groupValues?.get(2)?.toIntOrNull()
                if (partNum != null) partMap[partNum] = f
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
