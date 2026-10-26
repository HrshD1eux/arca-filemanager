package com.Hrshd1eux.filemanager.core.intent

import com.Hrshd1eux.filemanager.core.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ViewIntentResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolveDestination_forImage_returnsScreenPreview() {
        val file = tempFolder.newFile("photo.jpg")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.Preview", destination is Screen.Preview)
        val preview = destination as Screen.Preview
        assertEquals(file.absolutePath, preview.initialPath)
    }

    @Test
    fun resolveDestination_forVideo_returnsScreenPreview() {
        val file = tempFolder.newFile("movie.mp4")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.Preview", destination is Screen.Preview)
        val preview = destination as Screen.Preview
        assertEquals(file.absolutePath, preview.initialPath)
    }

    @Test
    fun resolveDestination_forAudio_returnsScreenPreview() {
        val file = tempFolder.newFile("song.mp3")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.Preview", destination is Screen.Preview)
        val preview = destination as Screen.Preview
        assertEquals(file.absolutePath, preview.initialPath)
    }

    @Test
    fun resolveDestination_forPdf_returnsScreenPreview() {
        val file = tempFolder.newFile("document.pdf")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.Preview", destination is Screen.Preview)
        val preview = destination as Screen.Preview
        assertEquals(file.absolutePath, preview.initialPath)
    }

    @Test
    fun resolveDestination_forTextFile_returnsScreenPreview() {
        val file = tempFolder.newFile("notes.txt")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.Preview", destination is Screen.Preview)
        val preview = destination as Screen.Preview
        assertEquals(file.absolutePath, preview.initialPath)
    }

    @Test
    fun resolveDestination_forZipArchive_returnsScreenArchiveViewer() {
        val file = tempFolder.newFile("backup.zip")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.ArchiveViewer", destination is Screen.ArchiveViewer)
        val archiveViewer = destination as Screen.ArchiveViewer
        assertEquals(file.absolutePath, archiveViewer.archivePath)
        assertEquals("backup.zip", archiveViewer.title)
    }

    @Test
    fun resolveDestination_for7zArchive_returnsScreenArchiveViewer() {
        val file = tempFolder.newFile("archive.7z")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.ArchiveViewer", destination is Screen.ArchiveViewer)
        val archiveViewer = destination as Screen.ArchiveViewer
        assertEquals(file.absolutePath, archiveViewer.archivePath)
    }

    @Test
    fun resolveDestination_forTarGzArchive_returnsScreenArchiveViewer() {
        val file = tempFolder.newFile("files.tar.gz")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.ArchiveViewer", destination is Screen.ArchiveViewer)
        val archiveViewer = destination as Screen.ArchiveViewer
        assertEquals(file.absolutePath, archiveViewer.archivePath)
    }

    @Test
    fun resolveDestination_forApk_returnsScreenPreview() {
        val file = tempFolder.newFile("sample.apk")
        val destination = ViewIntentResolver.resolveDestination(file.absolutePath)

        assertTrue("Destination must be Screen.Preview", destination is Screen.Preview)
        val preview = destination as Screen.Preview
        assertEquals(file.absolutePath, preview.initialPath)
    }

    @Test
    fun manifestContainsActionViewIntentFiltersForAllSupportedFormats() {
        val manifestFile = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("app/src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("../app/src/main/AndroidManifest.xml")

        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()

        assertTrue("Must declare android.intent.action.VIEW", content.contains("android.intent.action.VIEW"))
        assertTrue("Must declare DEFAULT category", content.contains("android.intent.category.DEFAULT"))
        assertTrue("Must declare BROWSABLE category", content.contains("android.intent.category.BROWSABLE"))

        // MIME filters
        assertTrue("Must declare image/* MIME filter", content.contains("android:mimeType=\"image/*\""))
        assertTrue("Must declare video/* MIME filter", content.contains("android:mimeType=\"video/*\""))
        assertTrue("Must declare audio/* MIME filter", content.contains("android:mimeType=\"audio/*\""))
        assertTrue("Must declare text/* MIME filter", content.contains("android:mimeType=\"text/*\""))
        assertTrue("Must declare application/pdf MIME filter", content.contains("android:mimeType=\"application/pdf\""))
        assertTrue("Must declare application/zip MIME filter", content.contains("android:mimeType=\"application/zip\""))
        assertTrue("Must declare application/x-7z-compressed MIME filter", content.contains("android:mimeType=\"application/x-7z-compressed\""))
        assertTrue("Must declare application/vnd.android.package-archive MIME filter",
            content.contains("android:mimeType=\"application/vnd.android.package-archive\""))

        // Schemes
        assertTrue("Must support content scheme", content.contains("android:scheme=\"content\""))
        assertTrue("Must support file scheme", content.contains("android:scheme=\"file\""))

        // SingleTop launch mode
        assertTrue("MainActivity must have singleTop launchMode", content.contains("android:launchMode=\"singleTop\""))
    }

    @Test
    fun mainActivity_implementsOnNewIntentForActionView() {
        val activityFile = File("src/main/java/com/Hrshd1eux/filemanager/MainActivity.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/Hrshd1eux/filemanager/MainActivity.kt").takeIf { it.exists() }
            ?: File("../app/src/main/java/com/Hrshd1eux/filemanager/MainActivity.kt")

        assertTrue("MainActivity.kt must exist", activityFile.exists())
        val content = activityFile.readText()

        assertTrue("MainActivity must override onNewIntent", content.contains("override fun onNewIntent"))
        assertTrue("MainActivity must check ACTION_VIEW", content.contains("Intent.ACTION_VIEW"))
        assertTrue("MainActivity must connect incomingIntent to ArcaNavHost", content.contains("incomingIntent = pendingIntent"))
    }
}
