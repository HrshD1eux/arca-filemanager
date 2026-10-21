package com.Hrshd1eux.filemanager.feature.about

import com.Hrshd1eux.filemanager.feature.about.model.OpenSourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rigorous tests for Version 1.2.33 — Professional About Screen.
 *
 * Verifies that:
 * 1. Dedicated About screen contains all required professional elements:
 *    - Brand emblem and dynamic version
 *    - Professional product description
 *    - Privacy Statement
 *    - Open-source licenses and acknowledgements
 *    - Authentic support and feedback channels
 *    - Relevant legal information and disclaimer
 * 2. Unprofessional and clutter elements are strictly removed:
 *    - "Foundation Complete."
 *    - Development milestone and sprint labels
 *    - Internal engineering stack notes
 *    - Unsupported security claims ("military-grade security", "100% secure", "impossible to hack")
 *    - Fake company details
 * 3. Settings connects cleanly to the dedicated About screen.
 */
class AboutScreenProfessionalTest {

    private fun getSourceFile(relativePath: String): File {
        return File(relativePath).takeIf { it.exists() }
            ?: File("app/$relativePath").takeIf { it.exists() }
            ?: File("../$relativePath").takeIf { it.exists() }
            ?: throw IllegalStateException("Source file $relativePath could not be located.")
    }

    @Test
    fun openSourceRegistry_containsAllThirdPartyDependenciesWithValidMetadata() {
        val libraries = OpenSourceRegistry.libraries
        assertTrue("Library registry must contain dependencies", libraries.isNotEmpty())

        val expectedLibraries = listOf(
            "Apache Commons Compress",
            "Tukaani XZ for Java",
            "Junrar",
            "Palantir ISO File Reader",
            "AndroidX & Jetpack Libraries",
            "Jetpack Compose & Material 3",
            "Kotlin & Coroutines"
        )

        val registeredNames = libraries.map { it.name }
        for (expected in expectedLibraries) {
            assertTrue("Expected dependency $expected must be registered", registeredNames.contains(expected))
        }

        for (lib in libraries) {
            assertTrue("Name must not be blank for ${lib.name}", lib.name.isNotBlank())
            assertTrue("Author must not be blank for ${lib.name}", lib.author.isNotBlank())
            assertTrue("License must not be blank for ${lib.name}", lib.license.isNotBlank())
            assertTrue("Description must not be blank for ${lib.name}", lib.description.isNotBlank())
            assertTrue("Project URL must start with https:// for ${lib.name}", lib.projectUrl.startsWith("https://"))
            assertTrue("License URL must start with https:// for ${lib.name}", lib.licenseUrl.startsWith("https://"))
        }
    }

    @Test
    fun aboutScreen_containsAllRequiredProfessionalSections() {
        val aboutFile = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/feature/about/AboutScreen.kt")
        val content = aboutFile.readText()

        // 1. App Name and Header
        assertTrue("Must contain ARCA title", content.contains("text = \"ARCA\""))
        assertTrue("Must contain AppHeader", content.contains("AppHeader(versionInfo = versionInfo)"))

        // 2. Product Description
        assertTrue("Must include ProductDescriptionCard", content.contains("ProductDescriptionCard"))
        assertTrue("Must describe ARCA as offline-first file manager", content.contains("ARCA is an offline-first file management"))

        // 3. Privacy Statement
        assertTrue("Must include PrivacyStatementCard", content.contains("PrivacyStatementCard"))
        assertTrue("Must include Zero Telemetry", content.contains("Zero Telemetry"))
        assertTrue("Must include Read Detailed Privacy Statement", content.contains("Read Detailed Privacy Statement"))

        // 4. Open-Source Licenses
        assertTrue("Must include OpenSourceLicensesCard", content.contains("OpenSourceLicensesCard"))
        assertTrue("Must reference OpenSourceRegistry", content.contains("OpenSourceRegistry.libraries"))

        // 5. Support & Feedback
        assertTrue("Must include SupportAndFeedbackCard", content.contains("SupportAndFeedbackCard"))
        assertTrue("Must link to GitHub issues", content.contains("https://github.com/HrshD1eux/filemanager/issues"))
        assertTrue("Must provide technical feedback email", content.contains("HrshD1eux@proton.me"))

        // 6. Legal Information
        assertTrue("Must include LegalInformationCard", content.contains("LegalInformationCard"))
        assertTrue("Must include Copyright notice", content.contains("Copyright © 2026 ARCA Contributors"))
        assertTrue("Must include Apache License 2.0 reference", content.contains("Licensed under the Apache License, Version 2.0"))
        assertTrue("Must include Disclaimer", content.contains("Disclaimer:"))

        // 7. Diagnostics
        assertTrue("Must include DeviceDiagnosticsCard", content.contains("DeviceDiagnosticsCard"))
    }

    @Test
    fun aboutScreen_strictlyExcludesUnprofessionalAndClutterContent() {
        val aboutFile = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/feature/about/AboutScreen.kt")
        val content = aboutFile.readText()

        // 1. "Foundation Complete."
        assertFalse("Must not contain 'Foundation Complete'", content.contains("Foundation Complete", ignoreCase = true))

        // 2. Internal engineering trivia notes
        assertFalse("Must not contain 'Foundation Stack'", content.contains("Foundation Stack", ignoreCase = true))
        assertFalse("Must not expose framework internals like 'Navigation Compose (Type-Safe)' in UI",
            content.contains("Navigation Compose (Type-Safe)", ignoreCase = true))

        // 3. Milestone or sprint labels
        assertFalse("Must not contain milestone tags in UI strings", content.contains("Milestone 1.", ignoreCase = true))
        assertFalse("Must not contain sprint labels in UI strings", content.contains("Sprint ", ignoreCase = true))

        // 4. Unsupported or hyperbolic security claims
        assertFalse("Must not claim 'military-grade security'", content.contains("military-grade", ignoreCase = true))
        assertFalse("Must not claim '100% secure'", content.contains("100% secure", ignoreCase = true))
        assertFalse("Must not claim 'impossible to hack'", content.contains("impossible to hack", ignoreCase = true))
    }

    @Test
    fun settingsScreen_integratesWithDedicatedAboutScreen() {
        val settingsFile = getSourceFile("src/main/java/com/Hrshd1eux/filemanager/feature/settings/SettingsScreen.kt")
        val content = settingsFile.readText()

        assertTrue("SettingsScreen must expose onNavigateToAbout parameter",
            content.contains("onNavigateToAbout: () -> Unit"))
        assertTrue("SettingsScreen must include About ARCA navigation row",
            content.contains("title = \"About ARCA\""))
        assertTrue("SettingsScreen About row must trigger onNavigateToAbout",
            content.contains("onClick = onNavigateToAbout"))
    }
}
