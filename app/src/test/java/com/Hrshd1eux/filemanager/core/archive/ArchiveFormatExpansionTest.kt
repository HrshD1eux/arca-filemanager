package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.IsoEngine
import com.Hrshd1eux.filemanager.core.archive.engine.RarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.archive.engine.StandaloneCompressorEngine
import com.Hrshd1eux.filemanager.core.archive.engine.TarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyException
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.archive.split.MissingSplitVolumeException
import com.Hrshd1eux.filemanager.core.archive.split.SplitArchiveResolver
import com.Hrshd1eux.filemanager.core.archive.split.SplitResolution
import com.Hrshd1eux.filemanager.feature.archive.ArchiveViewerUiState
import com.Hrshd1eux.filemanager.feature.archive.ArchiveViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveFormatExpansionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var zipEngine: ZipEngine
    private lateinit var sevenZEngine: SevenZEngine
    private lateinit var tarEngine: TarEngine
    private lateinit var rarEngine: RarEngine
    private lateinit var isoEngine: IsoEngine
    private lateinit var standaloneEngine: StandaloneCompressorEngine
    private lateinit var viewModel: ArchiveViewerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        zipEngine = ZipEngine(testDispatcher)
        sevenZEngine = SevenZEngine(testDispatcher)
        tarEngine = TarEngine(testDispatcher)
        rarEngine = RarEngine(testDispatcher)
        isoEngine = IsoEngine(testDispatcher)
        standaloneEngine = StandaloneCompressorEngine(testDispatcher)

        viewModel = ArchiveViewerViewModel(
            ioDispatcher = testDispatcher,
            zipEngine = zipEngine,
            sevenZEngine = sevenZEngine,
            tarEngine = tarEngine,
            rarEngine = rarEngine,
            isoEngine = isoEngine,
            standaloneCompressorEngine = standaloneEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Milestone 1.4.1 & 1.4.10: Corrupted Archive Error Handling ---

    @Test
    fun corruptedZipArchive_throwsIOException() = runTest(testDispatcher) {
        val badZip = File(tempFolder.root, "corrupt.zip").apply {
            writeBytes("This is not a valid zip archive header".toByteArray())
        }
        try {
            zipEngine.listEntries(badZip)
            fail("Expected IOException on corrupted ZIP file")
        } catch (e: IOException) {
            assertTrue("Message should mention corrupted or invalid ZIP", e.message?.contains("ZIP", ignoreCase = true) == true)
        }
    }

    @Test
    fun corrupted7zArchive_throwsIOException() = runTest(testDispatcher) {
        val bad7z = File(tempFolder.root, "corrupt.7z").apply {
            writeBytes("Garbage 7z header bytes 123456789".toByteArray())
        }
        try {
            sevenZEngine.listEntries(bad7z)
            fail("Expected IOException on corrupted 7z file")
        } catch (e: IOException) {
            assertTrue("Message should mention corrupted or invalid 7z", e.message?.contains("7z", ignoreCase = true) == true)
        }
    }

    @Test
    fun corruptedTarArchive_throwsIOException() = runTest(testDispatcher) {
        val badTarGz = File(tempFolder.root, "corrupt.tar.gz").apply {
            writeBytes("Not gzip compressed data".toByteArray())
        }
        try {
            tarEngine.listEntries(badTarGz)
            fail("Expected IOException on corrupted TAR.GZ file")
        } catch (e: IOException) {
            assertTrue("Message should mention corrupted or invalid TAR", e.message?.contains("TAR", ignoreCase = true) == true)
        }
    }

    @Test
    fun corruptedRarArchive_throwsIOException() = runTest(testDispatcher) {
        val badRar = File(tempFolder.root, "corrupt.rar").apply {
            writeBytes("Invalid RAR signature bytes".toByteArray())
        }
        try {
            rarEngine.listEntries(badRar)
            fail("Expected IOException on corrupted RAR file")
        } catch (e: IOException) {
            assertTrue("Message should mention corrupted or invalid RAR", e.message?.contains("RAR", ignoreCase = true) == true)
        }
    }

    @Test
    fun corruptedIsoArchive_throwsIOException() = runTest(testDispatcher) {
        val badIso = File(tempFolder.root, "corrupt.iso").apply {
            writeBytes(ByteArray(2048) { 0xFF.toByte() })
        }
        try {
            isoEngine.listEntries(badIso)
            fail("Expected IOException on corrupted ISO file")
        } catch (e: IOException) {
            assertTrue("Message should mention corrupted or invalid ISO", e.message?.contains("ISO", ignoreCase = true) == true)
        }
    }

    @Test
    fun corruptedGzip_throwsIOException() = runTest(testDispatcher) {
        val badGz = File(tempFolder.root, "bad.gz").apply {
            writeBytes("Not gzip stream".toByteArray())
        }
        try {
            standaloneEngine.listEntries(badGz)
            fail("Expected IOException on corrupted GZIP stream")
        } catch (e: IOException) {
            assertTrue("Message should mention GZIP", e.message?.contains("GZIP", ignoreCase = true) == true)
        }
    }

    // --- Milestones 1.4.6 & 1.4.7: Standalone GZIP, BZIP2, and XZ ---

    @Test
    fun standaloneGzip_compressionAndExtraction_roundTripsCorrectly() = runTest(testDispatcher) {
        val original = File(tempFolder.root, "notes.txt").apply {
            writeText("ARCA standalone compression test content for GZIP engine.")
        }
        val targetGz = File(tempFolder.root, "notes.txt.gz")

        // 1. Compress
        val compressResult = standaloneEngine.compressFile(original, targetGz)
        assertTrue(compressResult.isSuccess)
        assertTrue(targetGz.exists())

        // 2. List entries & verify ISIZE probing
        val entries = standaloneEngine.listEntries(targetGz)
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("notes.txt", entry.name)
        assertEquals(original.length(), entry.uncompressedSize)
        assertEquals(targetGz.length(), entry.compressedSize)

        // 3. Test integrity
        val testReport = standaloneEngine.testArchive(targetGz)
        assertTrue("Test report should pass", testReport.isPassed)
        assertEquals(1, testReport.verifiedEntries)
        assertTrue(testReport.corruptedEntries.isEmpty())

        // 4. Extract
        val extractDir = tempFolder.newFolder("extracted_gz")
        val extractResult = standaloneEngine.extractAll(targetGz, extractDir)
        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrThrow())

        val extractedFile = File(extractDir, "notes.txt")
        assertTrue(extractedFile.exists())
        assertEquals(original.readText(), extractedFile.readText())
    }

    @Test
    fun standaloneBzip2_compressionAndExtraction_roundTripsCorrectly() = runTest(testDispatcher) {
        val original = File(tempFolder.root, "data.csv").apply {
            writeText("id,name,value\n1,Alpha,100\n2,Beta,200\n")
        }
        val targetBz2 = File(tempFolder.root, "data.csv.bz2")

        // 1. Compress
        val compressResult = standaloneEngine.compressFile(original, targetBz2)
        assertTrue(compressResult.isSuccess)
        assertTrue(targetBz2.exists())

        // 2. List entries
        val entries = standaloneEngine.listEntries(targetBz2)
        assertEquals(1, entries.size)
        assertEquals("data.csv", entries.first().name)

        // 3. Test integrity
        val testReport = standaloneEngine.testArchive(targetBz2)
        assertTrue(testReport.isPassed)

        // 4. Extract
        val extractDir = tempFolder.newFolder("extracted_bz2")
        val extractResult = standaloneEngine.extractAll(targetBz2, extractDir)
        assertTrue(extractResult.isSuccess)

        val extractedFile = File(extractDir, "data.csv")
        assertTrue(extractedFile.exists())
        assertEquals(original.readText(), extractedFile.readText())
    }

    @Test
    fun standaloneXz_compressionAndExtraction_roundTripsCorrectly() = runTest(testDispatcher) {
        val original = File(tempFolder.root, "code.py").apply {
            writeText("def hello():\n    print('Hello ARCA XZ')\n")
        }
        val targetXz = File(tempFolder.root, "code.py.xz")

        // 1. Compress
        val compressResult = standaloneEngine.compressFile(original, targetXz)
        assertTrue(compressResult.isSuccess)
        assertTrue(targetXz.exists())

        // 2. List entries
        val entries = standaloneEngine.listEntries(targetXz)
        assertEquals(1, entries.size)
        assertEquals("code.py", entries.first().name)

        // 3. Test integrity
        val testReport = standaloneEngine.testArchive(targetXz)
        assertTrue(testReport.isPassed)

        // 4. Extract
        val extractDir = tempFolder.newFolder("extracted_xz")
        val extractResult = standaloneEngine.extractAll(targetXz, extractDir)
        assertTrue(extractResult.isSuccess)

        val extractedFile = File(extractDir, "code.py")
        assertTrue(extractedFile.exists())
        assertEquals(original.readText(), extractedFile.readText())
    }

    // --- Milestone 1.4.8: Multipart Split Archives ---

    @Test
    fun splitArchive_rarNewStyle_resolvesContiguousPartsAndExplainsMissing() {
        val folder = tempFolder.newFolder("rar_split_test")
        val part1 = File(folder, "movie.part01.rar").apply { writeBytes(ByteArray(100)) }
        val part2 = File(folder, "movie.part02.rar").apply { writeBytes(ByteArray(100)) }
        val part3 = File(folder, "movie.part03.rar").apply { writeBytes(ByteArray(100)) }

        // Test resolving complete set from part 2
        val res = SplitArchiveResolver.resolveSplitParts(part2)
        assertTrue(res is SplitResolution.Success)
        val success = res as SplitResolution.Success
        assertEquals(3, success.parts.size)
        assertEquals(listOf(part1, part2, part3), success.parts)
        assertEquals(300L, success.totalSize)

        // Test missing part 2
        part2.delete()
        val missingRes = SplitArchiveResolver.resolveSplitParts(part1)
        assertTrue(missingRes is SplitResolution.MissingParts)
        val missing = missingRes as SplitResolution.MissingParts
        assertEquals(2, missing.expectedPartIndex)
        assertEquals("movie.part02.rar", missing.missingPartName)
    }

    @Test
    fun splitArchive_rarOldStyle_resolvesContiguousPartsAndExplainsMissing() {
        val folder = tempFolder.newFolder("rar_old_split_test")
        val baseRar = File(folder, "game.rar").apply { writeBytes(ByteArray(50)) }
        val partR00 = File(folder, "game.r00").apply { writeBytes(ByteArray(50)) }
        val partR01 = File(folder, "game.r01").apply { writeBytes(ByteArray(50)) }

        val res = SplitArchiveResolver.resolveSplitParts(partR00)
        assertTrue(res is SplitResolution.Success)
        val success = res as SplitResolution.Success
        assertEquals(3, success.parts.size)
        assertEquals(listOf(baseRar, partR00, partR01), success.parts)

        // Test missing base game.rar
        baseRar.delete()
        val missingRes = SplitArchiveResolver.resolveSplitParts(partR00)
        assertTrue(missingRes is SplitResolution.MissingParts)
        assertEquals(1, (missingRes as SplitResolution.MissingParts).expectedPartIndex)
        assertEquals("game.rar", missingRes.missingPartName)
    }

    @Test
    fun splitArchive_zipParts_resolvesContiguousPartsAndExplainsMissing() {
        val folder = tempFolder.newFolder("zip_split_test")
        val partZ01 = File(folder, "bundle.z01").apply { writeBytes(ByteArray(60)) }
        val partZ02 = File(folder, "bundle.z02").apply { writeBytes(ByteArray(60)) }
        val baseZip = File(folder, "bundle.zip").apply { writeBytes(ByteArray(60)) }

        val res = SplitArchiveResolver.resolveSplitParts(partZ01)
        assertTrue(res is SplitResolution.Success)
        val success = res as SplitResolution.Success
        assertEquals(3, success.parts.size)
        assertEquals(listOf(partZ01, partZ02, baseZip), success.parts)

        // Test missing final bundle.zip
        baseZip.delete()
        val missingRes = SplitArchiveResolver.resolveSplitParts(partZ01)
        assertTrue(missingRes is SplitResolution.MissingParts)
        assertEquals("bundle.zip", (missingRes as SplitResolution.MissingParts).missingPartName)
    }

    // --- ArchiveViewerViewModel UI Integration ---

    @Test
    fun archiveViewerViewModel_loadsStandaloneGzip_showsValidSuccessState() = runTest(testDispatcher) {
        val file = File(tempFolder.root, "report.log").apply { writeText("Sample log line 1\nSample log line 2\n") }
        val gzFile = File(tempFolder.root, "report.log.gz")
        standaloneEngine.compressFile(file, gzFile)

        viewModel.loadArchive(gzFile.absolutePath)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Success: $state", state is ArchiveViewerUiState.Success)
        val success = state as ArchiveViewerUiState.Success
        assertEquals("report.log.gz", success.archiveName)
        assertEquals(1, success.items.size)
        assertEquals("report.log", success.items.first().name)
        assertTrue(success.isReadOnly)
        assertNotNull(success.formatBadge)
        assertTrue(success.formatBadge!!.contains("GZ", ignoreCase = true))
    }

    @Test
    fun archiveViewerViewModel_missingSplitVolume_displaysErrorState() = runTest(testDispatcher) {
        val folder = tempFolder.newFolder("broken_7z_split")
        // Only part 2 exists, part 1 is missing
        val part2 = File(folder, "data.7z.002").apply { writeBytes(ByteArray(100)) }

        viewModel.loadArchive(part2.absolutePath)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Error when volume missing: $state", state is ArchiveViewerUiState.Error)
        val errorState = state as ArchiveViewerUiState.Error
        assertTrue(errorState.message.contains("missing", ignoreCase = true))
    }

    // --- Path Traversal Guard Validation ---

    @Test
    fun zipSlipAttack_failsSafely() {
        val destDir = tempFolder.newFolder("dest")
        try {
            ArchiveSafetyGuard.resolveAndValidateTarget("../../evil.txt", destDir)
            fail("Expected ArchiveSafetyException for path traversal attempt")
        } catch (_: ArchiveSafetyException) {
            // Success
        }
    }
}
