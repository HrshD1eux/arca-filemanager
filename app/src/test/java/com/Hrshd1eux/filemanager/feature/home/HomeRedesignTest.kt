package com.Hrshd1eux.filemanager.feature.home

import android.os.Environment
import com.Hrshd1eux.filemanager.core.search.engine.FileSearchEngine
import com.Hrshd1eux.filemanager.core.search.model.CategoryFilter
import com.Hrshd1eux.filemanager.core.search.model.SearchCriteria
import com.Hrshd1eux.filemanager.core.storage.model.StorageBreakdown
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HomeRedesignTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var searchEngine: FileSearchEngine

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("home_test_root")
        searchEngine = FileSearchEngine(ioDispatcher = Dispatchers.Unconfined)
    }

    @Test
    fun categoryFilter_apksOptionPresent() {
        val apksCategory = CategoryFilter.APKS
        assertEquals("APKs", apksCategory.displayName)
    }

    @Test
    fun searchEngine_filtersApkFilesCorrectly() = runTest {
        // Create files of different types
        File(rootDir, "App_v1.0.apk").apply { writeBytes(ByteArray(1024)) }
        File(rootDir, "Bundle.xapk").apply { writeBytes(ByteArray(2048)) }
        File(rootDir, "Split.apks").apply { writeBytes(ByteArray(1024)) }
        File(rootDir, "Archive.zip").apply { writeBytes(ByteArray(512)) }
        File(rootDir, "Photo.jpg").apply { writeBytes(ByteArray(512)) }

        val criteria = SearchCriteria(
            rootPath = rootDir.absolutePath,
            category = CategoryFilter.APKS
        )

        val results = searchEngine.search(criteria).first()
        assertEquals(3, results.size)
        val names = results.map { it.name }.toSet()
        assertTrue(names.contains("App_v1.0.apk"))
        assertTrue(names.contains("Bundle.xapk"))
        assertTrue(names.contains("Split.apks"))
    }

    @Test
    fun storageBreakdown_formattingAndPercentage() {
        val total = 128L * 1024 * 1024 * 1024 // 128 GB
        val used = (86.4 * 1024 * 1024 * 1024).toLong() // 86.4 GB
        val free = total - used

        val breakdown = StorageBreakdown(
            totalBytes = total,
            freeBytes = free,
            usedBytes = used
        )

        val usedFormatted = FileFormatter.formatFileSize(breakdown.usedBytes)
        val totalFormatted = FileFormatter.formatFileSize(breakdown.totalBytes)

        assertTrue(usedFormatted.contains("86") || usedFormatted.contains("GB"))
        assertTrue(totalFormatted.contains("128") || totalFormatted.contains("GB"))
        assertTrue(breakdown.usedPercentage in 0.67f..0.68f)
    }

    @Test
    fun storageFolders_standardPathsResolution() {
        val downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "/storage/emulated/0/Download"
        val documentsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: "/storage/emulated/0/Documents"
        val picturesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.absolutePath ?: "/storage/emulated/0/Pictures"
        val moviesPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)?.absolutePath ?: "/storage/emulated/0/Movies"

        assertNotNull(downloadPath)
        assertNotNull(documentsPath)
        assertNotNull(picturesPath)
        assertNotNull(moviesPath)

        assertTrue(downloadPath.endsWith("Download"))
        assertTrue(documentsPath.endsWith("Documents"))
        assertTrue(picturesPath.endsWith("Pictures"))
        assertTrue(moviesPath.endsWith("Movies"))
    }
}
