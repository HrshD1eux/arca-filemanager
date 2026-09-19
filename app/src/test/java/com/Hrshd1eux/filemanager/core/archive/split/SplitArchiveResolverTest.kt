package com.Hrshd1eux.filemanager.core.archive.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SplitArchiveResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun isSplitArchiveName_accuratelyDetectsSplitNames() {
        assertTrue(SplitArchiveResolver.isSplitArchiveName("backup.7z.001"))
        assertTrue(SplitArchiveResolver.isSplitArchiveName("BACKUP.7Z.002"))
        assertTrue(SplitArchiveResolver.isSplitArchiveName("archive.001"))
        assertTrue(SplitArchiveResolver.isSplitArchiveName("archive.002"))
        assertTrue(SplitArchiveResolver.isSplitArchiveName("split.z01"))
        assertTrue(SplitArchiveResolver.isSplitArchiveName("SPLIT.Z02"))

        assertFalse(SplitArchiveResolver.isSplitArchiveName("backup.7z"))
        assertFalse(SplitArchiveResolver.isSplitArchiveName("archive.zip"))
        assertFalse(SplitArchiveResolver.isSplitArchiveName("document.pdf"))
        assertFalse(SplitArchiveResolver.isSplitArchiveName("image.01.png"))
    }

    @Test
    fun is7zSplit_accuratelyIdentifies7zSplits() {
        assertTrue(SplitArchiveResolver.is7zSplit("backup.7z.001"))
        assertTrue(SplitArchiveResolver.is7zSplit("archive.7z.099"))
        assertTrue(SplitArchiveResolver.is7zSplit("DATA.7Z.002"))

        assertFalse(SplitArchiveResolver.is7zSplit("archive.001"))
        assertFalse(SplitArchiveResolver.is7zSplit("archive.z01"))
        assertFalse(SplitArchiveResolver.is7zSplit("backup.7z"))
    }

    @Test
    fun resolveSplitParts_completeContiguousParts_resolvesSuccessfully() {
        val folder = tempFolder.newFolder("split_archive")
        val part1 = File(folder, "backup.7z.001").apply { writeBytes(ByteArray(100)) }
        val part2 = File(folder, "backup.7z.002").apply { writeBytes(ByteArray(150)) }
        val part3 = File(folder, "backup.7z.003").apply { writeBytes(ByteArray(200)) }

        // Test resolving from part 1
        val res1 = SplitArchiveResolver.resolveSplitParts(part1)
        assertTrue(res1 is SplitResolution.Success)
        val success1 = res1 as SplitResolution.Success
        assertEquals(3, success1.parts.size)
        assertEquals(listOf(part1, part2, part3), success1.parts)
        assertEquals(450L, success1.totalSize)
        assertTrue(success1.isSplit)

        // Test resolving from part 2 (should also find all parts 1, 2, 3!)
        val res2 = SplitArchiveResolver.resolveSplitParts(part2)
        assertTrue(res2 is SplitResolution.Success)
        val success2 = res2 as SplitResolution.Success
        assertEquals(listOf(part1, part2, part3), success2.parts)

        // Test resolving from part 3
        val res3 = SplitArchiveResolver.resolveSplitParts(part3)
        assertTrue(res3 is SplitResolution.Success)
        val success3 = res3 as SplitResolution.Success
        assertEquals(listOf(part1, part2, part3), success3.parts)
    }

    @Test
    fun resolveSplitParts_missingFirstPart_returnsMissingParts() {
        val folder = tempFolder.newFolder("missing_first")
        // Only part 2 and 3 exist, part 1 is missing
        val part2 = File(folder, "data.7z.002").apply { writeBytes(ByteArray(50)) }
        val part3 = File(folder, "data.7z.003").apply { writeBytes(ByteArray(50)) }

        val res = SplitArchiveResolver.resolveSplitParts(part2)
        assertTrue(res is SplitResolution.MissingParts)
        val missing = res as SplitResolution.MissingParts
        assertEquals(1, missing.expectedPartIndex)
        assertEquals("data.7z.001", missing.missingPartName)
    }

    @Test
    fun resolveSplitParts_missingIntermediatePart_returnsMissingParts() {
        val folder = tempFolder.newFolder("missing_middle")
        // Parts 1 and 3 exist, part 2 is missing
        val part1 = File(folder, "archive.7z.001").apply { writeBytes(ByteArray(50)) }
        val part3 = File(folder, "archive.7z.003").apply { writeBytes(ByteArray(50)) }

        val res = SplitArchiveResolver.resolveSplitParts(part1)
        assertTrue(res is SplitResolution.MissingParts)
        val missing = res as SplitResolution.MissingParts
        assertEquals(2, missing.expectedPartIndex)
        assertEquals("archive.7z.002", missing.missingPartName)
    }

    @Test
    fun resolveSplitParts_genericNumberedExtension_resolvesSuccessfully() {
        val folder = tempFolder.newFolder("generic_split")
        val part1 = File(folder, "archive.tar.001").apply { writeBytes(ByteArray(60)) }
        val part2 = File(folder, "archive.tar.002").apply { writeBytes(ByteArray(80)) }

        val res = SplitArchiveResolver.resolveSplitParts(part1)
        assertTrue(res is SplitResolution.Success)
        val success = res as SplitResolution.Success
        assertEquals(2, success.parts.size)
        assertEquals(listOf(part1, part2), success.parts)
        assertEquals(140L, success.totalSize)
    }

    @Test
    fun resolveSplitParts_singleNonSplitFile_returnsNotSplit() {
        val singleFile = tempFolder.newFile("regular.txt").apply { writeText("Hello") }
        val res = SplitArchiveResolver.resolveSplitParts(singleFile)
        assertTrue(res is SplitResolution.NotSplit)
    }

    @Test
    fun getSplitVolumeName_formatsCorrectVolumeNames() {
        val base7z = File(tempFolder.root, "backup.7z")
        val vol1 = SplitArchiveResolver.getSplitVolumeName(base7z, 1)
        assertEquals("backup.7z.001", vol1.name)

        val vol42 = SplitArchiveResolver.getSplitVolumeName(base7z, 42)
        assertEquals("backup.7z.042", vol42.name)

        val vol105 = SplitArchiveResolver.getSplitVolumeName(base7z, 105)
        assertEquals("backup.7z.105", vol105.name)

        // Without .7z extension initially
        val baseNoExt = File(tempFolder.root, "mybackup")
        val volNoExt1 = SplitArchiveResolver.getSplitVolumeName(baseNoExt, 1)
        assertEquals("mybackup.7z.001", volNoExt1.name)
    }
}
