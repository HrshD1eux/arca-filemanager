package com.Hrshd1eux.filemanager.core.cleaner.model

enum class LargeFileThreshold(
    val minBytes: Long,
    val displayName: String
) {
    MB_25(25L * 1024L * 1024L, "> 25 MB"),
    MB_50(50L * 1024L * 1024L, "> 50 MB"),
    MB_100(100L * 1024L * 1024L, "> 100 MB"),
    MB_500(500L * 1024L * 1024L, "> 500 MB"),
    GB_1(1024L * 1024L * 1024L, "> 1 GB")
}
