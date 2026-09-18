package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.IsoEngine
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

class IsoEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var isoEngine: IsoEngine
    private lateinit var sampleIsoFile: File

    @Before
    fun setUp() {
        isoEngine = IsoEngine(Dispatchers.Unconfined)
        sampleIsoFile = File(tempFolder.root, "sample.iso")

        // Load test.iso from classpath or filesystem fallbacks
        val stream = javaClass.classLoader?.getResourceAsStream("test.iso")
        if (stream != null) {
            stream.use { input ->
                sampleIsoFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            val candidatePaths = listOf(
                File("src/test/resources/test.iso"),
                File("app/src/test/resources/test.iso"),
                File("../app/src/test/resources/test.iso")
            )
            val source = candidatePaths.firstOrNull { it.exists() }
                ?: error("test.iso fixture could not be located")
            source.copyTo(sampleIsoFile, overwrite = true)
        }
    }

    @Test
    fun listEntries_validIso_returnsCorrectEntries() = runTest {
        val entries = isoEngine.listEntries(sampleIsoFile)
        assertFalse("Entries should not be empty", entries.isEmpty())

        val files = entries.filter { !it.isDirectory }
        assertTrue("Should contain at least one file entry", files.isNotEmpty())

        for (entry in entries) {
            assertTrue("Path should not be empty", entry.path.isNotEmpty())
            assertTrue("Name should not be empty", entry.name.isNotEmpty())
            assertTrue("Size must be non-negative", entry.uncompressedSize >= 0L)
        }
    }

    @Test
    fun extractAll_validIso_extractsSuccessfully() = runTest {
        val destDir = tempFolder.newFolder("ExtractedIsoAll")
        val result = isoEngine.extractAll(
            isoFile = sampleIsoFile,
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(result.isSuccess)
        val extractedCount = result.getOrNull()
        assertNotNull(extractedCount)
        assertTrue("Extracted count must be > 0", extractedCount!! > 0)

        // Verify that extracted directory contains files
        val extractedFiles = destDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("Extracted files on disk should be > 0", extractedFiles.isNotEmpty())
    }

    @Test
    fun extractEntries_validIso_extractsSelectedOnly() = runTest {
        val allEntries = isoEngine.listEntries(sampleIsoFile)
        val firstFile = allEntries.firstOrNull { !it.isDirectory }
        assertNotNull("ISO should have at least one file entry", firstFile)

        val destDir = tempFolder.newFolder("ExtractedIsoSelected")
        val result = isoEngine.extractEntries(
            isoFile = sampleIsoFile,
            entryPaths = setOf(firstFile!!.path),
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(result.isSuccess)
        val extractedCount = result.getOrNull()
        assertNotNull(extractedCount)
        assertEquals(1, extractedCount)

        val expectedTarget = File(destDir, firstFile.name)
        assertTrue("Selected file must exist on disk at ${expectedTarget.path}", expectedTarget.exists())
    }

    @Test
    fun testArchive_validIso_returnsPassedReport() = runTest {
        val result = isoEngine.testArchive(sampleIsoFile)
        assertTrue(result.isSuccess)

        val report = result.getOrNull()
        assertNotNull(report)
        assertTrue("Archive integrity should pass", report!!.isPassed)
        assertTrue("Corrupted entries list should be empty", report.corruptedEntries.isEmpty())
        assertTrue("Verified entries count should be > 0", report.verifiedEntries > 0)
    }

    @Test
    fun getArchiveProperties_validIso_computesAccurateMetrics() = runTest {
        val props = isoEngine.getArchiveProperties(sampleIsoFile)
        assertEquals("sample.iso", props.archiveName)
        assertTrue("Should detect at least 1 file", props.totalFiles >= 1)
        assertTrue("Uncompressed size should be > 0", props.uncompressedSize > 0L)
        assertEquals(sampleIsoFile.length(), props.compressedSize)
    }

    @Test
    fun mountIso_throwsUnsupportedOperationException_truthfullyRejectingMounting() {
        try {
            isoEngine.mountIso(sampleIsoFile)
            fail("mountIso must throw UnsupportedOperationException under Android sandbox")
        } catch (e: UnsupportedOperationException) {
            val message = e.message.orEmpty()
            assertTrue("Message must mention mounting", message.contains("mounting", ignoreCase = true))
            assertTrue("Message must mention Android / SELinux / root", message.contains("Android", ignoreCase = true))
        }
    }

    @Test
    fun createIso_throwsUnsupportedOperationException_truthfullyRejectingCreation() {
        try {
            isoEngine.createIso(
                sources = listOf(File("dummy.txt")),
                destinationIso = File("output.iso")
            )
            fail("createIso must throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("creation is not supported", ignoreCase = true) == true)
        }
    }

    @Test
    fun listEntries_nonExistentFile_returnsEmpty() = runTest {
        val missing = File(tempFolder.root, "missing.iso")
        val entries = isoEngine.listEntries(missing)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun getArchiveProperties_nonExistentFile_returnsEmptyProps() = runTest {
        val missing = File(tempFolder.root, "missing.iso")
        val props = isoEngine.getArchiveProperties(missing)
        assertEquals(0, props.totalFiles)
        assertEquals(0, props.totalFolders)
        assertEquals(0L, props.uncompressedSize)
    }
}
