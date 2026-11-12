package com.Hrshd1eux.filemanager.core.storage.util

import android.os.Environment
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import java.io.File

/**
 * Resolved target when jumping directly to a filesystem path pasted or entered by the user.
 */
sealed interface PathJumpTarget {
    val displayPath: String

    data class Directory(
        val path: String,
        val displayName: String
    ) : PathJumpTarget {
        override val displayPath: String get() = path
    }

    data class DocumentFile(
        val filePath: String,
        val parentPath: String,
        val fileName: String,
        val sizeBytes: Long,
        val lastModified: Long
    ) : PathJumpTarget {
        override val displayPath: String get() = filePath

        fun toStorageNode(): StorageNode {
            val ext = File(fileName).extension
            val mime = MimeTypeResolver.getMimeType(ext) ?: FileTypeResolver.resolveMimeType(fileName)
            return StorageNode(
                id = filePath,
                name = fileName,
                path = filePath,
                isDirectory = false,
                size = sizeBytes,
                lastModified = lastModified,
                mimeType = mime
            )
        }
    }
}

sealed interface PathResolutionResult {
    data class Valid(val target: PathJumpTarget) : PathResolutionResult
    data class Invalid(val reason: String) : PathResolutionResult
}

/**
 * Normalizes and validates raw path strings pasted from clipboard or typed into search / "Go to Path".
 */
object PathJumpResolver {

    fun defaultExternalStorageRoot(): String {
        return runCatching {
            Environment.getExternalStorageDirectory()?.absolutePath
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "/storage/emulated/0"
    }

    fun normalizeRawPath(
        rawInput: String,
        externalStorageRoot: String = defaultExternalStorageRoot()
    ): String {
        var text = rawInput.trim()

        // Strip matching wrapping quotes if copied as a quoted path
        if (text.length >= 2 &&
            ((text.startsWith("\"") && text.endsWith("\"")) ||
                (text.startsWith("'") && text.endsWith("'")))
        ) {
            text = text.substring(1, text.length - 1).trim()
        }

        // Convert file:// URIs to plain paths
        if (text.startsWith("file://", ignoreCase = true)) {
            text = text.removePrefix("file://").removePrefix("FILE://")
            // Handle file://localhost/... if present
            if (text.startsWith("localhost/", ignoreCase = true)) {
                text = text.substring("localhost".length)
            }
        }

        // Expand common /sdcard alias
        if (text == "/sdcard" || text == "/sdcard/") {
            text = externalStorageRoot
        } else if (text.startsWith("/sdcard/")) {
            text = externalStorageRoot.trimEnd('/') + text.removePrefix("/sdcard")
        }

        if (text.length > 1) {
            text = text.trimEnd('/')
        }
        return text
    }

    fun looksLikeExplicitPath(rawInput: String): Boolean {
        val trimmed = rawInput.trim().trim('"', '\'')
        val isPosixPath = trimmed.startsWith("/")
        val isFileUri = trimmed.startsWith("file://", ignoreCase = true)
        val isWindowsDrivePath = trimmed.length >= 3 &&
            trimmed[0].isLetter() &&
            trimmed[1] == ':' &&
            (trimmed[2] == '\\' || trimmed[2] == '/')
        return isPosixPath || isFileUri || isWindowsDrivePath
    }

    fun isProtectedVaultPath(normalizedPath: String): Boolean {
        val forward = normalizedPath.replace('\\', '/')
        return forward.contains("/vault/", ignoreCase = true) ||
            forward.endsWith("/vault", ignoreCase = true) ||
            forward.contains("/.vault/", ignoreCase = true) ||
            forward.endsWith("/.vault", ignoreCase = true)
    }

    fun resolve(
        rawInput: String,
        externalStorageRoot: String = defaultExternalStorageRoot()
    ): PathResolutionResult {
        val normalized = normalizeRawPath(rawInput, externalStorageRoot)
        if (normalized.isEmpty()) {
            return PathResolutionResult.Invalid("Enter or paste a folder or file path")
        }

        if (!looksLikeExplicitPath(normalized) || !File(normalized).isAbsolute) {
            return PathResolutionResult.Invalid("Path must start with / (for example $externalStorageRoot/Download)")
        }

        if (isProtectedVaultPath(normalized)) {
            return PathResolutionResult.Invalid("Vault directories cannot be opened directly")
        }

        val targetFile = File(normalized)
        if (!targetFile.exists()) {
            return PathResolutionResult.Invalid("Path does not exist")
        }

        return if (targetFile.isDirectory) {
            PathResolutionResult.Valid(
                PathJumpTarget.Directory(
                    path = targetFile.absolutePath,
                    displayName = targetFile.name.ifEmpty { "Internal Storage" }
                )
            )
        } else {
            PathResolutionResult.Valid(
                PathJumpTarget.DocumentFile(
                    filePath = targetFile.absolutePath,
                    parentPath = targetFile.parent ?: externalStorageRoot,
                    fileName = targetFile.name,
                    sizeBytes = targetFile.length(),
                    lastModified = targetFile.lastModified()
                )
            )
        }
    }
}
