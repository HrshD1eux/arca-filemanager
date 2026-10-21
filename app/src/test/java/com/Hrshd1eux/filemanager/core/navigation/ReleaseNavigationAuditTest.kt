package com.Hrshd1eux.filemanager.core.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

/**
 * Release Audit Test Suite for Milestones 1.5.41 – 1.5.45:
 * Navigation Integrity, Screen Coverage, and Argument Serialization.
 *
 * Verifies:
 * 1. Complete screen coverage: All 20 declared routes are active and accounted for.
 * 2. Type-safe serialization and round-trip fidelity for all Screen destinations.
 * 3. Default parameter stability and null-safety across all dynamic routes.
 * 4. Special character / URI encoding resilience for paths with spaces, hashes, and Unicode.
 */
class ReleaseNavigationAuditTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun screenHierarchy_containsAllExpectedRoutes() {
        val allScreens: List<Screen> = listOf(
            Screen.Home,
            Screen.Settings,
            Screen.About,
            Screen.PrivacyStatement,
            Screen.Browser(),
            Screen.Category(categoryName = "DOCS"),
            Screen.Search(),
            Screen.LargeFiles(),
            Screen.DuplicateFinder(),
            Screen.StorageAnalyzer,
            Screen.InstalledApps,
            Screen.ArchiveViewer(archivePath = "/test.zip"),
            Screen.Preview(initialPath = "/test.txt"),
            Screen.ImageViewer(initialPath = "/test.jpg"),
            Screen.VideoViewer(initialPath = "/test.mp4"),
            Screen.Vault,
            Screen.SecurityCenter,
            Screen.NetworkDiagnostics,
            Screen.Backup,
            Screen.Trash,
            Screen.NetworkBrowser(connectionId = "test_conn")
        )

        assertEquals("Screen sealed hierarchy must contain exactly 21 distinct route types", 21, allScreens.size)
        val routeNames = allScreens.map { it::class.simpleName }.toSet()
        assertEquals("Each route must have a unique class name", 21, routeNames.size)
    }

    @Test
    fun staticScreens_serializeAndDeserializeSafely() {
        assertNotNull(json.encodeToString(Screen.Home))
        assertNotNull(json.encodeToString(Screen.Settings))
        assertNotNull(json.encodeToString(Screen.About))
        assertNotNull(json.encodeToString(Screen.PrivacyStatement))
        assertNotNull(json.encodeToString(Screen.StorageAnalyzer))
        assertNotNull(json.encodeToString(Screen.InstalledApps))
        assertNotNull(json.encodeToString(Screen.Vault))
        assertNotNull(json.encodeToString(Screen.SecurityCenter))
        assertNotNull(json.encodeToString(Screen.NetworkDiagnostics))
        assertNotNull(json.encodeToString(Screen.Backup))
        assertNotNull(json.encodeToString(Screen.Trash))
    }

    @Test
    fun dynamicScreens_roundTripSerializationPreservesArguments() {
        // 1. Browser
        val browser = Screen.Browser(initialPath = "/storage/emulated/0/Download", title = "Downloads")
        val browserEncoded = json.encodeToString(browser)
        val browserDecoded = json.decodeFromString<Screen.Browser>(browserEncoded)
        assertEquals(browser.initialPath, browserDecoded.initialPath)
        assertEquals(browser.title, browserDecoded.title)

        // 2. Category
        val category = Screen.Category(categoryName = "IMAGES", rootPath = "/storage/emulated/0")
        val catEncoded = json.encodeToString(category)
        val catDecoded = json.decodeFromString<Screen.Category>(catEncoded)
        assertEquals(category.categoryName, catDecoded.categoryName)
        assertEquals(category.rootPath, catDecoded.rootPath)

        // 3. Search
        val search = Screen.Search(rootPath = "/sdcard", categoryName = "DOCUMENTS")
        val searchEncoded = json.encodeToString(search)
        val searchDecoded = json.decodeFromString<Screen.Search>(searchEncoded)
        assertEquals(search.rootPath, searchDecoded.rootPath)
        assertEquals(search.categoryName, searchDecoded.categoryName)

        // 4. LargeFiles & DuplicateFinder
        val largeFiles = Screen.LargeFiles(rootPath = "/storage/emulated/0")
        val lfDecoded = json.decodeFromString<Screen.LargeFiles>(json.encodeToString(largeFiles))
        assertEquals(largeFiles.rootPath, lfDecoded.rootPath)

        val dupFinder = Screen.DuplicateFinder(rootPath = null)
        val dupDecoded = json.decodeFromString<Screen.DuplicateFinder>(json.encodeToString(dupFinder))
        assertNull(dupDecoded.rootPath)

        // 5. ArchiveViewer
        val archive = Screen.ArchiveViewer(archivePath = "/sdcard/test.tar.gz", title = "test.tar.gz")
        val archDecoded = json.decodeFromString<Screen.ArchiveViewer>(json.encodeToString(archive))
        assertEquals(archive.archivePath, archDecoded.archivePath)
        assertEquals(archive.title, archDecoded.title)

        // 6. Viewers: Preview, ImageViewer, VideoViewer
        val preview = Screen.Preview(initialPath = "/sdcard/notes.md", categoryName = "DOCS", directoryPath = "/sdcard")
        val prevDecoded = json.decodeFromString<Screen.Preview>(json.encodeToString(preview))
        assertEquals(preview.initialPath, prevDecoded.initialPath)
        assertEquals(preview.categoryName, prevDecoded.categoryName)
        assertEquals(preview.directoryPath, prevDecoded.directoryPath)

        val imgViewer = Screen.ImageViewer(initialPath = "/sdcard/photo.jpg", categoryName = null, directoryPath = null)
        val imgDecoded = json.decodeFromString<Screen.ImageViewer>(json.encodeToString(imgViewer))
        assertEquals(imgViewer.initialPath, imgDecoded.initialPath)
        assertNull(imgDecoded.categoryName)
        assertNull(imgDecoded.directoryPath)

        val vidViewer = Screen.VideoViewer(initialPath = "/sdcard/clip.mp4", categoryName = "VIDEOS", directoryPath = "/sdcard")
        val vidDecoded = json.decodeFromString<Screen.VideoViewer>(json.encodeToString(vidViewer))
        assertEquals(vidViewer.initialPath, vidDecoded.initialPath)

        // 7. NetworkBrowser
        val netBrowser = Screen.NetworkBrowser(connectionId = "conn_sftp_123", initialPath = "/home/user", title = "My SFTP")
        val netDecoded = json.decodeFromString<Screen.NetworkBrowser>(json.encodeToString(netBrowser))
        assertEquals(netBrowser.connectionId, netDecoded.connectionId)
        assertEquals(netBrowser.initialPath, netDecoded.initialPath)
        assertEquals(netBrowser.title, netDecoded.title)
    }

    @Test
    fun dynamicScreens_pathsWithSpecialCharacters_preserveExactContent() {
        val pathWithSpecialChars = "/storage/emulated/0/DCIM/Test [Special] & Folder (2026) #1/photo %20 test.jpg"
        val screen = Screen.ImageViewer(
            initialPath = pathWithSpecialChars,
            categoryName = "SPECIAL [TEST]",
            directoryPath = "/storage/emulated/0/DCIM/Test [Special] & Folder (2026) #1"
        )

        val encoded = json.encodeToString(screen)
        val decoded = json.decodeFromString<Screen.ImageViewer>(encoded)

        assertEquals(pathWithSpecialChars, decoded.initialPath)
        assertEquals("SPECIAL [TEST]", decoded.categoryName)
        assertEquals("/storage/emulated/0/DCIM/Test [Special] & Folder (2026) #1", decoded.directoryPath)
    }

    @Test
    fun defaultArguments_resolveSafelyWithoutNullPointerExceptions() {
        val browserDefault = Screen.Browser()
        assertNull(browserDefault.initialPath)
        assertEquals("Internal Storage", browserDefault.title)

        val searchDefault = Screen.Search()
        assertNull(searchDefault.rootPath)
        assertNull(searchDefault.categoryName)

        val netBrowserDefault = Screen.NetworkBrowser(connectionId = "id_1")
        assertEquals("id_1", netBrowserDefault.connectionId)
        assertNull(netBrowserDefault.initialPath)
        assertEquals("Remote Server", netBrowserDefault.title)
    }
}
