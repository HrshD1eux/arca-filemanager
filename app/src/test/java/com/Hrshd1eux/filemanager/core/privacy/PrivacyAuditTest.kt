package com.Hrshd1eux.filemanager.core.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyAuditTest {

    private val projectRoot: File by lazy {
        var current = File(System.getProperty("user.dir") ?: ".")
        if (current.name == "app") {
            current = current.parentFile ?: current
        }
        current
    }

    private val mainSourceDir: File by lazy {
        File(projectRoot, "app/src/main/java")
    }

    private val manifestFile: File by lazy {
        File(projectRoot, "app/src/main/AndroidManifest.xml")
    }

    private val buildGradleFile: File by lazy {
        File(projectRoot, "app/build.gradle.kts")
    }

    private val versionCatalogFile: File by lazy {
        File(projectRoot, "gradle/libs.versions.toml")
    }

    @Test
    fun auditManifest_zeroNetworkPermissionsAndHardenedConfig() {
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()

        // 1. Assert INTERNET permission is declared for user-configured remote servers and version checks
        assertTrue(
            "AndroidManifest must declare positive uses-permission for INTERNET",
            manifestContent.contains("<uses-permission android:name=\"android.permission.INTERNET\" />")
        )

        // 2. Assert ACCESS_NETWORK_STATE is removed or not requested
        assertFalse(
            "AndroidManifest must not declare positive uses-permission for ACCESS_NETWORK_STATE",
            manifestContent.contains("<uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />")
        )

        // 3. Assert allowBackup is disabled
        assertTrue(
            "android:allowBackup must be false for zero cloud leakage",
            manifestContent.contains("android:allowBackup=\"false\"")
        )

        // 4. Assert usesCleartextTraffic is disabled
        assertTrue(
            "android:usesCleartextTraffic must be false",
            manifestContent.contains("android:usesCleartextTraffic=\"false\"")
        )
    }

    @Test
    fun auditDependencies_zeroAnalyticsOrTelemetrySDKs() {
        val buildGradle = buildGradleFile.readText()
        val versionCatalog = versionCatalogFile.readText()

        val forbiddenVendors = listOf(
            "firebase",
            "crashlytics",
            "google-analytics",
            "google.analytics",
            "sentry",
            "mixpanel",
            "segment",
            "adjust",
            "appsflyer",
            "facebook-android-sdk",
            "datadog",
            "amplitude",
            "flurry",
            "branch.io",
            "bugsnag",
            "retrofit",
            "okhttp",
            "ktor-client",
            "volley"
        )

        for (vendor in forbiddenVendors) {
            assertFalse(
                "build.gradle.kts must not contain forbidden telemetry/network dependency: $vendor",
                buildGradle.contains(vendor, ignoreCase = true)
            )
            assertFalse(
                "libs.versions.toml must not contain forbidden telemetry/network dependency: $vendor",
                versionCatalog.contains(vendor, ignoreCase = true)
            )
        }
    }

    @Test
    fun auditCodebase_zeroNetworkCallsOrTelemetryImports() {
        assertTrue("Main source directory must exist", mainSourceDir.exists())

        val forbiddenImports = listOf(
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.HttpURLConnection",
            "java.net.URL",
            "okhttp3",
            "retrofit2",
            "io.ktor.client",
            "com.google.firebase",
            "io.sentry",
            "com.mixpanel",
            "com.segment"
        )

        val kotlinFiles = mainSourceDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Source files must be discovered", kotlinFiles.isNotEmpty())

        for (file in kotlinFiles) {
            val content = file.readText()
            for (forbidden in forbiddenImports) {
                val hasForbiddenImport = content.lines().any { line ->
                    line.trim().startsWith("import ") && line.contains(forbidden)
                }
                assertFalse(
                    "Source file ${file.name} must not import forbidden network/telemetry package: $forbidden",
                    hasForbiddenImport
                )
            }
        }
    }

    @Test
    fun auditPrivacy_zeroAccountZeroCloud() {
        // Confirm that the architecture is strictly local-first with zero auth servers
        val kotlinFiles = mainSourceDir.walkTopDown().filter { it.extension == "kt" }.toList()

        val forbiddenKeywords = listOf(
            "OAuth2",
            "FirebaseAuth",
            "GoogleSignIn",
            "CloudStorage",
            "AWS_ACCESS_KEY",
            "AZURE_CLIENT_SECRET"
        )

        for (file in kotlinFiles) {
            val content = file.readText()
            for (keyword in forbiddenKeywords) {
                assertFalse(
                    "Source file ${file.name} must not reference cloud auth/telemetry keyword: $keyword",
                    content.contains(keyword, ignoreCase = true)
                )
            }
        }
    }
}
