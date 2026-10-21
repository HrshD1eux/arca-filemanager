package com.Hrshd1eux.filemanager.core.performance

import com.Hrshd1eux.filemanager.core.checksum.ChecksumAlgorithm
import com.Hrshd1eux.filemanager.core.checksum.ChecksumEngine
import com.Hrshd1eux.filemanager.core.crypto.CryptoEngine
import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.util.NodeSorter
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.vault.model.VaultFileEntry
import com.Hrshd1eux.filemanager.core.vault.model.VaultManifest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Performance Benchmarks & Scale Stress Suite for Version 1.5.36 – 1.5.39.
 *
 * Evaluates:
 * 1. Cryptographic throughput (AES-256-GCM authenticated encryption).
 * 2. Checksum streaming throughput (SHA-256 over multi-megabyte payloads).
 * 3. Scalability of large Vault manifests (1,000+ files).
 * 4. High-density directory sorting & categorization latency.
 * 5. Memory efficiency: Chunk-based streaming verification.
 */
class PerformanceBenchmarkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var cryptoEngine: CryptoEngine
    private lateinit var checksumEngine: ChecksumEngine

    @Before
    fun setUp() {
        cryptoEngine = CryptoEngine()
        checksumEngine = ChecksumEngine()
    }

    // =========================================================================
    // 1.5.36 Cryptographic & Checksum Streaming Throughput
    // =========================================================================

    @Test
    fun checksumStreaming_processesMultiMegabytePayloadWithHighThroughput() = runBlocking {
        val payloadSize = 2 * 1024 * 1024 // 2MB
        val sampleData = ByteArray(payloadSize) { (it % 127).toByte() }
        val testFile = tempFolder.newFile("bench_checksum.bin").apply { writeBytes(sampleData) }

        val elapsedMs = measureTimeMillis {
            val item = checksumEngine.calculateAlgorithm(testFile, ChecksumAlgorithm.SHA_256).getOrThrow()
            assertTrue(item.hashHex.isNotEmpty())
        }

        // 2MB in typical JVM should process well within 2,500ms
        assertTrue("Checksum of 2MB must complete in < 2500ms, took ${elapsedMs}ms", elapsedMs < 2500)
    }

    @Test
    fun aeadCipherStreaming_processesMultiMegabytePayloadWithHighThroughput() = runBlocking {
        val payloadSize = 1024 * 1024 // 1MB
        val sampleData = ByteArray(payloadSize) { (it % 251).toByte() }

        val salt = ByteArray(16) { 0x42.toByte() }
        val key = cryptoEngine.deriveKey("BenchmarkPass2026!".toCharArray(), salt)

        val input = ByteArrayInputStream(sampleData)
        val output = ByteArrayOutputStream()

        val elapsedMs = measureTimeMillis {
            cryptoEngine.aead.encryptStream(input, output, key)
        }

        assertTrue("Encryption of 1MB must complete in < 2000ms, took ${elapsedMs}ms", elapsedMs < 2000)
        assertTrue(output.size() > payloadSize) // Ciphertext + IV + Auth tag
    }

    // =========================================================================
    // 1.5.37 Scalability: Large Manifest Serialization & Deserialization
    // =========================================================================

    @Test
    fun vaultManifest_scalesToThousandEntriesEfficiently() {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
        val entries = (1..1000).map { i ->
            VaultFileEntry(
                id = "vault_entry_$i",
                originalName = "financial_document_annual_report_2026_q${i % 4}_v$i.pdf",
                encryptedFileName = "enc_payload_chunk_${i}.bin",
                parentVirtualPath = "Finance/Year_${2020 + (i % 6)}",
                isDirectory = false,
                size = 1024L * (i % 100),
                addedDate = 1729000000000L + i * 1000L
            )
        }

        val manifest = VaultManifest(entries)

        var serializedString = ""
        val serializeTimeMs = measureTimeMillis {
            serializedString = json.encodeToString(VaultManifest.serializer(), manifest)
        }

        var deserializedManifest: VaultManifest? = null
        val deserializeTimeMs = measureTimeMillis {
            deserializedManifest = json.decodeFromString(VaultManifest.serializer(), serializedString)
        }

        assertEquals(1000, deserializedManifest?.files?.size)
        assertTrue("Serializing 1,000 vault entries must complete in < 500ms, took ${serializeTimeMs}ms", serializeTimeMs < 500)
        assertTrue("Deserializing 1,000 vault entries must complete in < 500ms, took ${deserializeTimeMs}ms", deserializeTimeMs < 500)
    }

    // =========================================================================
    // 1.5.38 Directory Sorting & Categorization Scale
    // =========================================================================

    @Test
    fun nodeSorter_sortsFiveHundredEntriesQuickly() {
        val nodes = (1..500).map { i ->
            StorageNode(
                id = "node_$i",
                path = "/storage/emulated/0/Download/file_$i.dat",
                name = "file_${(500 - i).toString().padStart(4, '0')}.dat",
                size = (i * 4096L),
                lastModified = 1729000000000L + (i * 60_000L),
                isDirectory = i % 10 == 0
            )
        }

        val elapsedMs = measureTimeMillis {
            val sorted = NodeSorter.sort(nodes, SortOrder(field = SortField.NAME, direction = SortDirection.ASCENDING))
            assertEquals(500, sorted.size)
            // Directories should appear first
            assertTrue(sorted.first().isDirectory)
        }

        assertTrue("Sorting 500 nodes must complete in < 200ms, took ${elapsedMs}ms", elapsedMs < 200)
    }

    // =========================================================================
    // 1.5.39 Memory Efficiency: Chunk-Based Streaming Invariant
    // =========================================================================

    @Test
    fun streamingChunkSize_isBoundedToPreventMemoryExhaustion() {
        val engineChunkSize = ChecksumEngine.BUFFER_SIZE
        assertTrue("Checksum buffer must be <= 64KB to prevent OOM", engineChunkSize <= 64 * 1024)
        assertTrue("Checksum buffer must be >= 4KB for I/O efficiency", engineChunkSize >= 4 * 1024)
    }
}
