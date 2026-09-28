package com.Hrshd1eux.filemanager.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural isolation audit test verifying that the offline core
 * remains 100% quarantined from network code, sockets, and remote protocol implementations.
 *
 * MANDATE:
 * "Network code is isolated from the offline core."
 */
class NetworkIsolationAuditTest {

    private val projectRoot: File by lazy {
        var current = File(System.getProperty("user.dir") ?: ".")
        if (current.name == "app") {
            current = current.parentFile ?: current
        }
        current
    }

    private val corePackageDir: File by lazy {
        File(projectRoot, "app/src/main/java/com/Hrshd1eux/filemanager/core")
    }

    /**
     * Offline core packages that must NEVER depend on network packages or sockets.
     */
    private val offlineCorePackages = listOf(
        "storage",
        "operations",
        "archive",
        "crypto",
        "cleaner",
        "vault",
        "search",
        "recents",
        "favorites",
        "display"
    )

    @Test
    fun auditOfflineCore_zeroNetworkImports() {
        assertTrue("Core package directory must exist", corePackageDir.exists())

        val forbiddenNetworkPackages = listOf(
            "com.Hrshd1eux.filemanager.core.network",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.HttpURLConnection",
            "okhttp3",
            "retrofit2",
            "io.ktor.client"
        )

        for (pkgName in offlineCorePackages) {
            val pkgDir = File(corePackageDir, pkgName)
            if (!pkgDir.exists()) continue

            val kotlinFiles = pkgDir.walkTopDown().filter { it.extension == "kt" }.toList()
            assertTrue("Offline package '$pkgName' must contain source files", kotlinFiles.isNotEmpty())

            for (file in kotlinFiles) {
                val content = file.readText()
                for (forbidden in forbiddenNetworkPackages) {
                    val hasForbiddenImport = content.lines().any { line ->
                        line.trim().startsWith("import ") && line.contains(forbidden)
                    }
                    assertFalse(
                        "Offline core file '${file.name}' in package '$pkgName' must NOT import network code: '$forbidden'. " +
                                "Network code must remain strictly isolated from offline storage!",
                        hasForbiddenImport
                    )
                }
            }
        }
    }

    @Test
    fun auditNetworkSubsystem_livesExclusivelyInNetworkPackage() {
        val networkDir = File(corePackageDir, "network")
        assertTrue("core/network directory must exist", networkDir.exists())

        val networkFiles = networkDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("core/network must contain implementation files", networkFiles.isNotEmpty())

        // Verify all network files are in com.Hrshd1eux.filemanager.core.network namespace
        for (file in networkFiles) {
            val content = file.readText()
            assertTrue(
                "Network file '${file.name}' must belong to core.network package hierarchy",
                content.lines().any { it.trim().startsWith("package com.Hrshd1eux.filemanager.core.network") }
            )
        }
    }
}
