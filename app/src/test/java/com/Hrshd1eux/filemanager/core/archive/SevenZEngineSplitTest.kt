package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.archive.split.MissingSplitVolumeException
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

class SevenZEngineSplitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sevenZEngine: SevenZEngine

    @Before
    fun setUp() {
        sevenZEngine = SevenZEngine(Dispatchers.Unconfined)
    }

    @Test
    fun createSplit7z_createsConsecutiveVolumeParts() = runTest {
        // Create 2 test files with non-trivial text
        val f1 = tempFolder.newFile("large1.txt").apply {
            writeBytes(ByteArray(20 * 1024) { (it % 256).toByte() })
        }
        val f2 = tempFolder.newFile("large2.txt").apply {
            writeBytes(ByteArray(20 * 1024) { ((it + 7) % 256).toByte() })
        }

        val baseDestination = File(tempFolder.root, "my_backup.7z")
        // Split size = 16 KB (16384 bytes)
        val splitSize = 16 * 1024L

        val createResult = sevenZEngine.createSplit7z(
            sources = listOf(f1, f2),
            destinationBase7z = baseDestination,
            splitSizeBytes = splitSize,
            method = SevenZMethod.COPY
        )

        assertTrue(createResult.isSuccess)
        val createdVolumes = createResult.getOrNull()!!
        assertTrue("Should create at least 2 split volumes", createdVolumes.size >= 2)

        val vol1 = File(tempFolder.root, "my_backup.7z.001")
        val vol2 = File(tempFolder.root, "my_backup.7z.002")

        assertTrue(vol1.exists())
        assertTrue(vol2.exists())
        assertEquals(splitSize, vol1.length())
        assertEquals(vol1, createdVolumes[0])
        assertEquals(vol2, createdVolumes[1])
    }

    @Test
    fun split7z_listAndTestArchive_worksSeamlessly() = runTest {
        val f1 = tempFolder.newFile("doc_split.txt").apply {
            writeText("A".repeat(15000))
        }
        val f2 = tempFolder.newFile("data_split.bin").apply {
            writeBytes(ByteArray(25000) { (it % 128).toByte() })
        }

        val baseDestination = File(tempFolder.root, "archive_test.7z")
        sevenZEngine.createSplit7z(
            sources = listOf(f1, f2),
            destinationBase7z = baseDestination,
            splitSizeBytes = 10 * 1024L, // 10 KB volumes
            method = SevenZMethod.COPY
        )

        val part1 = File(tempFolder.root, "archive_test.7z.001")
        val part2 = File(tempFolder.root, "archive_test.7z.002")
        assertTrue(part1.exists())
        assertTrue(part2.exists())

        // Listing entries from part 1
        val entriesFromPart1 = sevenZEngine.listEntries(part1)
        assertEquals(2, entriesFromPart1.size)
        val names = entriesFromPart1.map { it.name }.toSet()
        assertTrue(names.contains("doc_split.txt"))
        assertTrue(names.contains("data_split.bin"))

        // Listing entries by clicking part 2 directly (should automatically resolve full archive)
        val entriesFromPart2 = sevenZEngine.listEntries(part2)
        assertEquals(2, entriesFromPart2.size)

        // Test archive integrity
        val testResult = sevenZEngine.testArchive(part1)
        assertTrue(testResult.isSuccess)
        val report = testResult.getOrNull()
        org.junit.Assert.assertNotNull(report)
        assertTrue(report!!.isPassed)
        assertEquals(2, report.totalEntries)

        // Archive properties
        val props = sevenZEngine.getArchiveProperties(part1)
        assertEquals(2, props.totalFiles)
        assertTrue(props.uncompressedSize > 0)
        assertTrue(props.compressedSize > 0)
    }

    @Test
    fun split7z_extractAll_reconstitutesOriginalContentAccurately() = runTest {
        val content1 = "First file data string content with multiple characters 1234567890".repeat(500)
        val content2 = ByteArray(30000) { (it % 255).toByte() }

        val f1 = tempFolder.newFile("extract1.txt").apply { writeText(content1) }
        val f2 = tempFolder.newFile("extract2.dat").apply { writeBytes(content2) }

        val baseDest = File(tempFolder.root, "roundtrip.7z")
        sevenZEngine.createSplit7z(
            sources = listOf(f1, f2),
            destinationBase7z = baseDest,
            splitSizeBytes = 12 * 1024L,
            method = SevenZMethod.LZMA2
        )

        val part1 = File(tempFolder.root, "roundtrip.7z.001")
        val extractDest = tempFolder.newFolder("ExtractedSplit")

        val extractResult = sevenZEngine.extractAll(
            sevenZFile = part1,
            destinationDir = extractDest,
            conflictResolution = ConflictResolution.OVERWRITE
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(2, extractResult.getOrNull())

        val extracted1 = File(extractDest, "extract1.txt")
        val extracted2 = File(extractDest, "extract2.dat")

        assertTrue(extracted1.exists())
        assertTrue(extracted2.exists())
        assertEquals(content1, extracted1.readText())
        assertTrue(content2.contentEquals(extracted2.readBytes()))
    }

    @Test
    fun split7z_extractEntries_selectiveExtractionWorks() = runTest {
        val f1 = tempFolder.newFile("target.txt").apply { writeText("Target file content") }
        val f2 = tempFolder.newFile("skip.txt").apply { writeText("Skip file content") }

        val baseDest = File(tempFolder.root, "selective_split.7z")
        sevenZEngine.createSplit7z(
            sources = listOf(f1, f2),
            destinationBase7z = baseDest,
            splitSizeBytes = 512L,
            method = SevenZMethod.COPY
        )

        val part1 = File(tempFolder.root, "selective_split.7z.001")
        val extractDest = tempFolder.newFolder("ExtractedSelective")

        val extractResult = sevenZEngine.extractEntries(
            sevenZFile = part1,
            entryPaths = setOf("target.txt"),
            destinationDir = extractDest
        )

        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())

        assertTrue(File(extractDest, "target.txt").exists())
        assertFalse(File(extractDest, "skip.txt").exists())
        assertEquals("Target file content", File(extractDest, "target.txt").readText())
    }

    @Test
    fun split7z_missingVolume_throwsMissingSplitVolumeException() = runTest {
        val f1 = tempFolder.newFile("sample.txt").apply { writeBytes(ByteArray(25000)) }
        val baseDest = File(tempFolder.root, "broken_split.7z")
        sevenZEngine.createSplit7z(
            sources = listOf(f1),
            destinationBase7z = baseDest,
            splitSizeBytes = 8 * 1024L,
            method = SevenZMethod.COPY
        )

        val part1 = File(tempFolder.root, "broken_split.7z.001")
        val part2 = File(tempFolder.root, "broken_split.7z.002")
        assertTrue(part1.exists())
        assertTrue(part2.exists())

        // Delete intermediate volume 2
        part2.delete()
        assertFalse(part2.exists())

        // Listing entries must fail with MissingSplitVolumeException
        val result = runCatching { sevenZEngine.listEntries(part1) }
        assertTrue(result.isFailure)
        assertTrue(
            "Expected MissingSplitVolumeException, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is MissingSplitVolumeException
        )
    }
}
