package com.Hrshd1eux.filemanager.core.vault

import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry
import com.Hrshd1eux.filemanager.feature.vault.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

import com.Hrshd1eux.filemanager.core.vault.search.VaultSearchEngine

class VaultSearchTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val entries = listOf(
        VaultFileEntry(
            id = "1",
            originalName = "Secret_Passcodes.txt",
            parentVirtualPath = "Personal/Security",
            isDirectory = false,
            size = 1024L, // 1.0 KB
            mimeType = "text/plain",
            lastModified = 1700000000000L
        ),
        VaultFileEntry(
            id = "2",
            originalName = "tax_return_2025.pdf",
            parentVirtualPath = "Finance",
            isDirectory = false,
            size = 2048576L, // ~2.0 MB
            mimeType = "application/pdf",
            lastModified = 1705000000000L
        ),
        VaultFileEntry(
            id = "3",
            originalName = "Family_Holiday_Photo.jpg",
            parentVirtualPath = "Photos",
            isDirectory = false,
            size = 5242880L, // 5.0 MB
            mimeType = "image/jpeg",
            lastModified = 1710000000000L
        ),
        VaultFileEntry(
            id = "4",
            originalName = "Security",
            parentVirtualPath = "Personal",
            isDirectory = true,
            size = 0L,
            mimeType = "*/*",
            lastModified = 1700000000000L
        )
    )

    private fun filter(entries: List<VaultFileEntry>, query: String): List<VaultFileEntry> {
        return VaultSearchEngine.search(entries, query)
    }

    @Test
    fun searchVault_partialFilenameMatching_caseInsensitive() {
        val results = filter(entries, "pass")
        assertEquals(1, results.size)
        assertEquals("Secret_Passcodes.txt", results.first().originalName)

        val uppercaseResults = filter(entries, "PASS")
        assertEquals(1, uppercaseResults.size)
        assertEquals("Secret_Passcodes.txt", uppercaseResults.first().originalName)
    }

    @Test
    fun searchVault_extensionMatching() {
        val pdfResults = filter(entries, "pdf")
        assertEquals(1, pdfResults.size)
        assertEquals("tax_return_2025.pdf", pdfResults.first().originalName)

        val jpgResults = filter(entries, ".jpg")
        assertEquals(1, jpgResults.size)
        assertEquals("Family_Holiday_Photo.jpg", jpgResults.first().originalName)
    }

    @Test
    fun searchVault_virtualPathMatching() {
        // "Security" should match the folder "Security" and file in "Personal/Security"
        val securityResults = filter(entries, "Security")
        assertEquals(2, securityResults.size)
        assertTrue(securityResults.any { it.isDirectory && it.originalName == "Security" })
        assertTrue(securityResults.any { !it.isDirectory && it.originalName == "Secret_Passcodes.txt" })

        // Folders come first in sorted results
        assertTrue(securityResults.first().isDirectory)
    }

    @Test
    fun searchVault_mimeTypeAndCategoryMatching() {
        val imageResults = filter(entries, "image")
        assertEquals(1, imageResults.size)
        assertEquals("Family_Holiday_Photo.jpg", imageResults.first().originalName)
    }

    @Test
    fun searchVault_multiTokenMatching() {
        val multiResults = filter(entries, "tax pdf")
        assertEquals(1, multiResults.size)
        assertEquals("tax_return_2025.pdf", multiResults.first().originalName)

        val noMatch = filter(entries, "tax pass")
        assertEquals(0, noMatch.size)
    }

    @Test
    fun searchVault_emptyQuery_returnsEmpty() {
        val emptyResults = filter(entries, "")
        assertTrue(emptyResults.isEmpty())

        val whitespaceResults = filter(entries, "   ")
        assertTrue(whitespaceResults.isEmpty())
    }
}
