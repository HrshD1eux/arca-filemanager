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
}
