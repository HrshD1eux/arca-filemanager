package com.Hrshd1eux.filemanager.core.storage

import com.Hrshd1eux.filemanager.core.storage.model.StorageStats
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalFileSystemProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var provider: LocalFileSystemProvider

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("arca_test_root")
        provider = LocalFileSystemProvider(rootDirectory = rootDir)
    }

    @Test
    fun getRootNode_returnsAccurateRootStorageNode() {
        val rootNode = provider.getRootNode()
        assertEquals(rootDir.absolutePath, rootNode.id)
        assertEquals(rootDir.name, rootNode.name)
        assertTrue(rootNode.isDirectory)
    }

    @Test
    fun listChildren_returnsAllFilesAndFolders() = runTest {
        // Arrange
        val subDir = File(rootDir, "Documents").apply { mkdir() }
        val testFile = File(rootDir, "test.txt").apply { writeText("Hello ARCA") }
        val hiddenFile = File(rootDir, ".hidden").apply { writeText("Secret") }

        // Act
        val result = provider.listChildren(rootDir.absolutePath)

        // Assert
        assertTrue(result.isSuccess)
        val children = result.getOrThrow()
        assertEquals(3, children.size)

        val docNode = children.find { it.name == "Documents" }
        assertTrue(docNode != null && docNode.isDirectory)

        val txtNode = children.find { it.name == "test.txt" }
        assertTrue(txtNode != null && !txtNode.isDirectory)
        assertEquals("txt", txtNode?.extension)
        assertEquals(10L, txtNode?.size)

        val hiddenNode = children.find { it.name == ".hidden" }
        assertTrue(hiddenNode != null && hiddenNode.isHidden)
    }

    @Test
    fun getNode_returnsAccurateNodeForFile() = runTest {
        val testFile = File(rootDir, "sample.pdf").apply { writeText("PDF Content") }

        val result = provider.getNode(testFile.absolutePath)
        assertTrue(result.isSuccess)

        val node = result.getOrThrow()
        assertEquals("sample.pdf", node.name)
        assertEquals("pdf", node.extension)
        assertFalse(node.isDirectory)
        assertFalse(node.isHidden)
        assertTrue(node.size > 0)
    }

    @Test
    fun listChildren_failsOnNonExistentPath() = runTest {
        val nonExistentPath = File(rootDir, "missing_folder").absolutePath
        val result = provider.listChildren(nonExistentPath)
        assertTrue(result.isFailure)
    }

    @Test
    fun exists_returnsAccurateBoolean() = runTest {
        val existing = File(rootDir, "exists.txt").apply { createNewFile() }
        val missing = File(rootDir, "not_exists.txt")

        assertTrue(provider.exists(existing.absolutePath))
        assertFalse(provider.exists(missing.absolutePath))
    }

    @Test
    fun storageStats_calculatesUsedRatioCorrectly() {
        val stats = StorageStats(totalBytes = 1000L, freeBytes = 400L)
        assertEquals(600L, stats.usedBytes)
        assertEquals(0.6f, stats.usedRatio, 0.001f)
    }
}
