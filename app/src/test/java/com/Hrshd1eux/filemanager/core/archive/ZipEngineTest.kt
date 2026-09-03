package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
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
        // Manually build a malicious zip entry that attempts directory traversal
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
}
