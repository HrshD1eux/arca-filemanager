package com.Hrshd1eux.filemanager.core.archive

import com.Hrshd1eux.filemanager.core.archive.engine.SevenZEngine
import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.operations.model.ConflictResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EncryptedArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sevenZEngine: SevenZEngine
    private lateinit var cryptoEngine: CryptoEngine

    @Before
    fun setUp() {
        sevenZEngine = SevenZEngine(Dispatchers.Unconfined)
        cryptoEngine = CryptoEngine(ioDispatcher = Dispatchers.Unconfined)
    }

    @Test
    fun create7z_withPassword_encryptsAndExtractsWithCorrectPassword() = runTest {
        val workDir = tempFolder.newFolder("enc_source")
        val secretFile = File(workDir, "confidential.txt").apply {
            writeText("Top Secret ARCA Data - Private and Offline")
        }
        val archive7z = File(tempFolder.root, "encrypted.7z")
        val extractDir = tempFolder.newFolder("enc_dest")

        val password = "StrongPassword7z!".toCharArray()

        // 1. Create Encrypted 7z Archive
        val createResult = sevenZEngine.create7z(
            sources = listOf(secretFile),
            destination7z = archive7z,
            method = SevenZMethod.LZMA2,
            password = password
        )
        assertTrue(createResult.isSuccess)
        assertTrue(archive7z.exists() && archive7z.length() > 0)

        // 2. Test listing with password
        val entries = sevenZEngine.listEntries(archive7z, password)
        assertEquals(1, entries.size)
        assertEquals("confidential.txt", entries.first().name)

        // 3. Extract with correct password
        val extractResult = sevenZEngine.extractAll(
            sevenZFile = archive7z,
            destinationDir = extractDir,
            conflictResolution = ConflictResolution.OVERWRITE,
            password = password,
            verifyIntegrity = true
        )
        assertTrue(extractResult.isSuccess)
        assertEquals(1, extractResult.getOrNull())

        val extractedFile = File(extractDir, "confidential.txt")
        assertTrue(extractedFile.exists())
        assertEquals("Top Secret ARCA Data - Private and Offline", extractedFile.readText())

        // 4. Test integrity report with password
        val testReport = sevenZEngine.testArchive(archive7z, password)
        assertTrue(testReport.isSuccess)
        assertTrue(testReport.getOrNull()!!.isPassed)
    }

    @Test
    fun extract7z_withWrongPassword_failsExtraction() = runTest {
        val workDir = tempFolder.newFolder("enc_wrong_source")
        val secretFile = File(workDir, "data.txt").apply {
            writeText("Secret Content")
        }
        val archive7z = File(tempFolder.root, "secure.7z")
        val extractDir = tempFolder.newFolder("enc_wrong_dest")

        val correctPassword = "CorrectPass123".toCharArray()
        val wrongPassword = "WrongPassword999".toCharArray()

        val createResult = sevenZEngine.create7z(
            sources = listOf(secretFile),
            destination7z = archive7z,
            password = correctPassword
        )
        assertTrue(createResult.isSuccess)

        // Extract with wrong password must fail
        val extractResult = sevenZEngine.extractAll(
            sevenZFile = archive7z,
            destinationDir = extractDir,
            password = wrongPassword
        )
        assertFalse(extractResult.isSuccess)
    }

    @Test
    fun passwordZeroization_wipesMemoryBuffer() {
        val password = "EphemeralMasterPassword!".toCharArray()
        cryptoEngine.wipeSensitiveData(password)
        assertTrue(password.all { it == '\u0000' })
    }
}
