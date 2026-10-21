package com.Hrshd1eux.filemanager.core.trash

import com.Hrshd1eux.filemanager.core.trash.shredder.FileShredder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileShredderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun shredFile_completelyRemovesFileFromFilesystem() = runBlocking {
        val testFile = tempFolder.newFile("confidential_document.pdf")
        testFile.writeText("SECRET_DATA_DO_NOT_RECOVER".repeat(500))

        assertTrue(testFile.exists())
        assertEquals(13000L, testFile.length())

        val result = FileShredder.shred(testFile)
        assertTrue(result.isSuccess)
        assertFalse("File must be deleted after shredding", testFile.exists())
    }

    @Test
    fun shredDirectory_recursivelyShredsChildFilesAndDeletesDirectory() = runBlocking {
        val testDir = tempFolder.newFolder("secret_folder")
        val file1 = File(testDir, "file1.txt").apply { writeText("Confidential 1") }
        val file2 = File(testDir, "file2.jpg").apply { writeBytes(ByteArray(1024) { 0x42 }) }
        val subDir = File(testDir, "sub").apply { mkdir() }
        val file3 = File(subDir, "file3.png").apply { writeBytes(ByteArray(2048) { 0x55 }) }

        assertTrue(file1.exists())
        assertTrue(file2.exists())
        assertTrue(file3.exists())

        val result = FileShredder.shred(testDir)
        assertTrue(result.isSuccess)

        assertFalse(testDir.exists())
        assertFalse(file1.exists())
        assertFalse(file2.exists())
        assertFalse(file3.exists())
    }

    @Test
    fun shredZeroLengthFile_handledGracefullyWithoutException() = runBlocking {
        val emptyFile = tempFolder.newFile("empty.txt")
        assertEquals(0L, emptyFile.length())

        val result = FileShredder.shred(emptyFile)
        assertTrue(result.isSuccess)
        assertFalse(emptyFile.exists())
    }
}
