package com.Hrshd1eux.filemanager.core.checksum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ChecksumEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var checksumEngine: ChecksumEngine

    @Before
    fun setUp() {
        checksumEngine = ChecksumEngine(Dispatchers.Unconfined)
    }

    @Test
    fun md5_isStrictlyLabeledAsLegacyChecksum() {
        assertEquals("Legacy checksum", ChecksumAlgorithm.MD5.label)
        assertFalse("MD5 must never be marked as cryptographically secure", ChecksumAlgorithm.MD5.isCryptographicallySecure)
        assertEquals("Error-detection code", ChecksumAlgorithm.CRC32.label)
        assertFalse(ChecksumAlgorithm.CRC32.isCryptographicallySecure)
        assertEquals("Secure hash", ChecksumAlgorithm.SHA_256.label)
        assertTrue(ChecksumAlgorithm.SHA_256.isCryptographicallySecure)
        assertEquals("High-security hash", ChecksumAlgorithm.SHA_512.label)
        assertTrue(ChecksumAlgorithm.SHA_512.isCryptographicallySecure)
    }

    @Test
    fun calculateAll_computesAccurateKnownHashes() = runTest {
        val testFile = tempFolder.newFile("vector.txt").apply {
            writeText("The quick brown fox jumps over the lazy dog")
        }

        val result = checksumEngine.calculateAll(testFile)
        assertTrue(result.isSuccess)

        val items = result.getOrNull()
        assertNotNull(items)
        assertEquals(4, items!!.size)

        val map = items.associate { it.algorithm to it.hashHex }

        assertEquals("414fa339", map[ChecksumAlgorithm.CRC32])
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", map[ChecksumAlgorithm.MD5])
        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", map[ChecksumAlgorithm.SHA_256])
        assertEquals(
            "07e547d9586f6a73f73fbac0435ed76951218fb7d0c8d788a309d785436bbb642e93a252a954f23912547d1e8a3b5ed6e1bfd7097821233fa0538f3db854fee6",
            map[ChecksumAlgorithm.SHA_512]
        )
    }

    @Test
    fun matchExpected_detectsMatchesWithSanitization() = runTest {
        val testFile = tempFolder.newFile("sample.txt").apply {
            writeText("The quick brown fox jumps over the lazy dog")
        }

        val items = checksumEngine.calculateAll(testFile).getOrThrow()

        // Exact match
        assertEquals(
            ChecksumAlgorithm.SHA_256,
            checksumEngine.matchExpected("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", items)
        )

        // Uppercase match
        assertEquals(
            ChecksumAlgorithm.SHA_256,
            checksumEngine.matchExpected("D7A8FBB307D7809469CA9ABCB0082E4F8D5651E46D3CDB762D02D0BF37C9E592", items)
        )

        // Formatted with colons and spaces
        assertEquals(
            ChecksumAlgorithm.MD5,
            checksumEngine.matchExpected("9E:10:7D:9D: 37:2B:B6:82 :6B:D8:1D:35:42:A4:19:D6", items)
        )

        assertEquals(
            ChecksumAlgorithm.CRC32,
            checksumEngine.matchExpected("414FA339", items)
        )

        // Non-matching hash
        assertNull(checksumEngine.matchExpected("deadbeef12345678", items))
    }
}
