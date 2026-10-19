package com.Hrshd1eux.filemanager.core.operations

import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.model.OperationProgress
import com.Hrshd1eux.filemanager.core.operations.model.OperationResult
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FileOperationEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var sourceDir: File
    private lateinit var destDir: File
    private lateinit var engine: FileOperationEngine

    @Before
    fun setUp() {
        sourceDir = tempFolder.newFolder("source")
        destDir = tempFolder.newFolder("dest")
        engine = FileOperationEngine(ioDispatcher = testDispatcher)
    }

    @Test
    fun copy_singleFile_copiesContentAndReportsProgress() = runTest(testDispatcher) {
        val srcFile = File(sourceDir, "sample.txt").apply { writeText("ARCA Streaming Engine Test Content") }
        val srcNode = srcFile.toNode()
        val destNode = destDir.toNode()

        var progressUpdates = 0
        var lastProgress: OperationProgress? = null

        val result = engine.copy(
            sources = listOf(srcNode),
            destinationDir = destNode,
            conflictResolution = ConflictResolution.RENAME_AUTO,
            onProgress = { progress ->
                progressUpdates++
                lastProgress = progress
            }
        )

        assertTrue(result.isSuccess)
        val opResult = result.getOrThrow()
        assertTrue(opResult is OperationResult.Success)
        assertEquals(1, opResult.filesProcessed)
        assertEquals(srcFile.length(), opResult.bytesProcessed)

        val copiedFile = File(destDir, "sample.txt")
        assertTrue(copiedFile.exists())
        assertEquals("ARCA Streaming Engine Test Content", copiedFile.readText())
        assertTrue(progressUpdates > 0)
        assertEquals(srcFile.length(), lastProgress?.bytesProcessed)
    }

    @Test
    fun copy_recursiveDirectory_copiesAllNestedFiles() = runTest(testDispatcher) {
        val subDir = File(sourceDir, "FolderA").apply { mkdir() }
        val innerSubDir = File(subDir, "InnerFolder").apply { mkdir() }
        File(subDir, "f1.txt").writeText("File 1")
        File(innerSubDir, "f2.txt").writeText("File 2")

        val result = engine.copy(
            sources = listOf(subDir.toNode()),
            destinationDir = destDir.toNode()
        )

        assertTrue(result.isSuccess)
        val copiedFolder = File(destDir, "FolderA")
        assertTrue(copiedFolder.exists())
        assertTrue(File(copiedFolder, "f1.txt").exists())
        assertTrue(File(copiedFolder, "InnerFolder/f2.txt").exists())
        assertEquals("File 2", File(copiedFolder, "InnerFolder/f2.txt").readText())
    }

    @Test
    fun copy_withRenameAutoConflict_generatesIndexedName() = runTest(testDispatcher) {
        val srcFile = File(sourceDir, "doc.pdf").apply { writeText("New Content") }
        File(destDir, "doc.pdf").writeText("Original Content")

        val result = engine.copy(
            sources = listOf(srcFile.toNode()),
            destinationDir = destDir.toNode(),
            conflictResolution = ConflictResolution.RENAME_AUTO
        )

        assertTrue(result.isSuccess)
        assertTrue(File(destDir, "doc.pdf").exists())
        assertEquals("Original Content", File(destDir, "doc.pdf").readText())

        val renamedFile = File(destDir, "doc (1).pdf")
        assertTrue(renamedFile.exists())
        assertEquals("New Content", renamedFile.readText())
    }

    @Test
    fun copy_withOverwriteConflict_replacesTarget() = runTest(testDispatcher) {
        val srcFile = File(sourceDir, "doc.txt").apply { writeText("Overwritten Content") }
        File(destDir, "doc.txt").writeText("Old Content")

        val result = engine.copy(
            sources = listOf(srcFile.toNode()),
            destinationDir = destDir.toNode(),
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(result.isSuccess)
        val target = File(destDir, "doc.txt")
        assertTrue(target.exists())
        assertEquals("Overwritten Content", target.readText())
    }

    @Test
    fun copy_withSkipConflict_leavesOriginalUntouched() = runTest(testDispatcher) {
        val srcFile = File(sourceDir, "skip.txt").apply { writeText("Should not copy") }
        File(destDir, "skip.txt").writeText("Original")

        val result = engine.copy(
            sources = listOf(srcFile.toNode()),
            destinationDir = destDir.toNode(),
            conflictResolution = ConflictResolution.SKIP
        )

        assertTrue(result.isSuccess)
        assertEquals("Original", File(destDir, "skip.txt").readText())
    }

    @Test
    fun move_movesFileAndRemovesSource() = runTest(testDispatcher) {
        val srcFile = File(sourceDir, "to_move.txt").apply { writeText("Moving") }

        val result = engine.move(
            sources = listOf(srcFile.toNode()),
            destinationDir = destDir.toNode()
        )

        assertTrue(result.isSuccess)
        assertFalse(srcFile.exists())
        assertTrue(File(destDir, "to_move.txt").exists())
        assertEquals("Moving", File(destDir, "to_move.txt").readText())
    }

    @Test
    fun delete_recursivelyDeletesFilesAndDirectories() = runTest(testDispatcher) {
        val toDeleteDir = File(sourceDir, "DeleteMe").apply { mkdir() }
        File(toDeleteDir, "child.txt").writeText("Bye")

        val result = engine.delete(targets = listOf(toDeleteDir.toNode()))

        assertTrue(result.isSuccess)
        assertFalse(toDeleteDir.exists())
    }

    @Test
    fun rename_renamesFileSuccessfully() = runTest(testDispatcher) {
        val original = File(sourceDir, "old_name.txt").apply { writeText("Rename test") }

        val result = engine.rename(target = original.toNode(), newName = "new_name.txt")

        assertTrue(result.isSuccess)
        assertFalse(original.exists())
        val renamed = File(sourceDir, "new_name.txt")
        assertTrue(renamed.exists())
        assertEquals("Rename test", renamed.readText())
    }

    @Test
    fun rename_failsOnInvalidNames() = runTest(testDispatcher) {
        val file = File(sourceDir, "valid.txt").apply { createNewFile() }

        val result1 = engine.rename(target = file.toNode(), newName = "invalid/name.txt")
        assertTrue(result1.isFailure)

        val result2 = engine.rename(target = file.toNode(), newName = "   ")
        assertTrue(result2.isFailure)
    }

    @Test
    fun createDirectory_and_createFile_succeeds() = runTest(testDispatcher) {
        val parentNode = destDir.toNode()

        val dirResult = engine.createDirectory(parentDir = parentNode, dirName = "NewFolder")
        assertTrue(dirResult.isSuccess)
        assertTrue(File(destDir, "NewFolder").isDirectory)

        val fileResult = engine.createFile(parentDir = parentNode, fileName = "created.txt")
        assertTrue(fileResult.isSuccess)
        assertTrue(File(destDir, "created.txt").isFile)
    }

    @Test
    fun copy_failureOrCancellation_cleansUpPartialDestinationFile() = runTest(testDispatcher) {
        val srcFile = File(sourceDir, "large.bin").apply {
            writeBytes(ByteArray(128 * 1024) { 0x42 })
        }
        val srcNode = srcFile.toNode()
        val destNode = destDir.toNode()

        var interrupted = false
        val result = engine.copy(
            sources = listOf(srcNode),
            destinationDir = destNode,
            onProgress = {
                if (!interrupted) {
                    interrupted = true
                    throw IllegalStateException("Simulated disk write failure or interruption")
                }
            }
        )

        assertTrue(result.isFailure)
        val targetFile = File(destDir, "large.bin")
        assertFalse("Partial destination file should be cleaned up", targetFile.exists())
    }

    private fun File.toNode(): StorageNode {
        return StorageNode(
            id = absolutePath,
            name = name,
            path = absolutePath,
            size = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            isDirectory = isDirectory
        )
    }
}
