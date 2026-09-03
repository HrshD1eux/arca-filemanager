package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var zipEngine: ZipEngine

    @Before
    fun setUp() {
        zipEngine = ZipEngine(Dispatchers.Unconfined)
    }

    @Test
    fun createZip_singleAndMultipleFiles_createsValidArchive() = runTest {
        val file1 = tempFolder.newFile("file1.txt").apply { writeText("Hello World 1") }
        val file2 = tempFolder.newFile("file2.txt").apply { writeText("Hello World 2") }
        val destZip = File(tempFolder.root, "output.zip")

        val result = zipEngine.createZip(listOf(file1, file2), destZip)
        assertTrue(result.isSuccess)
        assertTrue(destZip.exists())
        assertTrue(destZip.length() > 0)

        val entries = zipEngine.listEntries(destZip)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.name == "file1.txt" && !it.isDirectory })
        assertTrue(entries.any { it.name == "file2.txt" && !it.isDirectory })
    }

    @Test
    fun createZip_nestedDirectoryStructure_preservesTree() = runTest {
        val rootDir = tempFolder.newFolder("project")
        val subDir = File(rootDir, "src").apply { mkdirs() }
        File(rootDir, "README.md").writeText("# Project Readme")
        File(subDir, "Main.kt").writeText("fun main() {}")

        val destZip = File(tempFolder.root, "project.zip")
        val result = zipEngine.createZip(listOf(rootDir), destZip)
        assertTrue(result.isSuccess)

        val entries = zipEngine.listEntries(destZip)
        assertTrue(entries.any { it.name == "README.md" })
        assertTrue(entries.any { it.name == "Main.kt" && it.parentPath == "project/src" || it.parentPath == "src" })
    }

    @Test
    fun extractAll_extractsCompleteArchiveWithHierarchy() = runTest {
        val sourceDir = tempFolder.newFolder("source")
        File(sourceDir, "a.txt").writeText("File A content")
        val nested = File(sourceDir, "nested").apply { mkdirs() }
        File(nested, "b.txt").writeText("File B content")

        val zipFile = File(tempFolder.root, "archive.zip")
        zipEngine.createZip(listOf(sourceDir), zipFile)

        val extractDir = File(tempFolder.root, "extracted")
        val extractResult = zipEngine.extractAll(zipFile, extractDir)
        assertTrue(extractResult.isSuccess)

        val extractedA = extractDir.walkTopDown().firstOrNull { it.name == "a.txt" }
        val extractedB = extractDir.walkTopDown().firstOrNull { it.name == "b.txt" }

        assertTrue(extractedA != null && extractedA.exists())
        assertEquals("File A content", extractedA?.readText())

        assertTrue(extractedB != null && extractedB.exists())
        assertEquals("File B content", extractedB?.readText())
    }

    @Test
    fun extractEntries_extractsOnlySelectedSubset() = runTest {
        val file1 = tempFolder.newFile("one.txt").apply { writeText("Content One") }
        val file2 = tempFolder.newFile("two.txt").apply { writeText("Content Two") }
        val zipFile = File(tempFolder.root, "two_files.zip")
        zipEngine.createZip(listOf(file1, file2), zipFile)

        val extractDir = File(tempFolder.root, "extracted_subset")
        val extractResult = zipEngine.extractEntries(zipFile, setOf("one.txt"), extractDir)
        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())

        assertTrue(File(extractDir, "one.txt").exists())
        assertEquals("Content One", File(extractDir, "one.txt").readText())
        assertFalse(File(extractDir, "two.txt").exists())
    }

    @Test
    fun extractAll_zipSlipVulnerability_strictlyRejected() = runTest {
        val maliciousZip = File(tempFolder.root, "malicious.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../../escaped_file.txt"))
            zos.write("Malicious payload".toByteArray())
            zos.closeEntry()
        }

        val extractDir = File(tempFolder.root, "safe_target")
        val result = zipEngine.extractAll(maliciousZip, extractDir)

        assertTrue("Zip Slip archive extraction must fail", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertFalse(File(tempFolder.root, "escaped_file.txt").exists())
    }

    @Test
    fun extractAll_withConflictResolutionOverwrite_overwritesTarget() = runTest {
        val file = tempFolder.newFile("notes.txt").apply { writeText("New Zip Content") }
        val zipFile = File(tempFolder.root, "overwrite_test.zip")
        zipEngine.createZip(listOf(file), zipFile)

        val destDir = tempFolder.newFolder("dest_overwrite")
        val existing = File(destDir, "notes.txt").apply { writeText("Old Existing Content") }

        val result = zipEngine.extractAll(zipFile, destDir, conflictResolution = ConflictResolution.OVERWRITE)
        assertTrue(result.isSuccess)
        assertEquals("New Zip Content", existing.readText())
    }

    @Test
    fun extractAll_withConflictResolutionSkip_preservesExisting() = runTest {
        val file = tempFolder.newFile("notes.txt").apply { writeText("New Zip Content") }
        val zipFile = File(tempFolder.root, "skip_test.zip")
        zipEngine.createZip(listOf(file), zipFile)

        val destDir = tempFolder.newFolder("dest_skip")
        val existing = File(destDir, "notes.txt").apply { writeText("Original Content To Keep") }

        val result = zipEngine.extractAll(zipFile, destDir, conflictResolution = ConflictResolution.SKIP)
        assertTrue(result.isSuccess)
        assertEquals("Original Content To Keep", existing.readText())
    }

    @Test
    fun extractAll_withConflictResolutionKeepBoth_createsNumberedClone() = runTest {
        val file = tempFolder.newFile("notes.txt").apply { writeText("Zip Content") }
        val zipFile = File(tempFolder.root, "keep_both_test.zip")
        zipEngine.createZip(listOf(file), zipFile)

        val destDir = tempFolder.newFolder("dest_keep_both")
        val existing = File(destDir, "notes.txt").apply { writeText("Existing Original") }

        val result = zipEngine.extractAll(zipFile, destDir, conflictResolution = ConflictResolution.RENAME_AUTO)
        assertTrue(result.isSuccess)

        val renamed = File(destDir, "notes (1).txt")
        assertTrue(existing.exists())
        assertEquals("Existing Original", existing.readText())
        assertTrue(renamed.exists())
        assertEquals("Zip Content", renamed.readText())
    }

    @Test
    fun testArchive_validArchive_passesIntegrityCheck() = runTest {
        val file1 = tempFolder.newFile("notes.txt").apply { writeText("Sample note") }
        val file2 = tempFolder.newFile("doc.pdf").apply { writeText("%PDF-1.4 header") }
        val zipFile = File(tempFolder.root, "backup.zip")
        zipEngine.createZip(listOf(file1, file2), zipFile)

        val testResult = zipEngine.testArchive(zipFile)
        assertTrue(testResult.isSuccess)
        val report = testResult.getOrNull()!!
        assertTrue(report.isPassed)
        assertEquals(2, report.totalEntries)
        assertEquals(2, report.verifiedEntries)
        assertTrue(report.corruptedEntries.isEmpty())
    }

    @Test
    fun addEntries_appendsFilesToExistingArchive() = runTest {
        val file1 = tempFolder.newFile("original.txt").apply { writeText("Original content") }
        val zipFile = File(tempFolder.root, "growing.zip")
        zipEngine.createZip(listOf(file1), zipFile)

        val file2 = tempFolder.newFile("added.txt").apply { writeText("Newly added content") }
        val addResult = zipEngine.addEntries(zipFile, listOf(file2))
        assertTrue(addResult.isSuccess)

        val entries = zipEngine.listEntries(zipFile)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.name == "original.txt" })
        assertTrue(entries.any { it.name == "added.txt" })
    }

    @Test
    fun getArchiveProperties_accuratelyCalculatesMetadata() = runTest {
        val file1 = tempFolder.newFile("p1.txt").apply { writeText("Some text content for compression testing") }
        val zipFile = File(tempFolder.root, "properties.zip")
        zipEngine.createZip(listOf(file1), zipFile)

        val props = zipEngine.getArchiveProperties(zipFile)
        assertEquals("properties.zip", props.archiveName)
        assertEquals(1, props.totalFiles)
        assertEquals(0, props.totalFolders)
        assertTrue(props.uncompressedSize > 0)
        assertTrue(props.compressedSize > 0)
    }
}
