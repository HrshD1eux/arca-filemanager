package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.TarCompressionType
import com.Hrshd1eux.filemanager.core.archive.engine.TarEngine
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

class TarEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tarEngine: TarEngine

    @Before
    fun setUp() {
        tarEngine = TarEngine(Dispatchers.Unconfined)
    }

    @Test
    fun createAndListTar_createsValidUncompressedTar() = runTest {
        val f1 = tempFolder.newFile("file1.txt").apply { writeText("File 1 contents") }
        val f2 = tempFolder.newFile("file2.txt").apply { writeText("File 2 contents") }
        val tarFile = File(tempFolder.root, "bundle.tar")

        val createResult = tarEngine.createTar(listOf(f1, f2), tarFile, TarCompressionType.NONE)
        assertTrue(createResult.isSuccess)
        assertTrue(tarFile.exists())

        val entries = tarEngine.listEntries(tarFile)
        assertEquals(2, entries.size)
        val names = entries.map { it.name }.toSet()
        assertTrue(names.contains("file1.txt"))
        assertTrue(names.contains("file2.txt"))
    }

    @Test
    fun createAndExtractTarGz_compressesAndExtractsCorrectly() = runTest {
        val f1 = tempFolder.newFile("notes.txt").apply { writeText("Gzip compressed text") }
        val tarGzFile = File(tempFolder.root, "archive.tar.gz")

        val createResult = tarEngine.createTar(listOf(f1), tarGzFile, TarCompressionType.GZIP)
        assertTrue(createResult.isSuccess)

        val extractDest = tempFolder.newFolder("GzExtracted")
        val extractResult = tarEngine.extractAll(
            tarFile = tarGzFile,
            destinationDir = extractDest,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())

        val extracted = File(extractDest, "notes.txt")
        assertTrue(extracted.exists())
        assertEquals("Gzip compressed text", extracted.readText())
    }

    @Test
    fun createAndExtractTarXz_compressesAndExtractsCorrectly() = runTest {
        val f1 = tempFolder.newFile("data.json").apply { writeText("{\"key\":\"xz_value\"}") }
        val tarXzFile = File(tempFolder.root, "data.tar.xz")

        val createResult = tarEngine.createTar(listOf(f1), tarXzFile, TarCompressionType.XZ)
        assertTrue(createResult.isSuccess)

        val extractDest = tempFolder.newFolder("XzExtracted")
        val extractResult = tarEngine.extractAll(
            tarFile = tarXzFile,
            destinationDir = extractDest
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())

        val extracted = File(extractDest, "data.json")
        assertTrue(extracted.exists())
        assertEquals("{\"key\":\"xz_value\"}", extracted.readText())
    }

    @Test
    fun createAndExtractTarBz2_compressesAndExtractsCorrectly() = runTest {
        val f1 = tempFolder.newFile("config.cfg").apply { writeText("bzip2=true") }
        val tarBz2File = File(tempFolder.root, "config.tar.bz2")

        val createResult = tarEngine.createTar(listOf(f1), tarBz2File, TarCompressionType.BZIP2)
        assertTrue(createResult.isSuccess)

        val extractDest = tempFolder.newFolder("Bz2Extracted")
        val extractResult = tarEngine.extractAll(
            tarFile = tarBz2File,
            destinationDir = extractDest
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())

        val extracted = File(extractDest, "config.cfg")
        assertTrue(extracted.exists())
        assertEquals("bzip2=true", extracted.readText())
    }

    @Test
    fun extractEntries_extractsOnlySelectedEntry() = runTest {
        val f1 = tempFolder.newFile("first.txt").apply { writeText("First") }
        val f2 = tempFolder.newFile("second.txt").apply { writeText("Second") }
        val tarFile = File(tempFolder.root, "multi.tar")

        tarEngine.createTar(listOf(f1, f2), tarFile)

        val extractDest = tempFolder.newFolder("SelectiveTar")
        val extractResult = tarEngine.extractEntries(
            tarFile = tarFile,
            entryPaths = setOf("second.txt"),
            destinationDir = extractDest
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())

        assertFalse(File(extractDest, "first.txt").exists())
        assertTrue(File(extractDest, "second.txt").exists())
        assertEquals("Second", File(extractDest, "second.txt").readText())
    }

    @Test
    fun testArchive_verifiesIntegrityOfValidTar() = runTest {
        val f1 = tempFolder.newFile("verified.txt").apply { writeText("Payload") }
        val tarFile = File(tempFolder.root, "check.tar.gz")

        tarEngine.createTar(listOf(f1), tarFile, TarCompressionType.GZIP)

        val reportResult = tarEngine.testArchive(tarFile)
        assertTrue(reportResult.isSuccess)

        val report = reportResult.getOrNull()
        org.junit.Assert.assertNotNull(report)
        assertTrue(report!!.isPassed)
        assertEquals(1, report.totalEntries)
    }

    @Test
    fun getArchiveProperties_computesMetricsAccurately() = runTest {
        val f1 = tempFolder.newFile("p1.txt").apply { writeText("12345") }
        val f2 = tempFolder.newFile("p2.txt").apply { writeText("67890") }
        val tarFile = File(tempFolder.root, "metrics.tar.gz")

        tarEngine.createTar(listOf(f1, f2), tarFile, TarCompressionType.GZIP)

        val props = tarEngine.getArchiveProperties(tarFile)
        assertEquals("metrics.tar.gz", props.archiveName)
        assertEquals(2, props.totalFiles)
        assertEquals(10L, props.uncompressedSize)
        assertTrue(props.compressedSize > 0)
    }

    @Test
    fun listAndExtractTarZ_handlesUnixCompressTarSuccessfully() = runTest {
        val sampleTarZ = File(tempFolder.root, "sample.tar.Z")
        val stream = javaClass.classLoader?.getResourceAsStream("bla.tar.Z")
        if (stream != null) {
            stream.use { input ->
                sampleTarZ.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            val candidatePaths = listOf(
                File("src/test/resources/bla.tar.Z"),
                File("app/src/test/resources/bla.tar.Z"),
                File("../app/src/test/resources/bla.tar.Z")
            )
            val source = candidatePaths.firstOrNull { it.exists() }
                ?: error("bla.tar.Z fixture not found")
            source.copyTo(sampleTarZ, overwrite = true)
        }

        val entries = tarEngine.listEntries(sampleTarZ)
        assertFalse("TAR.Z entries should not be empty", entries.isEmpty())

        val destDir = tempFolder.newFolder("ExtractedTarZ")
        val result = tarEngine.extractAll(
            tarFile = sampleTarZ,
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )
        assertTrue(result.isSuccess)
        assertTrue((result.getOrNull() ?: 0) > 0)
    }

    @Test
    fun createTar_Z_returnsFailureWithUnsupportedOperationException() = runTest {
        val dummy = tempFolder.newFile("dummy.txt").apply { writeText("data") }
        val outTarZ = File(tempFolder.root, "out.tar.Z")
        val result = tarEngine.createTar(listOf(dummy), outTarZ, TarCompressionType.Z)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is UnsupportedOperationException)
        assertTrue(exception?.message?.contains("Unix Compress") == true)
    }
}
