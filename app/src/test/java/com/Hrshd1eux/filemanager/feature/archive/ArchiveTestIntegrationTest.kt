package com.Hrshd1eux.filemanager.feature.archive

import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.archive.engine.TarCompressionType
import com.Hrshd1eux.filemanager.core.archive.engine.TarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class ArchiveTestIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var zipEngine: ZipEngine
    private lateinit var sevenZEngine: SevenZEngine
    private lateinit var tarEngine: TarEngine

    @Before
    fun setUp() {
        zipEngine = ZipEngine(Dispatchers.Unconfined)
        sevenZEngine = SevenZEngine(Dispatchers.Unconfined)
        tarEngine = TarEngine(Dispatchers.Unconfined)
    }

    @Test
    fun testZipArchive_validZip_passesIntegrityCheck() = runTest {
        val file1 = tempFolder.newFile("test1.txt").apply { writeText("Hello ZIP verification!") }
        val file2 = tempFolder.newFile("test2.txt").apply { writeText("Second payload in ZIP") }
        val zipFile = File(tempFolder.root, "valid.zip")

        zipEngine.createZip(listOf(file1, file2), zipFile)
        assertTrue(zipFile.exists())

        val reportResult = zipEngine.testArchive(zipFile)
        assertTrue(reportResult.isSuccess)

        val report = reportResult.getOrNull()
        assertNotNull(report)
        assertTrue(report!!.isPassed)
        assertEquals(2, report.totalEntries)
        assertEquals(2, report.verifiedEntries)
        assertTrue(report.corruptedEntries.isEmpty())
    }

    @Test
    fun testSevenZArchive_valid7z_passesIntegrityCheck() = runTest {
        val file1 = tempFolder.newFile("seven1.txt").apply { writeText("Hello 7z verification!") }
        val sevenZFile = File(tempFolder.root, "valid.7z")

        sevenZEngine.create7z(listOf(file1), sevenZFile)
        assertTrue(sevenZFile.exists())

        val reportResult = sevenZEngine.testArchive(sevenZFile)
        assertTrue(reportResult.isSuccess)

        val report = reportResult.getOrNull()
        assertNotNull(report)
        assertTrue(report!!.isPassed)
        assertEquals(1, report.totalEntries)
        assertEquals(1, report.verifiedEntries)
        assertTrue(report.corruptedEntries.isEmpty())
    }

    @Test
    fun testTarGzArchive_validTarGz_passesIntegrityCheck() = runTest {
        val file1 = tempFolder.newFile("tar1.txt").apply { writeText("Hello TAR GZ verification!") }
        val tarGzFile = File(tempFolder.root, "valid.tar.gz")

        tarEngine.createTar(listOf(file1), tarGzFile, TarCompressionType.GZIP)
        assertTrue(tarGzFile.exists())

        val reportResult = tarEngine.testArchive(tarGzFile)
        assertTrue(reportResult.isSuccess)

        val report = reportResult.getOrNull()
        assertNotNull(report)
        assertTrue(report!!.isPassed)
        assertEquals(1, report.totalEntries)
        assertEquals(1, report.verifiedEntries)
        assertTrue(report.corruptedEntries.isEmpty())
    }

    @Test
    fun testCorruptedZip_detectsCorruptionOrFailure() = runTest {
        val file1 = tempFolder.newFile("data.bin").apply { writeBytes(ByteArray(1024) { 0x55 }) }
        val zipFile = File(tempFolder.root, "damaged.zip")

        zipEngine.createZip(listOf(file1), zipFile)

        // Corrupt archive body
        val raf = RandomAccessFile(zipFile, "rw")
        raf.seek(30)
        raf.write(ByteArray(50) { 0x00 })
        raf.close()

        val reportResult = zipEngine.testArchive(zipFile)
        // Should either fail with error or report corrupted entries
        if (reportResult.isSuccess) {
            val report = reportResult.getOrNull()
            assertNotNull(report)
            assertFalse(report!!.isPassed)
        } else {
            assertTrue(reportResult.isFailure)
        }
    }
}
