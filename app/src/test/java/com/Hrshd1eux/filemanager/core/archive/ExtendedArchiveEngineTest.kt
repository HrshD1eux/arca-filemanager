package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.ExtendedArchiveEngine
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

class ExtendedArchiveEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var extendedArchiveEngine: ExtendedArchiveEngine
    private lateinit var sampleArjFile: File
    private lateinit var sampleArFile: File

    @Before
    fun setUp() {
        extendedArchiveEngine = ExtendedArchiveEngine(Dispatchers.Unconfined)
        sampleArjFile = File(tempFolder.root, "sample.arj")
        sampleArFile = File(tempFolder.root, "sample.ar")

        copyFixture("bla.arj", sampleArjFile)
        copyFixture("bla.ar", sampleArFile)
    }

    private fun copyFixture(resourceName: String, destination: File) {
        val stream = javaClass.classLoader?.getResourceAsStream(resourceName)
        if (stream != null) {
            stream.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            val candidatePaths = listOf(
                File("src/test/resources/$resourceName"),
                File("app/src/test/resources/$resourceName"),
                File("../app/src/test/resources/$resourceName")
            )
            val source = candidatePaths.firstOrNull { it.exists() }
                ?: error("Test fixture $resourceName could not be located")
            source.copyTo(destination, overwrite = true)
        }
    }

    @Test
    fun arj_listEntries_validArj_returnsEntries() = runTest {
        val entries = extendedArchiveEngine.listEntries(sampleArjFile)
        assertFalse("ARJ entries should not be empty", entries.isEmpty())

        for (entry in entries) {
            assertTrue("Path must not be empty", entry.path.isNotEmpty())
            assertTrue("Name must not be empty", entry.name.isNotEmpty())
        }
    }

    @Test
    fun arj_extractAll_validArj_extractsSuccessfully() = runTest {
        val destDir = tempFolder.newFolder("ExtractedArjAll")
        val result = extendedArchiveEngine.extractAll(
            archiveFile = sampleArjFile,
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(result.isSuccess)
        val extractedCount = result.getOrNull()
        assertNotNull(extractedCount)
        assertTrue(extractedCount!! > 0)

        val extractedFiles = destDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("Extracted files must exist on disk", extractedFiles.isNotEmpty())
    }

    @Test
    fun arj_testArchive_validArj_returnsPassedReport() = runTest {
        val result = extendedArchiveEngine.testArchive(sampleArjFile)
        assertTrue(result.isSuccess)

        val report = result.getOrNull()
        assertNotNull(report)
        assertTrue("ARJ integrity test should pass", report!!.isPassed)
        assertTrue("Corrupted entries should be empty", report.corruptedEntries.isEmpty())
        assertTrue("Verified entries should be > 0", report.verifiedEntries > 0)
    }

    @Test
    fun ar_listEntries_and_extractAll_validAr_extractsSuccessfully() = runTest {
        val entries = extendedArchiveEngine.listEntries(sampleArFile)
        assertFalse("AR entries should not be empty", entries.isEmpty())

        val destDir = tempFolder.newFolder("ExtractedArAll")
        val result = extendedArchiveEngine.extractAll(
            archiveFile = sampleArFile,
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(result.isSuccess)
        val count = result.getOrNull()
        assertNotNull(count)
        assertTrue(count!! > 0)
    }

    @Test
    fun deb_package_treatedAsAr_extractsSuccessfully() = runTest {
        val debFile = File(tempFolder.root, "test-package.deb")
        sampleArFile.copyTo(debFile, overwrite = true)

        val entries = extendedArchiveEngine.listEntries(debFile)
        assertFalse("Debian package entries should not be empty", entries.isEmpty())

        val destDir = tempFolder.newFolder("ExtractedDeb")
        val result = extendedArchiveEngine.extractAll(
            archiveFile = debFile,
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun cpio_create_list_extract_roundTrip() = runTest {
        val sourceDir = tempFolder.newFolder("CpioSource")
        val file1 = File(sourceDir, "hello.txt").apply { writeText("Hello CPIO") }
        val subDir = File(sourceDir, "sub").apply { mkdirs() }
        val file2 = File(subDir, "world.txt").apply { writeText("World CPIO") }

        val destCpio = File(tempFolder.root, "archive.cpio")
        val createResult = extendedArchiveEngine.createCpio(
            sources = listOf(file1, subDir),
            destinationCpio = destCpio
        )

        assertTrue("CPIO creation must succeed", createResult.isSuccess)
        assertTrue("CPIO file must exist on disk", destCpio.exists())
        assertTrue("CPIO size must be > 0", destCpio.length() > 0L)

        // List entries
        val entries = extendedArchiveEngine.listEntries(destCpio)
        assertTrue("CPIO should have at least 2 entries", entries.size >= 2)

        // Extract all
        val extractDir = tempFolder.newFolder("ExtractedCpio")
        val extractResult = extendedArchiveEngine.extractAll(
            archiveFile = destCpio,
            destinationDir = extractDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )
        assertTrue(extractResult.isSuccess)

        val extractedFiles = extractDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("Extracted files on disk should be >= 2", extractedFiles.size >= 2)
    }

    @Test
    fun cpio_extractEntries_selectedOnly() = runTest {
        val sourceDir = tempFolder.newFolder("CpioSelectSource")
        val fileA = File(sourceDir, "fileA.txt").apply { writeText("File A content") }
        val fileB = File(sourceDir, "fileB.txt").apply { writeText("File B content") }

        val destCpio = File(tempFolder.root, "archive_select.cpio")
        extendedArchiveEngine.createCpio(
            sources = listOf(fileA, fileB),
            destinationCpio = destCpio
        ).getOrThrow()

        val extractDir = tempFolder.newFolder("ExtractedCpioSelect")
        val result = extendedArchiveEngine.extractEntries(
            archiveFile = destCpio,
            entryPaths = setOf("fileA.txt"),
            destinationDir = extractDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertTrue("Only fileA.txt should be extracted", File(extractDir, "fileA.txt").exists())
    }

    @Test
    fun getArchiveProperties_returnsAccurateMetrics() = runTest {
        val props = extendedArchiveEngine.getArchiveProperties(sampleArjFile)
        assertEquals("sample.arj", props.archiveName)
        assertTrue("Total files should be >= 1", props.totalFiles >= 1)
        assertTrue("Uncompressed size should be > 0", props.uncompressedSize > 0L)
        assertEquals(sampleArjFile.length(), props.compressedSize)
    }

    @Test
    fun refusals_adhereToTruthfulness() {
        try {
            extendedArchiveEngine.createArj(listOf(File("x.txt")), File("out.arj"))
            fail("createArj must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("ARJ") == true)
        }

        try {
            extendedArchiveEngine.createAr(listOf(File("x.txt")), File("out.ar"))
            fail("createAr must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("AR") == true)
        }

        try {
            extendedArchiveEngine.rejectCab()
            fail("rejectCab must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("CAB") == true)
        }

        try {
            extendedArchiveEngine.rejectLzh()
            fail("rejectLzh must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("LZH") == true)
        }

        try {
            extendedArchiveEngine.rejectZstd()
            fail("rejectZstd must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("Zstandard") == true)
        }
    }

    @Test
    fun nonExistentFile_handledGracefully() = runTest {
        val missing = File(tempFolder.root, "missing.arj")
        val entries = extendedArchiveEngine.listEntries(missing)
        assertTrue(entries.isEmpty())

        val props = extendedArchiveEngine.getArchiveProperties(missing)
        assertEquals(0, props.totalFiles)
    }
}
