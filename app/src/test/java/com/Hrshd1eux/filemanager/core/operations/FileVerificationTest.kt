package com.Hrshd1eux.filemanager.core.operations

import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.operations.engine.FileOperationEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import com.Hrshd1eux.filemanager.core.operations.verification.FileVerifier
import com.Hrshd1eux.filemanager.core.operations.verification.IntegrityVerificationException
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

class FileVerificationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var operationEngine: FileOperationEngine
    private lateinit var zipEngine: ZipEngine

    @Before
    fun setUp() {
        operationEngine = FileOperationEngine(Dispatchers.Unconfined)
        zipEngine = ZipEngine(Dispatchers.Unconfined)
    }

    @Test
    fun verifyFilePair_succeedsOnIdenticalFiles() = runTest {
        val src = tempFolder.newFile("source.bin").apply {
            writeBytes(ByteArray(1024 * 128) { (it % 256).toByte() })
        }
        val dst = tempFolder.newFile("dest.bin").apply {
            writeBytes(ByteArray(1024 * 128) { (it % 256).toByte() })
        }

        val verified = FileVerifier.verifyFilePair(src, dst, Dispatchers.Unconfined)
        assertTrue(verified)
    }

    @Test
    fun verifyFilePair_throwsOnSizeMismatch() = runTest {
        val src = tempFolder.newFile("source_size.bin").apply {
            writeBytes(ByteArray(100) { 1 })
        }
        val dst = tempFolder.newFile("dest_size.bin").apply {
            writeBytes(ByteArray(101) { 1 })
        }

        assertThrows(IntegrityVerificationException::class.java) {
            kotlinx.coroutines.runBlocking {
                FileVerifier.verifyFilePair(src, dst, Dispatchers.Unconfined)
            }
        }
    }

    @Test
    fun verifyFilePair_throwsOnContentMismatch() = runTest {
        val src = tempFolder.newFile("source_diff.bin").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val dst = tempFolder.newFile("dest_diff.bin").apply {
            writeBytes(byteArrayOf(1, 2, 99, 4, 5))
        }

        assertThrows(IntegrityVerificationException::class.java) {
            kotlinx.coroutines.runBlocking {
                FileVerifier.verifyFilePair(src, dst, Dispatchers.Unconfined)
            }
        }
    }

    @Test
    fun verifyCrc_validatesChecksumAccurately() = runTest {
        val testData = "Secure and verified archive payload".toByteArray()
        val crc = CRC32().apply { update(testData) }.value

        val file = tempFolder.newFile("valid.txt").apply {
            writeBytes(testData)
        }

        assertTrue(FileVerifier.verifyCrc(file, crc, Dispatchers.Unconfined))

        // Checksum mismatch
        assertThrows(IntegrityVerificationException::class.java) {
            kotlinx.coroutines.runBlocking {
                FileVerifier.verifyCrc(file, crc + 1, Dispatchers.Unconfined)
            }
        }
    }

    @Test
    fun copyOperation_withVerifyIntegrity_succeeds() = runTest {
        val srcDir = tempFolder.newFolder("src_dir")
        val dstDir = tempFolder.newFolder("dst_dir")

        val file1 = File(srcDir, "data.txt").apply { writeText("Critical financial records") }
        val node1 = StorageNode(id = file1.path, name = file1.name, path = file1.path, isDirectory = false, size = file1.length())
        val destNode = StorageNode(id = dstDir.path, name = dstDir.name, path = dstDir.path, isDirectory = true)

        val result = operationEngine.copy(
            sources = listOf(node1),
            destinationDir = destNode,
            conflictResolution = ConflictResolution.RENAME_AUTO,
            verifyIntegrity = true
        )

        assertTrue(result.isSuccess)
        val copiedFile = File(dstDir, "data.txt")
        assertTrue(copiedFile.exists())
        assertEquals("Critical financial records", copiedFile.readText())
    }

    @Test
    fun zipExtract_withVerifyIntegrity_validatesExtractedFiles() = runTest {
        val workDir = tempFolder.newFolder("zip_work")
        val testFile = File(workDir, "payload.txt").apply { writeText("ARCA Offline Privacy Integrity Payload") }
        val zipFile = File(workDir, "test.zip")
        val extractDir = tempFolder.newFolder("extracted_out")

        val createResult = zipEngine.createZip(
            sources = listOf(testFile),
            destinationZip = zipFile,
            compressionLevel = Deflater.DEFAULT_COMPRESSION
        )
        assertTrue(createResult.isSuccess)

        val extractResult = zipEngine.extractAll(
            zipFile = zipFile,
            destinationDir = extractDir,
            verifyIntegrity = true
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())
        val extractedFile = File(extractDir, "payload.txt")
        assertTrue(extractedFile.exists())
        assertEquals("ARCA Offline Privacy Integrity Payload", extractedFile.readText())
    }
}
