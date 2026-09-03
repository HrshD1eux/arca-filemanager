package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SevenZEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sevenZEngine: SevenZEngine

    @Before
    fun setUp() {
        sevenZEngine = SevenZEngine(Dispatchers.Unconfined)
    }

    @Test
    fun createAndList7z_createsValidArchiveAndListsEntries() = runTest {
        val f1 = tempFolder.newFile("file1.txt").apply { writeText("Content for file 1") }
        val f2 = tempFolder.newFile("file2.txt").apply { writeText("Content for file 2 with more bytes") }
        val archive7z = File(tempFolder.root, "test.7z")

        val result = sevenZEngine.create7z(
            sources = listOf(f1, f2),
            destination7z = archive7z,
            method = SevenZMethod.LZMA2
        )

        assertTrue(result.isSuccess)
        assertTrue(archive7z.exists())
        assertTrue(archive7z.length() > 0)

        val entries = sevenZEngine.listEntries(archive7z)
        assertEquals(2, entries.size)
        val names = entries.map { it.name }.toSet()
        assertTrue(names.contains("file1.txt"))
        assertTrue(names.contains("file2.txt"))
    }

    @Test
    fun extractAll_extractsAllFilesToDestination() = runTest {
        val f1 = tempFolder.newFile("doc.txt").apply { writeText("Document Body") }
        val f2 = tempFolder.newFile("notes.txt").apply { writeText("Personal Notes") }
        val archive7z = File(tempFolder.root, "bundle.7z")

        sevenZEngine.create7z(listOf(f1, f2), archive7z)

        val extractDest = tempFolder.newFolder("Extracted")
        val extractResult = sevenZEngine.extractAll(
            sevenZFile = archive7z,
            destinationDir = extractDest,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(2, extractResult.getOrNull())

        val extractedDoc = File(extractDest, "doc.txt")
        val extractedNotes = File(extractDest, "notes.txt")

        assertTrue(extractedDoc.exists())
        assertTrue(extractedNotes.exists())
        assertEquals("Document Body", extractedDoc.readText())
        assertEquals("Personal Notes", extractedNotes.readText())
    }

    @Test
    fun extractEntries_extractsOnlySelectedFiles() = runTest {
        val f1 = tempFolder.newFile("keep.txt").apply { writeText("Keep Me") }
        val f2 = tempFolder.newFile("ignore.txt").apply { writeText("Ignore Me") }
        val archive7z = File(tempFolder.root, "selective.7z")

        sevenZEngine.create7z(listOf(f1, f2), archive7z)

        val extractDest = tempFolder.newFolder("SelectiveExtract")
        val extractResult = sevenZEngine.extractEntries(
            sevenZFile = archive7z,
            entryPaths = setOf("keep.txt"),
            destinationDir = extractDest
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())

        assertTrue(File(extractDest, "keep.txt").exists())
        assertFalse(File(extractDest, "ignore.txt").exists())
        assertEquals("Keep Me", File(extractDest, "keep.txt").readText())
    }

    @Test
    fun testArchive_verifiesIntegrityOfValidArchive() = runTest {
        val f1 = tempFolder.newFile("data.bin").apply { writeText("binary test payload") }
        val archive7z = File(tempFolder.root, "integrity.7z")

        sevenZEngine.create7z(listOf(f1), archive7z)

        val reportResult = sevenZEngine.testArchive(archive7z)
        assertTrue(reportResult.isSuccess)

        val report = reportResult.getOrNull()
        org.junit.Assert.assertNotNull(report)
        assertTrue(report!!.isPassed)
        assertEquals(1, report.totalEntries)
        assertEquals(1, report.verifiedEntries)
        assertTrue(report.corruptedEntries.isEmpty())
    }

    @Test
    fun getArchiveProperties_returnsAccurateCountsAndSizes() = runTest {
        val f1 = tempFolder.newFile("prop1.txt").apply { writeText("12345") }
        val f2 = tempFolder.newFile("prop2.txt").apply { writeText("1234567890") }
        val archive7z = File(tempFolder.root, "props.7z")

        sevenZEngine.create7z(listOf(f1, f2), archive7z)

        val props = sevenZEngine.getArchiveProperties(archive7z)
        assertEquals("props.7z", props.archiveName)
        assertEquals(2, props.totalFiles)
        assertEquals(15L, props.uncompressedSize)
        assertTrue(props.compressedSize > 0)
    }
}
