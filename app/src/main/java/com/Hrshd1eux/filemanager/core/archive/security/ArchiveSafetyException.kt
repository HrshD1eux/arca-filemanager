package com.Hrshd1eux.filemanager.core.archive.security

import java.io.IOException

sealed class ArchiveSafetyException(message: String) : IOException(message)

class PathTraversalException(entryName: String, targetPath: String) :
    ArchiveSafetyException("Path traversal security violation: Entry '$entryName' attempts to escape destination target ($targetPath)")

class SuspiciousPathException(entryName: String, reason: String) :
    ArchiveSafetyException("Suspicious entry path rejected: Entry '$entryName' contains $reason")

class ZipBombException(entryName: String, ratio: Double, bytesRead: Long) :
    ArchiveSafetyException("Decompression bomb protection triggered: Entry '$entryName' exceeded expansion ratio of $ratio:1 ($bytesRead bytes decompressed)")

class ExcessiveArchiveSizeException(cumulativeBytes: Long, maxAllowedBytes: Long) :
    ArchiveSafetyException("Archive extraction exceeded maximum allowed output size: $cumulativeBytes bytes > $maxAllowedBytes bytes")

class MalformedArchiveException(message: String) :
    ArchiveSafetyException("Malformed archive error: $message")

class SymlinkAbuseException(entryName: String, target: String) :
    ArchiveSafetyException("Symlink security violation: Entry '$entryName' targets an unverified or external path: '$target'")
