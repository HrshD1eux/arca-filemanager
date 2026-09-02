package com.Hrshd1eux.filemanager.core.storage.model

/**
 * Pure Kotlin MIME type resolver with comprehensive extension mapping.
 * Avoids stub crashes from android.webkit.MimeTypeMap during local JVM unit tests.
 */
object MimeTypeResolver {

    private val extensionMap = mapOf(
        // Documents
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "csv" to "text/csv",
        "json" to "application/json",
        "xml" to "application/xml",
        "html" to "text/html",
        "htm" to "text/html",
        "md" to "text/markdown",

        // Images
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "heic" to "image/heic",

        // Audio
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
        "flac" to "audio/flac",
        "aac" to "audio/aac",

        // Video
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "3gp" to "video/3gpp",

        // Archives
        "zip" to "application/zip",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "xz" to "application/x-xz",
        "bz2" to "application/x-bzip2",
        "rar" to "application/x-rar-compressed",

        // Packages
        "apk" to "application/vnd.android.package-archive"
    )

    fun getMimeType(extension: String?): String? {
        if (extension.isNullOrEmpty()) return null
        return extensionMap[extension.lowercase()] ?: runCatching {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        }.getOrNull()
    }
}
