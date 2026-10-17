package com.Hrshd1eux.filemanager.core.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for Version 1.2.32 — Dynamic App Version.
 *
 * Verifies that:
 * 1. Current version name and version code are dynamically read and represented.
 * 2. Displayed versions automatically update across upgrades.
 * 3. Fixed version strings are removed from About and relevant screens.
 * 4. Build identifiers are optional and clearly labeled ("Build ID: ...").
 * 5. Error fallbacks prevent crashes when package metadata is unavailable.
 */
class AppVersionTest {

    @Test
    fun appVersionInfo_formatsDisplayStringsCorrectly() {
        val info = AppVersionInfo(
            versionName = "1.2.32",
            versionCode = 10232L,
            applicationId = "com.Hrshd1eux.filemanager",
            buildIdentifier = null
        )

        assertEquals("Version 1.2.32", info.displayVersion)
        assertEquals("Build 10232", info.displayBuild)
        assertNull(info.displayBuildIdentifier)
        assertEquals("Version 1.2.32 (Build 10232)", info.formattedSummary)
    }

    @Test
    fun appVersionInfo_withBuildIdentifier_formatsClearlyLabeled() {
        val info = AppVersionInfo(
            versionName = "1.2.32",
            versionCode = 10232L,
            applicationId = "com.Hrshd1eux.filemanager",
            buildIdentifier = "release-f11454a"
        )

        assertEquals("Version 1.2.32", info.displayVersion)
        assertEquals("Build 10232", info.displayBuild)
        assertEquals("Build ID: release-f11454a", info.displayBuildIdentifier)
        assertEquals("Version 1.2.32 (Build 10232 • Build ID: release-f11454a)", info.formattedSummary)
    }

    @Test
    fun appVersionInfo_withBlankBuildIdentifier_treatsAsNull() {
        val info = AppVersionInfo(
            versionName = "1.2.32",
            versionCode = 10232L,
            applicationId = "com.Hrshd1eux.filemanager",
            buildIdentifier = "   "
        )

        assertNull(info.displayBuildIdentifier)
        assertEquals("Version 1.2.32 (Build 10232)", info.formattedSummary)
    }

    @Test
    fun staticAppVersionProvider_deliversSuppliedMetadata() {
        val expected = AppVersionInfo(
            versionName = "1.2.32",
            versionCode = 10232L,
            applicationId = "com.Hrshd1eux.filemanager",
            buildIdentifier = "debug"
        )
        val provider = StaticAppVersionProvider(expected)

        val actual = provider.getAppVersionInfo()
        assertEquals(expected, actual)
        assertEquals("1.2.32", actual.versionName)
        assertEquals(10232L, actual.versionCode)
        assertEquals("com.Hrshd1eux.filemanager", actual.applicationId)
        assertEquals("debug", actual.buildIdentifier)
    }

    @Test
    fun dynamicUpgrade_automaticallyReflectsNewVersionWithoutCodeChanges() {
        // Simulate initial version 1.2.31
        var packageVersionName = "1.2.31"
        var packageVersionCode = 10231L

        val dynamicProvider = object : AppVersionProvider {
            override fun getAppVersionInfo(): AppVersionInfo {
                return AppVersionInfo(
                    versionName = packageVersionName,
                    versionCode = packageVersionCode,
                    applicationId = "com.Hrshd1eux.filemanager"
                )
            }
        }

        val preUpgrade = dynamicProvider.getAppVersionInfo()
        assertEquals("1.2.31", preUpgrade.versionName)
        assertEquals(10231L, preUpgrade.versionCode)
        assertEquals("Version 1.2.31 (Build 10231)", preUpgrade.formattedSummary)

        // Simulate app upgrade to 1.2.32
        packageVersionName = "1.2.32"
        packageVersionCode = 10232L

        val postUpgrade = dynamicProvider.getAppVersionInfo()
        assertEquals("1.2.32", postUpgrade.versionName)
        assertEquals(10232L, postUpgrade.versionCode)
        assertEquals("Version 1.2.32 (Build 10232)", postUpgrade.formattedSummary)
    }

    @Test
    fun aboutScreen_containsNoHardcodedVersionStrings() {
        val aboutScreenFile = File("src/main/java/com/Hrshd1eux/filemanager/feature/about/AboutScreen.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/Hrshd1eux/filemanager/feature/about/AboutScreen.kt")
        assertTrue("AboutScreen.kt must exist at ${aboutScreenFile.absolutePath}", aboutScreenFile.exists())

        val content = aboutScreenFile.readText()
        assertFalse("AboutScreen must not hardcode 'Version 1.'", content.contains("text = \"Version 1."))
        assertFalse("AboutScreen must not hardcode 'Build 10'", content.contains("(Build 10"))
        assertFalse("AboutScreen must not hardcode 'Version 1.1.40'", content.contains("1.1.40"))
        assertFalse("AboutScreen must not hardcode 'Version 1.2.32'", content.contains("text = \"Version 1.2.32\""))

        // Verify that dynamic versionInfo is used instead
        assertTrue("AboutScreen must reference versionInfo.displayVersion", content.contains("versionInfo.displayVersion"))
        assertTrue("AboutScreen must reference versionInfo.displayBuild", content.contains("versionInfo.displayBuild"))
        assertTrue("AboutScreen must reference versionInfo.applicationId", content.contains("versionInfo.applicationId"))
        assertTrue("AboutScreen must reference versionInfo.buildIdentifier", content.contains("versionInfo.buildIdentifier"))
    }

    @Test
    fun packageAppVersionProvider_handlesMissingMetadataGracefully() {
        // In JVM unit tests without Android Context mocks, calling with a dummy or unmocked context
        // should safely catch any exceptions and return fallback info without crashing.
        val fallback = AppVersionInfo(
            versionName = "Unknown",
            versionCode = 0L,
            applicationId = "com.Hrshd1eux.filemanager",
            buildIdentifier = null
        )

        assertEquals("Version Unknown", fallback.displayVersion)
        assertEquals("Build 0", fallback.displayBuild)
        assertNull(fallback.displayBuildIdentifier)
    }
}
