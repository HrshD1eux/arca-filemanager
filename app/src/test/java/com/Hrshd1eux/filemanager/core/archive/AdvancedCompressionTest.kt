package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionLevelPreset
import com.Hrshd1eux.filemanager.core.archive.model.CompressionMethodOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionProfile
import com.Hrshd1eux.filemanager.core.archive.model.DictionarySizePreset
import com.Hrshd1eux.filemanager.core.archive.model.SplitSizePreset
import com.Hrshd1eux.filemanager.core.archive.model.ThreadCountPreset
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AdvancedCompressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sevenZEngine: SevenZEngine

    @Before
    fun setUp() {
        sevenZEngine = SevenZEngine(Dispatchers.Unconfined)
    }

    @Test
    fun compressionProfile_presetsAndRAMEstimates_areAccurate() {
        val dict64 = DictionarySizePreset.fromSizeMb(64)
        assertEquals(DictionarySizePreset.MB_64, dict64)
        assertEquals("64 MB", dict64.displayName)
        assertEquals(660, dict64.memoryEstimateMb)

        val dict128 = DictionarySizePreset.fromSizeMb(128)
        assertEquals(DictionarySizePreset.MB_128, dict128)
        assertEquals(1300, dict128.memoryEstimateMb)

        val threads4 = ThreadCountPreset.fromCount(4)
        assertEquals(ThreadCountPreset.THREADS_4, threads4)
        assertEquals("4", threads4.displayName)

        val default7z = CompressionProfile.defaultFor(ArchiveFormatOption.SEVEN_Z)
        assertEquals(ArchiveFormatOption.SEVEN_Z, default7z.format)
        assertEquals(CompressionMethodOption.LZMA2, default7z.method)
        assertEquals(32, default7z.dictionarySizeMb)
        assertTrue(default7z.isSolid)
    }

    @Test
    fun create7z_ultraLevelAnd64MbDictionary_compressesAndExtractsAccurately() = runTest {
        val payload1 = "Advanced Compression payload with repeated text pattern ".repeat(300)
        val payload2 = ByteArray(50 * 1024) { (it % 127).toByte() }

        val f1 = tempFolder.newFile("doc_ultra.txt").apply { writeText(payload1) }
        val f2 = tempFolder.newFile("data_ultra.bin").apply { writeBytes(payload2) }

        val archiveFile = File(tempFolder.root, "power_ultra.7z")

        // Ultra level = 9, Dictionary = 64 MB, LZMA2
        val result = sevenZEngine.create7z(
            sources = listOf(f1, f2),
            destination7z = archiveFile,
            method = SevenZMethod.LZMA2,
            compressionLevel = 9,
            dictionarySizeMb = 64,
            isSolid = true,
            threads = 4
        )

        assertTrue("Archive creation should succeed, error: ${result.exceptionOrNull()?.stackTraceToString()}", result.isSuccess)
        assertTrue(archiveFile.exists())
        assertTrue(archiveFile.length() > 0)

        // Verify entries
        val entries = sevenZEngine.listEntries(archiveFile)
        assertEquals(2, entries.size)
        val entryNames = entries.map { it.name }.toSet()
        assertTrue(entryNames.contains("doc_ultra.txt"))
        assertTrue(entryNames.contains("data_ultra.bin"))

        // Extract and verify integrity
        val extractDir = tempFolder.newFolder("UltraExtracted")
        val extractResult = sevenZEngine.extractAll(
            sevenZFile = archiveFile,
            destinationDir = extractDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(2, extractResult.getOrNull())

        val extracted1 = File(extractDir, "doc_ultra.txt")
        val extracted2 = File(extractDir, "data_ultra.bin")

        assertTrue(extracted1.exists())
        assertTrue(extracted2.exists())
        assertEquals(payload1, extracted1.readText())
        assertArrayEquals(payload2, extracted2.readBytes())
    }

    @Test
    fun create7z_deflateAndCopyMethods_succeeds() = runTest {
        val f1 = tempFolder.newFile("deflate_file.txt").apply { writeText("Deflate method content payload") }
        val deflateArchive = File(tempFolder.root, "deflate_test.7z")

        // DEFLATE method
        val deflateResult = sevenZEngine.create7z(
            sources = listOf(f1),
            destination7z = deflateArchive,
            method = SevenZMethod.DEFLATE,
            compressionLevel = 6
        )
        assertTrue(deflateResult.isSuccess)
        val deflateEntries = sevenZEngine.listEntries(deflateArchive)
        assertEquals(1, deflateEntries.size)
        assertEquals("deflate_file.txt", deflateEntries.first().name)

        // COPY (Store) method
        val copyArchive = File(tempFolder.root, "copy_test.7z")
        val copyResult = sevenZEngine.create7z(
            sources = listOf(f1),
            destination7z = copyArchive,
            method = SevenZMethod.COPY,
            compressionLevel = 0
        )
        assertTrue(copyResult.isSuccess)
        val copyEntries = sevenZEngine.listEntries(copyArchive)
        assertEquals(1, copyEntries.size)
        assertEquals("deflate_file.txt", copyEntries.first().name)
    }

    @Test
    fun createSplit7z_withAdvancedUltraAndDictionary_createsValidVolumes() = runTest {
        val randomData = ByteArray(64 * 1024).also { java.util.Random(42).nextBytes(it) }
        val f1 = tempFolder.newFile("split_power.dat").apply {
            writeBytes(randomData)
        }
        val baseDest = File(tempFolder.root, "split_advanced.7z")
        val splitSize = 16 * 1024L

        val result = sevenZEngine.createSplit7z(
            sources = listOf(f1),
            destinationBase7z = baseDest,
            splitSizeBytes = splitSize,
            method = SevenZMethod.LZMA2,
            compressionLevel = 9,
            dictionarySizeMb = 16,
            isSolid = true,
            threads = 2
        )

        assertTrue(result.isSuccess)
        val parts = result.getOrNull()!!
        assertTrue("Should produce multiple split volumes, but got ${parts.size}", parts.size >= 2)

        val part1 = File(tempFolder.root, "split_advanced.7z.001")
        assertTrue(part1.exists())
        assertEquals(splitSize, part1.length())

        val entries = sevenZEngine.listEntries(part1)
        assertEquals(1, entries.size)
        assertEquals("split_power.dat", entries.first().name)

        val destDir = tempFolder.newFolder("SplitExtract")
        val extractRes = sevenZEngine.extractAll(
            sevenZFile = part1,
            destinationDir = destDir,
            conflictResolution = ConflictResolution.OVERWRITE
        )
        assertTrue(extractRes.isSuccess)
        val extractedFile = File(destDir, "split_power.dat")
        assertTrue(extractedFile.exists())
        assertEquals(64 * 1024L, extractedFile.length())
        assertArrayEquals(randomData, extractedFile.readBytes())
    }
}
