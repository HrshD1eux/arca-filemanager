package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.RarEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class RarEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rarEngine: RarEngine
    private lateinit var sampleRarFile: File

    // Authentic Junrar RAR test fixture (120 bytes) containing directory 'foo' and file 'foo\bar.txt' ("test")
    private val sampleRarBase64 = "UmFyIRoHAM+QcwAADQAAAAAAAAB8zXQgkC0ADQAAAAQAAAAD4Tl7zCeTJEEdMwsAtIEAAGZvb1xiYXIudHh0AMAACL8IrvLDGH6f/ZLdiiN04IAjAAAAAAAAAAAAAwAAAAAnkyRBFDADAP1BAABmb2/EPXsAQAcA"

    @Before
    fun setUp() {
        rarEngine = RarEngine(Dispatchers.Unconfined)
        sampleRarFile = File(tempFolder.root, "sample.rar")
        val decoded = Base64.getDecoder().decode(sampleRarBase64)
        sampleRarFile.writeBytes(decoded)
    }

    @Test
    fun listEntries_validRar_returnsCorrectEntries() = runTest {
        val entries = rarEngine.listEntries(sampleRarFile)
        assertFalse("Entries should not be empty", entries.isEmpty())

        // The archive contains directory 'foo' and file 'foo/bar.txt'
        val paths = entries.map { it.path }
        assertTrue("Should contain foo/bar.txt", paths.any { it == "foo/bar.txt" })

        val fileEntry = entries.first { it.name == "bar.txt" }
        assertFalse(fileEntry.isDirectory)
        assertEquals(4L, fileEntry.uncompressedSize)
    }

    @Test
    fun extractAll_validRar_extractsSuccessfully() = runTest {
        val destDir = tempFolder.newFolder("ExtractedAll")
        val result = rarEngine.extractAll(
            rarFile = sampleRarFile,
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(result.isSuccess)
        val extractedCount = result.getOrNull()
        assertNotNull(extractedCount)
        assertTrue(extractedCount!! > 0)

        val extractedFile = File(destDir, "foo/bar.txt")
        assertTrue("Extracted file foo/bar.txt must exist", extractedFile.exists())
        assertEquals("baz\n", extractedFile.readText().replace("\r\n", "\n"))
    }

    @Test
    fun extractEntries_validRar_extractsSelectedOnly() = runTest {
        val destDir = tempFolder.newFolder("ExtractedSelected")
        val result = rarEngine.extractEntries(
            rarFile = sampleRarFile,
            entryPaths = setOf("foo/bar.txt"),
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(result.isSuccess)
        val extractedFile = File(destDir, "bar.txt")
        assertTrue("Extracted file bar.txt must exist", extractedFile.exists())
        assertEquals("baz\n", extractedFile.readText().replace("\r\n", "\n"))
    }

    @Test
    fun testArchive_validRar_returnsPassedReport() = runTest {
        val result = rarEngine.testArchive(sampleRarFile)
        assertTrue(result.isSuccess)

        val report = result.getOrNull()
        assertNotNull(report)
        assertTrue("Archive integrity should pass", report!!.isPassed)
        assertTrue("Corrupted list should be empty", report.corruptedEntries.isEmpty())
        assertTrue("Verified entries count should be > 0", report.verifiedEntries > 0)
    }

    @Test
    fun getArchiveProperties_validRar_computesAccurateMetrics() = runTest {
        val props = rarEngine.getArchiveProperties(sampleRarFile)
        assertEquals("sample.rar", props.archiveName)
        assertTrue("Should detect total files", props.totalFiles >= 1)
        assertTrue("Uncompressed size should be > 0", props.uncompressedSize >= 4L)
        assertEquals(sampleRarFile.length(), props.compressedSize)
    }

    @Test
    fun createRar_throwsUnsupportedOperationException_adheringToLicense() {
        try {
            rarEngine.createRar(
                sources = listOf(File("test.txt")),
                destinationRar = File("out.rar")
            )
            fail("createRar must throw UnsupportedOperationException under Roshal UnRAR license")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("Roshal UnRAR license") == true)
        }
    }

    @Test
    fun listEntries_nonExistentFile_returnsEmpty() = runTest {
        val missing = File(tempFolder.root, "missing.rar")
        val entries = rarEngine.listEntries(missing)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun getArchiveProperties_nonExistentFile_returnsEmptyProps() = runTest {
        val missing = File(tempFolder.root, "missing.rar")
        val props = rarEngine.getArchiveProperties(missing)
        assertEquals(0, props.totalFiles)
        assertEquals(0L, props.uncompressedSize)
    }
}
