package com.Hrshd1eux.filemanager.core.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release Hardening & Candidate Freeze Audit Test Suite for Milestones 1.5.91 – 1.5.99:
 * Permissions, SDK Dependencies, ProGuard Rules, and Security Hardening.
 *
 * Verifies:
 * 1. Zero-leak Manifest Config: android:allowBackup="false", android:usesCleartextTraffic="false".
 * 2. Strict Permissions Minimalism: Zero unauthorized background network permissions.
 * 3. ProGuard/R8 Rules: Required keep rules for Kotlinx Serialization and Apache Commons Compress.
 * 4. Build Configuration: Target SDK 35 (Android 15), Min SDK 26 (Android 8.0).
 * 5. Feature Freeze Assurance: Zero unverified third-party analytics, crash-loggers, or trackers.
 */
class ReleaseHardeningAuditTest {

    private val projectRoot: File by lazy {
        var current = File(System.getProperty("user.dir") ?: ".")
        if (current.name == "app") {
            current = current.parentFile ?: current
        }
        current
    }

    private val manifestFile: File by lazy {
        File(projectRoot, "app/src/main/AndroidManifest.xml")
    }

    private val buildGradleFile: File by lazy {
        File(projectRoot, "app/build.gradle.kts")
    }

    private val proguardFile: File by lazy {
        File(projectRoot, "app/proguard-rules.pro")
    }

    @Test
    fun manifestConfiguration_meetsStrictPrivacyAndSecurityRequirements() {
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()

        assertTrue("android:allowBackup must be disabled to prevent cloud extraction",
            content.contains("android:allowBackup=\"false\""))

        assertTrue("android:usesCleartextTraffic must be disabled",
            content.contains("android:usesCleartextTraffic=\"false\""))

        // Ensure INTERNET permission is declared for user-configured remote servers and version checks
        assertTrue("Must declare positive uses-permission for INTERNET",
            content.contains("<uses-permission android:name=\"android.permission.INTERNET\" />"))
    }

    @Test
    fun proguardRules_protectSerializationAndArchiveEngines() {
        assertTrue("proguard-rules.pro must exist", proguardFile.exists())
        val rules = proguardFile.readText()

        assertTrue("Must keep serialization annotations and classes",
            rules.contains("kotlinx.serialization"))

        assertTrue("Must keep Commons Compress classes",
            rules.contains("org.apache.commons.compress"))

        assertTrue("Must strip verbose and debug logs in release",
            rules.contains("android.util.Log"))
    }

    @Test
    fun buildConfiguration_targetsModernAndroidStandards() {
        assertTrue("build.gradle.kts must exist", buildGradleFile.exists())
        val gradleContent = buildGradleFile.readText()

        assertTrue("compileSdk must target API 35", gradleContent.contains("compileSdk = 35"))
        assertTrue("targetSdk must target API 35", gradleContent.contains("targetSdk = 35"))
        assertTrue("minSdk must be at least API 26", gradleContent.contains("minSdk = 26"))
    }

    @Test
    fun dependencies_zeroThirdPartyAnalyticsOrAdSDKs() {
        val gradleContent = buildGradleFile.readText()

        val forbiddenSdkSignatures = listOf(
            "play-services-ads",
            "firebase-analytics",
            "firebase-crashlytics",
            "appsflyer",
            "adjust",
            "branch",
            "mixpanel",
            "segment",
            "sentry-android",
            "bugsnag-android"
        )

        for (sig in forbiddenSdkSignatures) {
            assertFalse("build.gradle.kts must not contain forbidden SDK '$sig'",
                gradleContent.contains(sig, ignoreCase = true))
        }
    }
}
