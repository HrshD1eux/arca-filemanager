package com.Hrshd1eux.filemanager.core.storage.util

import com.Hrshd1eux.filemanager.core.storage.model.ArchiveFormat
import com.Hrshd1eux.filemanager.core.storage.model.FileType
import com.Hrshd1eux.filemanager.core.storage.model.MimeTypeResolver

object FileTypeResolver {

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "dng", "cr2", "nef", "arw", "svg", "ico"
    )

    private val videoExtensions = setOf(
        "mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "m4v", "wmv", "ts", "m2ts"
    )

    private val audioExtensions = setOf(
        "mp3", "m4a", "flac", "wav", "aac", "ogg", "opus", "wma", "alac", "aiff", "mid", "midi"
    )

    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub", "mobi", "csv"
    )

    private val archiveExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "tgz", "tbz2", "txz", "lz4", "lzh", "cab", "iso", "dmg"
    )

    private val packageExtensions = setOf(
        "apk", "apks", "xapk", "aab", "jar"
    )

    private val codeExtensions = setOf(
        "kt", "java", "py", "c", "cpp", "h", "hpp", "cs", "js", "ts", "jsx", "tsx", "html", "htm", "css", "scss",
        "json", "xml", "yaml", "yml", "toml", "sql", "sh", "bash", "zsh", "bat", "ps1", "gradle", "properties", "rs", "go", "swift", "rb", "php"
    )

    private val textExtensions = setOf(
        "txt", "md", "log", "conf", "cfg", "ini"
    )

    private val fontExtensions = setOf(
        "ttf", "otf", "woff", "woff2", "eot"
    )

    fun resolveType(fileName: String, mimeType: String? = null): FileType {
        val lowerName = fileName.lowercase()

        // Compound archive check (.tar.gz, .tar.bz2, .tar.xz)
        if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tar.xz")) {
            return FileType.ARCHIVE
        }

        val ext = lowerName.substringAfterLast('.', "")
        return when {
            ext in imageExtensions -> FileType.IMAGE
            ext in videoExtensions -> FileType.VIDEO
            ext in audioExtensions -> FileType.AUDIO
            ext in documentExtensions -> FileType.DOCUMENT
            ext in archiveExtensions -> FileType.ARCHIVE
            ext in packageExtensions -> FileType.PACKAGE
            ext in codeExtensions -> FileType.CODE
            ext in textExtensions -> FileType.TEXT
            ext in fontExtensions -> FileType.FONT
            mimeType != null -> resolveFromMime(mimeType)
            else -> FileType.UNKNOWN
        }
    }

    private fun resolveFromMime(mime: String): FileType {
        val lowerMime = mime.lowercase()
        return when {
            lowerMime.startsWith("image/") -> FileType.IMAGE
            lowerMime.startsWith("video/") -> FileType.VIDEO
            lowerMime.startsWith("audio/") -> FileType.AUDIO
            lowerMime == "application/pdf" || lowerMime.contains("officedocument") || lowerMime.contains("msword") -> FileType.DOCUMENT
            lowerMime.contains("zip") || lowerMime.contains("tar") || lowerMime.contains("compressed") || lowerMime.contains("archive") -> FileType.ARCHIVE
            lowerMime == "application/vnd.android.package-archive" -> FileType.PACKAGE
            lowerMime.startsWith("text/") -> FileType.TEXT
            else -> FileType.UNKNOWN
        }
    }

    fun resolveMimeType(fileName: String): String {
        val lowerName = fileName.lowercase()
        if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")) return "application/gzip"
        if (lowerName.endsWith(".tar.bz2")) return "application/x-bzip2"
        if (lowerName.endsWith(".tar.xz")) return "application/x-xz"

        val ext = lowerName.substringAfterLast('.', "")
        return MimeTypeResolver.getMimeType(ext) ?: "*/*"
    }

    fun resolveArchiveFormat(fileName: String): ArchiveFormat? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> ArchiveFormat.TAR_BZ2
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> ArchiveFormat.TAR_XZ
            lower.endsWith(".zip") -> ArchiveFormat.ZIP
            lower.endsWith(".tar") -> ArchiveFormat.TAR
            lower.endsWith(".7z") -> ArchiveFormat.SEVEN_ZIP
            lower.endsWith(".rar") -> ArchiveFormat.RAR
            lower.endsWith(".gz") -> ArchiveFormat.GZIP
            lower.endsWith(".bz2") -> ArchiveFormat.BZIP2
            lower.endsWith(".xz") -> ArchiveFormat.XZ
            lower.endsWith(".zst") -> ArchiveFormat.ZSTD
            lower.endsWith(".iso") -> ArchiveFormat.ISO
            isArchive(fileName) -> ArchiveFormat.OTHER
            else -> null
        }
    }

    fun canPreviewArchive(fileName: String): Boolean {
        return resolveArchiveFormat(fileName)?.supportsStreamingPreview == true
    }

    fun isArchive(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".tar.gz") || lower.endsWith(".tar.bz2") || lower.endsWith(".tar.xz") ||
                lower.substringAfterLast('.', "") in archiveExtensions
    }

    fun isPackage(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in packageExtensions
    }

    fun isMedia(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in imageExtensions || ext in videoExtensions || ext in audioExtensions
    }

    fun isImage(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in imageExtensions
    }

    fun isVideo(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in videoExtensions
    }

    fun isAudio(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in audioExtensions
    }

    fun isDocument(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in documentExtensions
    }
}
