package com.Hrshd1eux.filemanager.feature.about.model

/**
 * Represents a third-party open-source library used by ARCA,
 * detailing its license, authors, and purpose.
 */
data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String,
    val description: String,
    val projectUrl: String,
    val licenseUrl: String
)

object OpenSourceRegistry {
    val libraries: List<OpenSourceLibrary> = listOf(
        OpenSourceLibrary(
            name = "Apache Commons Compress",
            author = "The Apache Software Foundation",
            license = "Apache License 2.0",
            description = "Provides decompression and packaging for Tar, 7z, Cpio, Ar, and Dump archives.",
            projectUrl = "https://commons.apache.org/proper/commons-compress/",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        ),
        OpenSourceLibrary(
            name = "Tukaani XZ for Java",
            author = "Lasse Collin",
            license = "Public Domain",
            description = "Pure Java implementation of XZ and LZMA data compression filters.",
            projectUrl = "https://tukaani.org/xz/java.html",
            licenseUrl = "https://tukaani.org/xz/java.html"
        ),
        OpenSourceLibrary(
            name = "Junrar",
            author = "Junrar Contributors",
            license = "UnRAR License / Apache 2.0",
            description = "Enables reliable extraction of RAR archives without native dependencies.",
            projectUrl = "https://github.com/junrar/junrar",
            licenseUrl = "https://raw.githubusercontent.com/junrar/junrar/master/LICENSE"
        ),
        OpenSourceLibrary(
            name = "Zip4j",
            author = "Srikanth Reddy Lingala",
            license = "Apache License 2.0",
            description = "Handles encrypted and standard ZIP archive creation and extraction locally on device.",
            projectUrl = "https://github.com/srikanth-lingala/zip4j",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        ),
        OpenSourceLibrary(
            name = "Palantir ISO File Reader",
            author = "Palantir Technologies",
            license = "Apache License 2.0",
            description = "High-performance ISO 9660 disc image structure parsing.",
            projectUrl = "https://github.com/palantir/isofilereader",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        ),
        OpenSourceLibrary(
            name = "AndroidX & Jetpack Libraries",
            author = "Google LLC / AOSP",
            license = "Apache License 2.0",
            description = "Core KTX, Lifecycle, Navigation Compose, and WorkManager background tasks.",
            projectUrl = "https://developer.android.com/jetpack",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        ),
        OpenSourceLibrary(
            name = "Jetpack Compose & Material 3",
            author = "Google LLC / AOSP",
            license = "Apache License 2.0",
            description = "Modern declarative UI toolkit and Material Design 3 guidelines.",
            projectUrl = "https://developer.android.com/jetpack/compose",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        ),
        OpenSourceLibrary(
            name = "Kotlin & Coroutines",
            author = "JetBrains s.r.o. / Kotlin Contributors",
            license = "Apache License 2.0",
            description = "Kotlin language runtime, asynchronous coroutine pipelines, and serialization.",
            projectUrl = "https://kotlinlang.org",
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        )
    )
}
