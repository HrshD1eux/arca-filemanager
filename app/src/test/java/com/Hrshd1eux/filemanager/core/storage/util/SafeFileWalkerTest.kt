package com.Hrshd1eux.filemanager.core.storage.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SafeFileWalkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun isRestrictedDirectory_detectsAndroidDataAndObb() {
        val root = tempFolder.newFolder("emulated_0")
        val androidDir = File(root, "Android").apply { mkdir() }
        val dataDir = File(androidDir, "data").apply { mkdir() }
        val obbDir = File(androidDir, "obb").apply { mkdir() }
        val normalDir = File(root, "Documents").apply { mkdir() }

        assertTrue(SafeFileWalker.isRestrictedDirectory(dataDir))
        assertTrue(SafeFileWalker.isRestrictedDirectory(obbDir))
        assertFalse(SafeFileWalker.isRestrictedDirectory(normalDir))
        assertFalse(SafeFileWalker.isRestrictedDirectory(androidDir))
    }

    @Test
    fun walkSafe_skipsRestrictedAndHiddenDirectories() {
        val root = tempFolder.newFolder("storage_root")
        val androidDir = File(root, "Android").apply { mkdir() }
        val dataDir = File(androidDir, "data").apply { mkdir() }
        File(dataDir, "hidden_data.txt").writeText("secret")

        val hiddenDir = File(root, ".hidden_folder").apply { mkdir() }
        File(hiddenDir, "file_in_hidden.txt").writeText("hidden")

        val docDir = File(root, "Documents").apply { mkdir() }
        val docFile = File(docDir, "resume.pdf").apply { writeText("resume content") }

        val restrictedEncountered = mutableListOf<String>()
        val walked = SafeFileWalker.walkSafe(
            root = root,
            includeHidden = false,
            onRestrictedDir = { restrictedEncountered.add(it.name) }
        ).toList()

        // Should include Documents and resume.pdf, but not dataDir's children or hiddenDir
        assertTrue(walked.any { it.name == "resume.pdf" })
        assertFalse(walked.any { it.name == "hidden_data.txt" })
        assertFalse(walked.any { it.name == "file_in_hidden.txt" })
        assertTrue(restrictedEncountered.contains("data"))
    }
}
