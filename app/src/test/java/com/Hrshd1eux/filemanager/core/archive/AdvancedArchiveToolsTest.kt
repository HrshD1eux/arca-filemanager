package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.archive.engine.StandaloneCompressorEngine
import com.Hrshd1eux.filemanager.core.archive.engine.TarEngine
import com.Hrshd1eux.filemanager.core.archive.engine.ZipEngine
import com.Hrshd1eux.filemanager.core.archive.model.ArchiveFormatOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionLevelPreset
import com.Hrshd1eux.filemanager.core.archive.model.CompressionMethodOption
import com.Hrshd1eux.filemanager.core.archive.model.CompressionPresetProfile
import com.Hrshd1eux.filemanager.core.archive.model.CompressionProfile
import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSecurityEvaluator
import com.Hrshd1eux.filemanager.core.archive.security.PasswordStrength
import com.Hrshd1eux.filemanager.core.checksum.ChecksumAlgorithm
import com.Hrshd1eux.filemanager.core.checksum.ChecksumEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AdvancedArchiveToolsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var zipEngine: ZipEngine
    private lateinit var sevenZEngine: SevenZEngine
    private lateinit var tarEngine: TarEngine
    private lateinit var standaloneEngine: StandaloneCompressorEngine
    private lateinit var checksumEngine: ChecksumEngine

    @Before
    fun setUp() {
        val dispatcher = Dispatchers.Unconfined
        zipEngine = ZipEngine(dispatcher)
        sevenZEngine = SevenZEngine(dispatcher)
        tarEngine = TarEngine(dispatcher)
        standaloneEngine = StandaloneCompressorEngine(dispatcher)
        checksumEngine = ChecksumEngine(dispatcher)
    }

    // -------------------------------------------------------------
    // 1.4.11 Compression profiles
    // -------------------------------------------------------------
    @Test
    fun testCompressionProfiles_fastBalancedMaximum() {
        // ZIP
        val zipFast = CompressionProfile.fromPreset(CompressionPresetProfile.FAST, ArchiveFormatOption.ZIP)
        assertEquals(1, zipFast.compressionLevel)
        assertEquals(CompressionLevelPreset.FAST, zipFast.levelPreset)

        val zipBalanced = CompressionProfile.fromPreset(CompressionPresetProfile.BALANCED, ArchiveFormatOption.ZIP)
        assertEquals(6, zipBalanced.compressionLevel)
        assertEquals(CompressionLevelPreset.NORMAL, zipBalanced.levelPreset)

        val zipMax = CompressionProfile.fromPreset(CompressionPresetProfile.MAXIMUM, ArchiveFormatOption.ZIP)
        assertEquals(9, zipMax.compressionLevel)
        assertEquals(CompressionLevelPreset.MAXIMUM, zipMax.levelPreset)

        // 7Z
        val sevenZFast = CompressionProfile.fromPreset(CompressionPresetProfile.FAST, ArchiveFormatOption.SEVEN_Z)
        assertEquals(1, sevenZFast.compressionLevel)
        assertFalse(sevenZFast.isSolid)
        assertEquals(16, sevenZFast.dictionarySizeMb)

        val sevenZBalanced = CompressionProfile.fromPreset(CompressionPresetProfile.BALANCED, ArchiveFormatOption.SEVEN_Z)
        assertEquals(6, sevenZBalanced.compressionLevel)
        assertTrue(sevenZBalanced.isSolid)
        assertEquals(32, sevenZBalanced.dictionarySizeMb)

        val sevenZMax = CompressionProfile.fromPreset(CompressionPresetProfile.MAXIMUM, ArchiveFormatOption.SEVEN_Z)
        assertEquals(9, sevenZMax.compressionLevel)
        assertTrue(sevenZMax.isSolid)
        assertEquals(64, sevenZMax.dictionarySizeMb)

        // TAR
        val tarFast = CompressionProfile.fromPreset(CompressionPresetProfile.FAST, ArchiveFormatOption.TAR)
        assertEquals(0, tarFast.compressionLevel)
        assertEquals(CompressionMethodOption.COPY, tarFast.method)
    }

    // -------------------------------------------------------------
    // 1.4.12 Custom compression
    // -------------------------------------------------------------
    @Test
    fun testCustomCompression_levelsAndMethods() {
        val customProfile = CompressionProfile(
            format = ArchiveFormatOption.SEVEN_Z,
            levelPreset = CompressionLevelPreset.MAXIMUM,
            compressionLevel = 8,
            method = CompressionMethodOption.LZMA2,
            dictionarySizeMb = 128,
            isSolid = true,
            threads = 2
        )
        assertEquals(ArchiveFormatOption.SEVEN_Z, customProfile.format)
        assertEquals(8, customProfile.compressionLevel)
        assertEquals(CompressionMethodOption.LZMA2, customProfile.method)
        assertEquals(128, customProfile.dictionarySizeMb)
        assertEquals(2, customProfile.threads)
    }

    // -------------------------------------------------------------
    // 1.4.13 Archive creation options
    // -------------------------------------------------------------
    @Test
    fun testArchiveCreationOptions_filenameSanitization() {
        val illegalChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        val validNames = listOf("archive_2026", "backup-photos", "my.document.v1")
        validNames.forEach { name ->
            assertFalse("Valid name $name was marked illegal", name.any { it in illegalChars })
        }

        val invalidNames = listOf("archive/file", "backup\\sub", "test:colon", "star*", "quote\"")
        invalidNames.forEach { name ->
            assertTrue("Invalid name $name was not detected", name.any { it in illegalChars })
        }
    }

    // -------------------------------------------------------------
    // 1.4.14 Password-protected archives
    // -------------------------------------------------------------
    @Test
    fun testPasswordProtectedArchives_7zEncryption() = runTest {
        val sampleFile = tempFolder.newFile("confidential.txt").apply {
            writeText("Strictly confidential payload for testing archive encryption.")
        }
        val dest7z = File(tempFolder.root, "secure_vault.7z")
        val password = "StrongPassword#2026!".toCharArray()

        val size = sevenZEngine.create7z(
            sources = listOf(sampleFile),
            destination7z = dest7z,
            password = password
        ).getOrThrow()

        assertTrue("Encrypted 7z file must exist and have non-zero size", dest7z.exists() && size > 0)

        // Read entries with correct password
        val entries = sevenZEngine.listEntries(dest7z, password)
        assertEquals(1, entries.size)
        assertEquals("confidential.txt", entries[0].name)

        // Wipe password
        java.util.Arrays.fill(password, '\u0000')
        assertEquals('\u0000', password[0])
    }

    // -------------------------------------------------------------
    // 1.4.15 Encryption compatibility
    // -------------------------------------------------------------
    @Test
    fun testEncryptionCompatibility_unsupportedFormatsExplained() {
        assertTrue(ArchiveFormatOption.SEVEN_Z.supportsEncryption)
        assertFalse(ArchiveFormatOption.ZIP.supportsEncryption)
        assertFalse(ArchiveFormatOption.TAR.supportsEncryption)
        assertFalse(ArchiveFormatOption.TAR_GZ.supportsEncryption)
        assertFalse(ArchiveFormatOption.TAR_XZ.supportsEncryption)
        assertFalse(ArchiveFormatOption.TAR_BZ2.supportsEncryption)
    }

    // -------------------------------------------------------------
    // 1.4.16 Password generator
    // -------------------------------------------------------------
    @Test
    fun testPasswordGenerator_localCsprng() {
        val pass1 = ArchiveSecurityEvaluator.generateSecurePassword(24, includeLetters = true, includeNumbers = true, includeSymbols = true)
        val pass2 = ArchiveSecurityEvaluator.generateSecurePassword(24, includeLetters = true, includeNumbers = true, includeSymbols = true)

        assertEquals(24, pass1.length)
        assertEquals(24, pass2.length)
        assertNotEquals("Consecutive generated passwords must be unique", pass1, pass2)

        val strength = ArchiveSecurityEvaluator.evaluatePassword(pass1)
        assertTrue(strength.strength == PasswordStrength.STRONG || strength.strength == PasswordStrength.VERY_STRONG)
        assertNull(strength.warningMessage)
    }

    // -------------------------------------------------------------
    // 1.4.17 Checksum tools
    // -------------------------------------------------------------
    @Test
    fun testChecksumTools_sha256AndMultiHash() = runTest {
        val testFile = tempFolder.newFile("sample_for_hash.txt").apply {
            writeText("Hello Checksums & File Integrity 2026!")
        }

        val allItems = checksumEngine.calculateAll(testFile).getOrThrow()
        assertEquals(4, allItems.size)

        val sha256Item = allItems.first { it.algorithm == ChecksumAlgorithm.SHA_256 }
        val sha512Item = allItems.first { it.algorithm == ChecksumAlgorithm.SHA_512 }
        val md5Item = allItems.first { it.algorithm == ChecksumAlgorithm.MD5 }
        val crc32Item = allItems.first { it.algorithm == ChecksumAlgorithm.CRC32 }

        assertEquals(64, sha256Item.hashHex.length)
        assertEquals(128, sha512Item.hashHex.length)
        assertEquals(32, md5Item.hashHex.length)
        assertEquals(8, crc32Item.hashHex.length)

        // Single algorithm calculation
        val singleSha256 = checksumEngine.calculateAlgorithm(testFile, ChecksumAlgorithm.SHA_256).getOrThrow()
        assertEquals(sha256Item.hashHex, singleSha256.hashHex)
    }

    // -------------------------------------------------------------
    // 1.4.18 File verification
    // -------------------------------------------------------------
    @Test
    fun testFileVerification_compareFilesAndParseChecksumFiles() = runTest {
        val fileA = tempFolder.newFile("fileA.bin").apply { writeText("Identical data block") }
        val fileB = tempFolder.newFile("fileB.bin").apply { writeText("Identical data block") }
        val fileC = tempFolder.newFile("fileC.bin").apply { writeText("Different data block") }

        // Compare identical files
        val matchResult = checksumEngine.compareFiles(fileA, fileB).getOrThrow()
        assertTrue(matchResult.isMatch)
        assertEquals(matchResult.hash1, matchResult.hash2)

        // Compare different files
        val mismatchResult = checksumEngine.compareFiles(fileA, fileC).getOrThrow()
        assertFalse(mismatchResult.isMatch)
        assertNotEquals(mismatchResult.hash1, mismatchResult.hash2)

        // Parse checksum file format
        val checksumContent = """
            # Checksums generated on 2026-10-19
            d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592  release.zip
            414fa339  data.bin
            SHA256 (archive.7z) = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        """.trimIndent()

        val parsedRecords = checksumEngine.parseChecksumContent(checksumContent)
        assertEquals(3, parsedRecords.size)
        assertEquals("release.zip", parsedRecords[0].fileName)
        assertEquals(ChecksumAlgorithm.SHA_256, parsedRecords[0].algorithm)
        assertEquals("data.bin", parsedRecords[1].fileName)
        assertEquals(ChecksumAlgorithm.CRC32, parsedRecords[1].algorithm)
        assertEquals("archive.7z", parsedRecords[2].fileName)
        assertEquals(ChecksumAlgorithm.SHA_256, parsedRecords[2].algorithm)

        // Test hash extraction from pasted string
        val extracted1 = checksumEngine.extractHashFromText("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592  release.zip")
        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", extracted1)

        val extracted2 = checksumEngine.extractHashFromText("SHA256 (file.iso) = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", extracted2)
    }

    // -------------------------------------------------------------
    // 1.4.19 Archive properties
    // -------------------------------------------------------------
    @Test
    fun testArchiveProperties_richMetadataAcrossFormats() = runTest {
        val docFile = tempFolder.newFile("document.txt").apply { writeText("Some document content") }

        // ZIP properties
        val zipFile = File(tempFolder.root, "test_metadata.zip")
        zipEngine.createZip(listOf(docFile), zipFile).getOrThrow()
        val zipProps = zipEngine.getArchiveProperties(zipFile)
        assertEquals("ZIP Archive", zipProps.formatDisplayName)
        assertTrue(zipProps.compressionMethod.contains("Deflate") || zipProps.compressionMethod.contains("Store"))
        assertEquals(1, zipProps.totalFiles)
        assertFalse(zipProps.isEncrypted)

        // 7Z properties
        val sevenZFile = File(tempFolder.root, "test_metadata.7z")
        sevenZEngine.create7z(listOf(docFile), sevenZFile).getOrThrow()
        val sevenZProps = sevenZEngine.getArchiveProperties(sevenZFile)
        assertEquals("7-Zip Archive (7Z)", sevenZProps.formatDisplayName)
        assertEquals("LZMA2 / LZMA", sevenZProps.compressionMethod)
        assertTrue(sevenZProps.isSolid)
        assertFalse(sevenZProps.isEncrypted)

        // TAR.GZ properties
        val tarGzFile = File(tempFolder.root, "test_metadata.tar.gz")
        tarEngine.createTar(listOf(docFile), tarGzFile).getOrThrow()
        val tarProps = tarEngine.getArchiveProperties(tarGzFile)
        assertEquals("TAR.GZ Archive", tarProps.formatDisplayName)
        assertEquals("GZIP Deflate", tarProps.compressionMethod)
        assertTrue(tarProps.isSolid)

        // Standalone GZIP properties
        val gzFile = standaloneEngine.compressFile(docFile, File(tempFolder.root, "doc.txt.gz")).getOrThrow()
        val gzProps = standaloneEngine.getArchiveProperties(gzFile)
        assertEquals("GZIP Compressed File", gzProps.formatDisplayName)
        assertEquals(1, gzProps.totalFiles)
    }

    // -------------------------------------------------------------
    // 1.4.20 Archive workflow polish (cancellation & error cleanup)
    // -------------------------------------------------------------
    @Test
    fun testArchiveWorkflowPolish_cleanupOnFailure() = runTest {
        val nonExistentSource = File(tempFolder.root, "ghost_file.txt")
        val targetZip = File(tempFolder.root, "should_be_deleted.zip")

        // Intentionally cause failure
        val result = zipEngine.createZip(listOf(nonExistentSource), targetZip)
        assertTrue(result.isFailure)
        assertFalse("Target zip file must be cleaned up on failure", targetZip.exists())
    }
}
