package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.security.ArchiveSafetyGuard
import com.Hrshd1eux.filemanager.core.archive.security.ExcessiveArchiveSizeException
import com.Hrshd1eux.filemanager.core.archive.security.MalformedArchiveException
import com.Hrshd1eux.filemanager.core.archive.security.PathTraversalException
import com.Hrshd1eux.filemanager.core.archive.security.SuspiciousPathException
import com.Hrshd1eux.filemanager.core.archive.security.ZipBombException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchiveSafetyGuardTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test(expected = PathTraversalException::class)
    fun resolveAndValidateTarget_unixPathTraversal_throwsException() {
        val destDir = tempFolder.newFolder("safe_dest")
        ArchiveSafetyGuard.resolveAndValidateTarget("../../../../system/file", destDir)
    }

    @Test(expected = PathTraversalException::class)
    fun resolveAndValidateTarget_windowsPathTraversal_throwsException() {
        val destDir = tempFolder.newFolder("safe_dest")
        ArchiveSafetyGuard.resolveAndValidateTarget("..\\..\\..\\windows\\system32", destDir)
    }

    @Test(expected = SuspiciousPathException::class)
    fun sanitizeAndValidateEntryName_nulByteInjection_throwsException() {
        ArchiveSafetyGuard.sanitizeAndValidateEntryName("payload.jpg\u0000.sh")
    }

    @Test(expected = SuspiciousPathException::class)
    fun sanitizeAndValidateEntryName_windowsDeviceName_throwsException() {
        ArchiveSafetyGuard.sanitizeAndValidateEntryName("sub/CON.txt")
    }

    @Test(expected = SuspiciousPathException::class)
    fun sanitizeAndValidateEntryName_windowsAuxDevice_throwsException() {
        ArchiveSafetyGuard.sanitizeAndValidateEntryName("AUX")
    }

    @Test(expected = SuspiciousPathException::class)
    fun sanitizeAndValidateEntryName_controlCharacters_throwsException() {
        ArchiveSafetyGuard.sanitizeAndValidateEntryName("bad\u0007char.txt")
    }

    @Test
    fun resolveAndValidateTarget_validRelativePath_resolvesSafely() {
        val destDir = tempFolder.newFolder("safe_dest")
        val target = ArchiveSafetyGuard.resolveAndValidateTarget("docs/nested/file.txt", destDir)
        assertNotNull(target)
        assertTrue(target.canonicalPath.startsWith(destDir.canonicalPath))
    }

    @Test(expected = ZipBombException::class)
    fun validateExpansionStream_decompressionBombHighRatio_throwsException() {
        // 20 MB uncompressed from 50 KB compressed -> ratio = 400:1 (> 100:1 limit)
        ArchiveSafetyGuard.validateExpansionStream(
            entryName = "bomb.bin",
            uncompressedBytesRead = 20L * 1024 * 1024,
            compressedEntrySize = 50L * 1024,
            cumulativeUncompressedBytes = 20L * 1024 * 1024
        )
    }

    @Test(expected = ExcessiveArchiveSizeException::class)
    fun validateExpansionStream_exceedsCumulativeSessionLimit_throwsException() {
        val oversizedCumulative = ArchiveSafetyGuard.MAX_CUMULATIVE_SESSION_BYTES + 1024
        ArchiveSafetyGuard.validateExpansionStream(
            entryName = "large.bin",
            uncompressedBytesRead = 1000,
            compressedEntrySize = 900,
            cumulativeUncompressedBytes = oversizedCumulative
        )
    }

    @Test(expected = MalformedArchiveException::class)
    fun validateArchiveHeader_exceedsMaxEntriesLimit_throwsException() {
        ArchiveSafetyGuard.validateArchiveHeader(60_000)
    }

    @Test(expected = MalformedArchiveException::class)
    fun validateArchiveHeader_negativeEntriesCount_throwsException() {
        ArchiveSafetyGuard.validateArchiveHeader(-1)
    }
}
