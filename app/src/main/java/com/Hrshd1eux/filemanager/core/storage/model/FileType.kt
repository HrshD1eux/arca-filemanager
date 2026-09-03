package com.Hrshd1eux.filemanager.core.storage.model

enum class FileType(val displayName: String) {
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCUMENT("Document"),
    ARCHIVE("Archive"),
    PACKAGE("Package / App"),
    CODE("Source Code"),
    TEXT("Plain Text"),
    FONT("Font"),
    UNKNOWN("Unknown")
}
