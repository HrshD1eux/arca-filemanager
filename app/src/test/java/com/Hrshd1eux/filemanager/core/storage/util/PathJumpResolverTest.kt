package com.Hrshd1eux.filemanager.core.storage.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathJumpResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun resolve_directory_stripsQuotesAndFileUri() {
        val dir = tempFolder.newFolder("Documents")

        val quotedResult = PathJumpResolver.resolve("\"${dir.absolutePath}\"", tempFolder.root.absolutePath)
        assertTrue(quotedResult is PathResolutionResult.Valid)
        val dirTarget = (quotedResult as PathResolutionResult.Valid).target as PathJumpTarget.Directory
        assertEquals(dir.absolutePath, dirTarget.path)
        assertEquals("Documents", dirTarget.displayName)

        val uriPath = "file://${dir.absolutePath.replace('\\', '/')}"
        val uriResult = PathJumpResolver.resolve(uriPath, tempFolder.root.absolutePath)
        assertTrue(uriResult is PathResolutionResult.Valid)
        assertTrue((uriResult as PathResolutionResult.Valid).target is PathJumpTarget.Directory)
    }

    @Test
    fun resolve_file_returnsDocumentFileWithParentPath() {
        val dir = tempFolder.newFolder("Audio")
        val track = File(dir, "podcast.mp3").apply { writeBytes(ByteArray(256)) }

        val result = PathJumpResolver.resolve(track.absolutePath, tempFolder.root.absolutePath)
        assertTrue(result is PathResolutionResult.Valid)
        val fileTarget = (result as PathResolutionResult.Valid).target as PathJumpTarget.DocumentFile
        assertEquals(track.absolutePath, fileTarget.filePath)
        assertEquals(dir.absolutePath, fileTarget.parentPath)
        assertEquals("podcast.mp3", fileTarget.fileName)
        assertEquals(256L, fileTarget.sizeBytes)
    }

    @Test
    fun resolve_sdcardAliasMapsToStorageRoot() {
        val downloadDir = tempFolder.newFolder("Download")
        val result = PathJumpResolver.resolve("/sdcard/Download", tempFolder.root.absolutePath)
        assertTrue(result is PathResolutionResult.Valid)
        val dirTarget = (result as PathResolutionResult.Valid).target as PathJumpTarget.Directory
        assertEquals(downloadDir.absolutePath, dirTarget.path)
    }

    @Test
    fun resolve_rejectsMissingAndVaultPaths() {
        val vaultDir = tempFolder.newFolder(".vault")
        val missingPath = File(tempFolder.root, "does_not_exist").absolutePath

        val missingResult = PathJumpResolver.resolve(missingPath, tempFolder.root.absolutePath)
        assertTrue(missingResult is PathResolutionResult.Invalid)

        val vaultResult = PathJumpResolver.resolve(vaultDir.absolutePath, tempFolder.root.absolutePath)
        assertTrue(vaultResult is PathResolutionResult.Invalid)
        assertTrue((vaultResult as PathResolutionResult.Invalid).reason.contains("Vault", ignoreCase = true))
    }

    @Test
    fun looksLikeExplicitPath_distinguishesPlainKeywordsFromPaths() {
        assertFalse(PathJumpResolver.looksLikeExplicitPath("annual_report.pdf"))
        assertFalse(PathJumpResolver.looksLikeExplicitPath("music"))
        assertTrue(PathJumpResolver.looksLikeExplicitPath("/storage/emulated/0/Download"))
        assertTrue(PathJumpResolver.looksLikeExplicitPath("file:///storage/emulated/0/Download"))
    }
}
