package com.Hrshd1eux.filemanager.core.storage.apk

import com.Hrshd1eux.filemanager.core.actions.QuickActionContext
import com.Hrshd1eux.filemanager.core.actions.QuickActionType
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInstallerTest {

    @Test
    fun mimeType_isStandardAndroidPackageArchive() {
        assertEquals("application/vnd.android.package-archive", ApkInstaller.MIME_TYPE_APK)
    }

    @Test
    fun determineStatus_whenNotInstalled_returnsNewApp() {
        val status = ApkInstaller.determineStatus(apkVersionCode = 100L, installedVersionCode = null)
        assertEquals(ApkInstallStatus.NEW_APP, status)
    }

    @Test
    fun determineStatus_whenApkVersionCodeHigher_returnsUpdate() {
        val status = ApkInstaller.determineStatus(apkVersionCode = 200L, installedVersionCode = 100L)
        assertEquals(ApkInstallStatus.UPDATE, status)
    }

    @Test
    fun determineStatus_whenApkVersionCodeSame_returnsSameVersion() {
        val status = ApkInstaller.determineStatus(apkVersionCode = 200L, installedVersionCode = 200L)
        assertEquals(ApkInstallStatus.SAME_VERSION, status)
    }

    @Test
    fun determineStatus_whenApkVersionCodeLower_returnsDowngrade() {
        val status = ApkInstaller.determineStatus(apkVersionCode = 150L, installedVersionCode = 300L)
        assertEquals(ApkInstallStatus.DOWNGRADE, status)
    }

    @Test
    fun quickAction_forApkFile_includesInstallActionFirst() {
        val apkNode = StorageNode(
            id = "test-apk-id",
            path = "/storage/emulated/0/Download/signal.apk",
            name = "signal.apk",
            size = 45000000L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false
        )

        val actions = QuickActionContext.FILE.getActions(apkNode)
        assertTrue("Quick actions for APK must contain INSTALL action", actions.contains(QuickActionType.INSTALL))
        assertEquals("INSTALL must be the first quick action for APK files", QuickActionType.INSTALL, actions.first())
    }

    @Test
    fun quickAction_forNonApkFile_doesNotIncludeInstallAction() {
        val docNode = StorageNode(
            id = "test-doc-id",
            path = "/storage/emulated/0/Download/document.pdf",
            name = "document.pdf",
            size = 1200000L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false
        )

        val actions = QuickActionContext.FILE.getActions(docNode)
        assertFalse("Quick actions for non-APK files must NOT contain INSTALL action", actions.contains(QuickActionType.INSTALL))
    }

    @Test
    fun isSplitApkArchive_correctlyIdentifiesContainerFormats() {
        assertTrue(ApkInstaller.isSplitApkArchive(java.io.File("app.xapk")))
        assertTrue(ApkInstaller.isSplitApkArchive(java.io.File("app.apks")))
        assertTrue(ApkInstaller.isSplitApkArchive(java.io.File("app.apkm")))
        assertTrue(ApkInstaller.isSplitApkArchive(java.io.File("/path/to/archive.XAPK")))

        assertFalse(ApkInstaller.isSplitApkArchive(java.io.File("app.apk")))
        assertFalse(ApkInstaller.isSplitApkArchive(java.io.File("app.zip")))
        assertFalse(ApkInstaller.isSplitApkArchive(java.io.File("document.pdf")))
    }

    @Test
    fun extractSplitApk_extractsOnlyApksSafely() {
        val tempDir = java.nio.file.Files.createTempDirectory("split_apk_test").toFile()
        val archiveFile = java.io.File(tempDir, "test.xapk")

        // Create a mock zip containing base.apk, split.apk, and manifest.json
        java.util.zip.ZipOutputStream(archiveFile.outputStream().buffered()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("base.apk"))
            zos.write("base_content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("split_config.arm64_v8a.apk"))
            zos.write("split_content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        val extractOut = java.io.File(tempDir, "extracted")
        val extracted = ApkInstaller.extractSplitApk(archiveFile, extractOut)

        assertEquals(2, extracted.size)
        assertTrue(extracted.any { it.name == "base.apk" })
        assertTrue(extracted.any { it.name == "split_config.arm64_v8a.apk" })
        assertFalse(extracted.any { it.name == "manifest.json" })

        tempDir.deleteRecursively()
    }

    @Test
    fun installSilentlyViaRoot_whenEmptyList_failsWithIllegalArgumentException() {
        val res = ApkInstaller.installSilentlyViaRoot(emptyList())
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is IllegalArgumentException || res.exceptionOrNull() is IllegalStateException)
    }
}
