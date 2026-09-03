package com.Hrshd1eux.filemanager.core.archive.security

import java.io.File

object ArchiveSafetyGuard {

    const val MAX_ENTRIES_PER_ARCHIVE = 50_000
    const val MAX_SINGLE_ENTRY_BYTES = 5L * 1024 * 1024 * 1024 // 5 GB
    const val MAX_CUMULATIVE_SESSION_BYTES = 25L * 1024 * 1024 * 1024 // 25 GB
    const val MAX_EXPANSION_RATIO = 100.0
    const val MIN_BYTES_BEFORE_RATIO_CHECK = 10L * 1024 * 1024 // 10 MB

    private val WINDOWS_RESERVED_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    /**
     * Validates archive-level metadata before extraction begins.
     */
    fun validateArchiveHeader(totalEntries: Int) {
        if (totalEntries < 0) {
            throw MalformedArchiveException("Negative entries count ($totalEntries)")
        }
        if (totalEntries > MAX_ENTRIES_PER_ARCHIVE) {
            throw MalformedArchiveException("Archive exceeds maximum entry limit ($totalEntries > $MAX_ENTRIES_PER_ARCHIVE)")
        }
    }

    /**
     * Sanitizes and validates entry name against suspicious characters, NUL bytes, and device names.
     */
    fun sanitizeAndValidateEntryName(rawName: String): String {
        if (rawName.contains('\u0000')) {
            throw SuspiciousPathException(rawName, "NUL byte injection detected")
        }

        // Check for control characters
        for (char in rawName) {
            if (char.code in 1..31 && char != '\t' && char != '\n' && char != '\r') {
                throw SuspiciousPathException(rawName, "illegal control character (code: ${char.code})")
            }
        }

        val normalized = rawName.replace('\\', '/')
        val segments = normalized.split('/')

        for (segment in segments) {
            val baseName = segment.substringBefore('.').uppercase()
            if (WINDOWS_RESERVED_NAMES.contains(baseName)) {
                throw SuspiciousPathException(rawName, "reserved Windows device name '$segment'")
            }
        }

        return normalized.trimStart('/')
    }

    /**
     * Resolves target file and enforces that canonical path remains strictly inside destination directory.
     * Prevents Zip-Slip, absolute path escaping, and symlink breakout.
     */
    fun resolveAndValidateTarget(entryName: String, destinationDir: File): File {
        val cleanName = sanitizeAndValidateEntryName(entryName)
        val targetFile = File(destinationDir, cleanName)

        val destCanonical = destinationDir.canonicalFile
        val targetCanonical = targetFile.canonicalFile

        val destCanonicalPath = destCanonical.path
        val targetCanonicalPath = targetCanonical.path

        val isDirectChildOrDescendant = targetCanonicalPath.startsWith(destCanonicalPath + File.separator) ||
                targetCanonicalPath == destCanonicalPath

        if (!isDirectChildOrDescendant) {
            throw PathTraversalException(entryName, targetCanonicalPath)
        }

        // Verify intermediate ancestors do not resolve outside destination root (Symlink defense)
        var currentParent: File? = targetCanonical.parentFile
        while (currentParent != null && currentParent.exists() && currentParent != destCanonical) {
            val parentCanonical = currentParent.canonicalPath
            if (!parentCanonical.startsWith(destCanonicalPath)) {
                throw SymlinkAbuseException(entryName, parentCanonical)
            }
            currentParent = currentParent.parentFile
        }

        return targetFile
    }

    /**
     * Real-time monitoring of decompressed streaming byte output to prevent Zip Bombs / enormous expansion.
     */
    fun validateExpansionStream(
        entryName: String,
        uncompressedBytesRead: Long,
        compressedEntrySize: Long,
        cumulativeUncompressedBytes: Long
    ) {
        if (uncompressedBytesRead > MAX_SINGLE_ENTRY_BYTES) {
            throw ExcessiveArchiveSizeException(uncompressedBytesRead, MAX_SINGLE_ENTRY_BYTES)
        }

        if (cumulativeUncompressedBytes > MAX_CUMULATIVE_SESSION_BYTES) {
            throw ExcessiveArchiveSizeException(cumulativeUncompressedBytes, MAX_CUMULATIVE_SESSION_BYTES)
        }

        if (uncompressedBytesRead > MIN_BYTES_BEFORE_RATIO_CHECK && compressedEntrySize > 0) {
            val ratio = uncompressedBytesRead.toDouble() / compressedEntrySize.toDouble()
            if (ratio > MAX_EXPANSION_RATIO) {
                throw ZipBombException(entryName, ratio, uncompressedBytesRead)
            }
        }
    }
}
