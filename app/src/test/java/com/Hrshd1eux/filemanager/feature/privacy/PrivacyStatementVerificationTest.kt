package com.Hrshd1eux.filemanager.feature.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rigorous tests for Version 1.2.34 — Privacy Statement.
 *
 * Verifies that:
 * 1. The Privacy Statement screen contains all required disclosures:
 *    - Local data processing (file metadata, vault data, preferences, credentials)
 *    - Permissions requested and justification (All Files Access, Notifications, Foreground Services)
 *    - Network features behavior (user-initiated only, direct point-to-point, zero relays, protocol encryption)
 *    - Clear statement on zero transmission to external services
 *    - Accurate disclosure of zero analytics SDKs and zero automated crash reporting
 *    - Third-party library disclosures (Commons Compress, XZ, Junrar, Palantir ISO, AndroidX, Kotlin)
 *    - Privacy contact and inquiries
 * 2. The zero analytics / zero telemetry claims are strictly verified against actual dependencies and manifest.
 * 3. The screen is accessible from AboutScreen, SettingsScreen, HomeScreen, and FileBrowserScreen first-run permission view.
 */
class PrivacyStatementVerificationTest {

    private fun getSourceFile(relativePath: String): File {
        return File(relativePath).takeIf { it.exists() }
            ?: File("app/$relativePath").takeIf { it.exists() }
            ?: File("../$relativePath").takeIf { it.exists() }
            ?: throw IllegalStateException("Source file $relativePath could not be located.")
    }

    @Test
    fun verifyDependencies_zeroAnalyticsOrTelemetrySDKs() {
        val buildGradle = getSourceFile("build.gradle.kts").readText()
        val versionCatalog = getSourceFile("../gradle/libs.versions.toml").takeIf { it.exists() }
            ?: getSourceFile("gradle/libs.versions.toml")
        val catalogContent = versionCatalog.readText()

        val forbiddenTrackers = listOf(
            "firebase",
            "crashlytics",
            "google-analytics",
            "google.analytics",
            "sentry",
            "mixpanel",
            "segment",
            "adjust",
            "appsflyer",
            "datadog",
            "amplitude",
            "flurry",
            "bugsnag"
        )

        for (tracker in forbiddenTrackers) {
            assertFalse(
                "build.gradle.kts must not contain tracker: $tracker",
                buildGradle.contains(tracker, ignoreCase = true)
            )
            assertFalse(
                "libs.versions.toml must not contain tracker: $tracker",
                catalogContent.contains(tracker, ignoreCase = true)
            )
        }
    }

    @Test
    fun verifyManifest_zeroNetworkPermissionsAndHardenedFlags() {
        val manifest = getSourceFile("src/main/AndroidManifest.xml").readText()

        assertFalse(
            "AndroidManifest must not declare positive uses-permission for INTERNET",
            manifest.contains("<uses-permission android:name=\"android.permission.INTERNET\" />")
        )
        assertTrue(
            "ACCESS_NETWORK_STATE must be strictly removed",
            manifest.contains("<uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" tools:node=\"remove\" />")
        )
        assertTrue(
            "android:allowBackup must be false to avoid cloud leakage",
            manifest.contains("android:allowBackup=\"false\"")
        )
        assertTrue(
            "android:usesCleartextTraffic must be false",
            manifest.contains("android:usesCleartextTraffic=\"false\"")
        )
    }

    @Test
    fun privacyStatementScreen_containsAllRequiredDisclosures() {
        val privacyFile = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/feature/privacy/PrivacyStatementScreen.kt")
        val content = privacyFile.readText()

        // 1. Local Data Processing
        assertTrue("Must disclose local data processing", content.contains("Data Processed Locally on Your Device"))
        assertTrue("Must disclose file metadata processing", content.contains("File & Storage Metadata"))
        assertTrue("Must disclose private vault encryption", content.contains("Private Vault Content"))
        assertTrue("Must disclose user preferences", content.contains("User Preferences & Settings"))
        assertTrue("Must disclose remote credentials storage", content.contains("Remote Server Credentials"))

        // 2. Permissions Disclosure
        assertTrue("Must disclose permissions requested", content.contains("Permissions Requested & Why"))
        assertTrue("Must explain All Files Access", content.contains("All Files Access (MANAGE_EXTERNAL_STORAGE)"))
        assertTrue("Must explain Notifications", content.contains("Notifications (POST_NOTIFICATIONS)"))
        assertTrue("Must explain Foreground Service", content.contains("Foreground Service (DATA_SYNC)"))
        assertTrue("Must disclose excluded permissions", content.contains("Explicitly Excluded Permissions"))

        // 3. Network Features
        assertTrue("Must explain network features", content.contains("What Happens When You Use Network Features"))
        assertTrue("Must explain user-initiated only", content.contains("100% User-Initiated"))
        assertTrue("Must explain direct point-to-point", content.contains("Direct Point-to-Point Communication"))
        assertTrue("Must explain protocol encryption", content.contains("Protocol Encryption Standards"))

        // 4. Zero External Transmission
        assertTrue("Must state zero external transmission", content.contains("Zero External Services & No Data Transmission"))
        assertTrue("Must state no transmission to developers or partners", content.contains("No Transmission to External Services"))
        assertTrue("Must state no advertising or fingerprinting", content.contains("No Advertising or Fingerprinting"))
        assertTrue("Must state cloud backup prevention", content.contains("Cloud Backup Leakage Prevention"))

        // 5. Accurate Crash Reporting & Analytics Disclosure
        assertTrue("Must disclose crash reporting and analytics", content.contains("Crash Reporting & Analytics Disclosure"))
        assertTrue("Must accurately disclose zero analytics SDKs", content.contains("Zero Analytics SDKs"))
        assertTrue("Must accurately disclose zero automated crash reporting", content.contains("Zero Automated Crash Reporting"))
        assertTrue("Must disclose local system logs only", content.contains("Local System Logs Only"))

        // 6. Third-Party Library Disclosures
        assertTrue("Must disclose third-party libraries", content.contains("Third-Party Library Disclosures"))
        assertTrue("Must disclose Apache Commons Compress", content.contains("Apache Commons Compress"))
        assertTrue("Must disclose Tukaani XZ for Java", content.contains("Tukaani XZ for Java"))
        assertTrue("Must disclose Junrar", content.contains("Junrar"))
        assertTrue("Must disclose Palantir ISO File Reader", content.contains("Palantir ISO File Reader"))

        // 7. Contact & Privacy Inquiries
        assertTrue("Must disclose contact information", content.contains("Contact & Privacy Inquiries"))
        assertTrue("Must provide issue tracker", content.contains("github.com/HrshD1eux/filemanager/issues"))
        assertTrue("Must provide contact email", content.contains("HrshD1eux@proton.me"))
    }

    @Test
    fun privacyStatement_accessibleFromAllKeyEntryPoints() {
        // AboutScreen
        val aboutContent = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/feature/about/AboutScreen.kt").readText()
        assertTrue("AboutScreen must contain button to read privacy statement",
            aboutContent.contains("Read Detailed Privacy Statement"))
        assertTrue("AboutScreen must trigger onViewPrivacyStatement",
            aboutContent.contains("onClick = onViewPrivacyStatement"))

        // SettingsScreen
        val settingsContent = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/feature/settings/SettingsScreen.kt").readText()
        assertTrue("SettingsScreen must contain Privacy Statement row",
            settingsContent.contains("title = \"Privacy Statement\""))
        assertTrue("SettingsScreen Privacy row must trigger onNavigateToPrivacy",
            settingsContent.contains("onClick = onNavigateToPrivacy"))

        // HomeScreen
        val homeContent = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/feature/home/HomeScreen.kt").readText()
        assertTrue("HomeScreen banner must trigger onNavigateToPrivacy",
            homeContent.contains("clickable(onClick = onNavigateToPrivacy)"))

        // FileBrowserScreen (First-run permission prompt)
        val browserContent = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/feature/browser/FileBrowserScreen.kt").readText()
        assertTrue("FileBrowserScreen permission view must contain Read Privacy Statement button",
            browserContent.contains("Text(\"Read Privacy Statement\""))

        // ArcaNavHost
        val navContent = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/core/navigation/ArcaNavHost.kt").readText()
        assertTrue("ArcaNavHost must define Screen.PrivacyStatement destination",
            navContent.contains("composable<Screen.PrivacyStatement>"))
        assertTrue("AboutScreen in NavHost must pass onNavigateToPrivacy",
            navContent.contains("onNavigateToPrivacy = { navController.navigate(Screen.PrivacyStatement) }"))
    }
}
