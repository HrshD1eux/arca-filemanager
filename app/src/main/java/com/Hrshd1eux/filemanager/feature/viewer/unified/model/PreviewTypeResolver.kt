package com.Hrshd1eux.filemanager.feature.viewer.unified.model

import com.Hrshd1eux.filemanager.core.storage.util.FileTypeResolver

/**
 * Pure Kotlin resolver determining the [PreviewType] for a given file name and MIME type.
 */
object PreviewTypeResolver {

    private val textExtensions = setOf(
        "txt", "md", "markdown", "log", "conf", "cfg", "ini", "properties",
        "json", "xml", "yaml", "yml", "toml", "csv", "tsv",
        "kt", "kts", "java", "py", "c", "cpp", "h", "hpp", "cs",
        "js", "ts", "jsx", "tsx", "html", "htm", "css", "scss", "sass", "less",
        "sql", "sh", "bash", "zsh", "bat", "cmd", "ps1", "gradle", "rs", "go",
        "swift", "rb", "php", "dart", "lua", "r", "dockerfile", "env"
    )

    fun resolve(fileName: String, mimeType: String? = null): PreviewType {
        val lowerName = fileName.lowercase()
        val ext = lowerName.substringAfterLast('.', "")

        if (lowerName.endsWith(".pdf") || mimeType == "application/pdf") {
            return PreviewType.PDF
        }

        if (FileTypeResolver.isImage(fileName) || mimeType?.startsWith("image/") == true) {
            return PreviewType.IMAGE
        }

        if (FileTypeResolver.isVideo(fileName) || mimeType?.startsWith("video/") == true) {
            return PreviewType.VIDEO
        }

        if (FileTypeResolver.isAudio(fileName) || mimeType?.startsWith("audio/") == true) {
            return PreviewType.AUDIO
        }

        if (ext in textExtensions ||
            mimeType?.startsWith("text/") == true ||
            mimeType == "application/json" ||
            mimeType == "application/xml" ||
            mimeType == "application/javascript"
        ) {
            return PreviewType.TEXT
        }

        return PreviewType.UNSUPPORTED
    }
}
