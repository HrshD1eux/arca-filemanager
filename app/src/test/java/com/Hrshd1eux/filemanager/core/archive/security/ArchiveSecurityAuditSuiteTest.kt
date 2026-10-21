package com.Hrshd1eux.filemanager.core.archive.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Archive Security Audit Test Suite for Milestones 1.5.51 – 1.5.55:
 * Malformed Archives, Path Traversal, Archive Bombs, and Threat Mitigation.
 *
 * Verifies:
 * 1. Path Traversal Defense (Zip Slip): UNIX, Windows, and mixed ../ traversals are strictly blocked.
 * 2. Absolute Path Escapes: Target canonical paths outside destination throw PathTraversalException.
 * 3. Archive Bomb Mitigation: 100:1 compression ratio limit and cumulative size limits enforced.
 * 4. NUL Byte and Control Character Injection: Malicious embedded characters cause immediate rejection.
 * 5. Windows Device Name Protection: CON, PRN, AUX, NUL, COM1-9, LPT1-9 cannot create dangerous filesystem handles.
 * 6. Header Validation: Negative and excessive (>50,000) entry counts throw MalformedArchiveException.
 */
class ArchiveSecurityAuditSuiteTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun pathTraversal_variousTraversalVectors_strictlyBlocked() {
        val destDir = tempFolder.newFolder("safe_extract_root")

        val maliciousVectors = listOf(
            "../../etc/shadow",
            "..\\..\\Windows\\System32\\calc.exe",
            "safe_folder/../../../escaped.txt",
            "/../escaped.txt",
            "/../../root.sh",
            "C:\\Windows\\System32\\drivers\\etc\\hosts",
            "nested/dir/../../../../../../../../tmp/pwned",
            "./.././../secret.key"
        )

        for (vector in maliciousVectors) {
            assertThrows("Path traversal attack vector '$vector' must be blocked", PathTraversalException::class.java) {
                ArchiveSafetyGuard.resolveAndValidateTarget(vector, destDir)
            }
        }
    }

    @Test
    fun leadingSlash_isSanitizedToExtractSafelyInsideDestination() {
        val destDir = tempFolder.newFolder("safe_extract_root2")
        val sanitized = ArchiveSafetyGuard.resolveAndValidateTarget("/safe_folder/file.txt", destDir)
        assertTrue(sanitized.canonicalPath.startsWith(destDir.canonicalPath))
    }

    @Test
    fun suspiciousPath_nulByteAndControlCharacters_strictlyBlocked() {
        val injectionVectors = listOf(
            "document.pdf\u0000.exe",
            "test\u0000file.png",
            "bad\u0001header.bin",
            "evil\u001fpayload.dat"
        )

        for (vector in injectionVectors) {
            assertThrows("Suspicious path vector '$vector' must be rejected", SuspiciousPathException::class.java) {
                ArchiveSafetyGuard.sanitizeAndValidateEntryName(vector)
            }
        }
    }

    @Test
    fun reservedDeviceNames_windowsSpecialHandles_strictlyBlocked() {
        val deviceNames = listOf(
            "CON",
            "con.txt",
            "PRN",
            "AUX.dat",
            "NUL",
            "folder/COM1",
            "COM9.log",
            "sub/LPT1",
            "LPT8.tmp"
        )

        for (device in deviceNames) {
            assertThrows("Device name '$device' must be blocked", SuspiciousPathException::class.java) {
                ArchiveSafetyGuard.sanitizeAndValidateEntryName(device)
            }
        }
    }

    @Test
    fun archiveBomb_highRatioDecompression_throwsZipBombException() {
        // 12 MB uncompressed from 50 KB compressed -> ratio = 240:1 (> 100:1 limit)
        assertThrows(ZipBombException::class.java) {
            ArchiveSafetyGuard.validateExpansionStream(
                entryName = "ratio_bomb.xml",
                uncompressedBytesRead = 12L * 1024 * 1024,
                compressedEntrySize = 50L * 1024,
                cumulativeUncompressedBytes = 12L * 1024 * 1024
            )
        }
    }

    @Test
    fun excessiveSingleEntry_exceedsFiveGigabytes_throwsExcessiveArchiveSizeException() {
        val oversizedSingle = ArchiveSafetyGuard.MAX_SINGLE_ENTRY_BYTES + 1
        assertThrows(ExcessiveArchiveSizeException::class.java) {
            ArchiveSafetyGuard.validateExpansionStream(
                entryName = "massive.bin",
                uncompressedBytesRead = oversizedSingle,
                compressedEntrySize = 1024 * 1024,
                cumulativeUncompressedBytes = oversizedSingle
            )
        }
    }

    @Test
    fun cumulativeSession_exceedsTwentyFiveGigabytes_throwsExcessiveArchiveSizeException() {
        val oversizedCumulative = ArchiveSafetyGuard.MAX_CUMULATIVE_SESSION_BYTES + 1
        assertThrows(ExcessiveArchiveSizeException::class.java) {
            ArchiveSafetyGuard.validateExpansionStream(
                entryName = "part.bin",
                uncompressedBytesRead = 1024,
                compressedEntrySize = 1000,
                cumulativeUncompressedBytes = oversizedCumulative
            )
        }
    }

    @Test
    fun archiveHeaderValidation_enforcesEntryLimits() {
        // Valid counts
        ArchiveSafetyGuard.validateArchiveHeader(0)
        ArchiveSafetyGuard.validateArchiveHeader(1_000)
        ArchiveSafetyGuard.validateArchiveHeader(ArchiveSafetyGuard.MAX_ENTRIES_PER_ARCHIVE)

        // Exceeding maximum
        assertThrows(MalformedArchiveException::class.java) {
            ArchiveSafetyGuard.validateArchiveHeader(ArchiveSafetyGuard.MAX_ENTRIES_PER_ARCHIVE + 1)
        }

        // Negative count
        assertThrows(MalformedArchiveException::class.java) {
            ArchiveSafetyGuard.validateArchiveHeader(-5)
        }
    }

    @Test
    fun legitimatePaths_resolveSafelyInsideDestination() {
        val destDir = tempFolder.newFolder("legit_dest")
        val legitimate = listOf(
            "readme.txt",
            "documents/invoice_2026.pdf",
            "images/vacation/beach (1).jpg",
            "nested/deep/directory/tree/file.json"
        )

        for (path in legitimate) {
            val resolved = ArchiveSafetyGuard.resolveAndValidateTarget(path, destDir)
            assertNotNull(resolved)
            assertTrue("Target must be inside destination: ${resolved.canonicalPath}",
                resolved.canonicalPath.startsWith(destDir.canonicalPath))
        }
    }
}
