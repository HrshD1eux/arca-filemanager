package com.Hrshd1eux.filemanager.core.archive.split

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

class MultiVolumeWritableByteChannelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun write_spanningMultipleVolumes_segmentsAccurately() {
        val baseFile = File(tempFolder.root, "test.7z")
        // Split size = 100 bytes
        val splitChannel = MultiVolumeWritableByteChannel(baseFile, splitSizeBytes = 100L)

        // Write 250 bytes
        val data = ByteArray(250) { it.toByte() }
        val buffer = ByteBuffer.wrap(data)

        val written = splitChannel.write(buffer)
        assertEquals(250, written)
        assertEquals(250L, splitChannel.size())
        assertEquals(250L, splitChannel.position())

        splitChannel.close()

        val volumes = splitChannel.getCreatedVolumes()
        assertEquals(3, volumes.size)

        val vol1 = File(tempFolder.root, "test.7z.001")
        val vol2 = File(tempFolder.root, "test.7z.002")
        val vol3 = File(tempFolder.root, "test.7z.003")

        assertTrue(vol1.exists())
        assertTrue(vol2.exists())
        assertTrue(vol3.exists())

        assertEquals(100L, vol1.length())
        assertEquals(100L, vol2.length())
        assertEquals(50L, vol3.length())

        // Validate byte content integrity across boundary
        val readBack1 = vol1.readBytes()
        val readBack2 = vol2.readBytes()
        val readBack3 = vol3.readBytes()

        val fullCombined = readBack1 + readBack2 + readBack3
        assertArrayEquals(data, fullCombined)
    }

    @Test
    fun write_backwardSeekToHeader_overwritesBeginningWithoutCorruptingLaterVolumes() {
        val baseFile = File(tempFolder.root, "header_test.7z")
        val splitChannel = MultiVolumeWritableByteChannel(baseFile, splitSizeBytes = 100L)

        // Write 200 bytes across vol1 and vol2
        val initialData = ByteArray(200) { 1 }
        splitChannel.write(ByteBuffer.wrap(initialData))

        // Seek back to position 0 (simulates SevenZOutputFile writing 32-byte header at finish)
        splitChannel.position(0)
        assertEquals(0L, splitChannel.position())

        // Overwrite first 10 bytes with specific signature
        val headerBytes = byteArrayOf(0x37, 0x7A.toByte(), 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 4, 1, 2)
        splitChannel.write(ByteBuffer.wrap(headerBytes))

        // Close channel
        splitChannel.close()

        val vol1 = File(tempFolder.root, "header_test.7z.001")
        val vol2 = File(tempFolder.root, "header_test.7z.002")

        assertEquals(100L, vol1.length())
        assertEquals(100L, vol2.length())

        val vol1Bytes = vol1.readBytes()
        // First 10 bytes must match headerBytes
        for (i in 0 until 10) {
            assertEquals(headerBytes[i], vol1Bytes[i])
        }
        // Remaining 90 bytes must still be 1
        for (i in 10 until 100) {
            assertEquals(1.toByte(), vol1Bytes[i])
        }

        // vol2 must remain entirely 1
        val vol2Bytes = vol2.readBytes()
        assertTrue(vol2Bytes.all { it == 1.toByte() })
    }

    @Test
    fun read_acrossVolumeBoundaries_readsContinuously() {
        val baseFile = File(tempFolder.root, "read_test.7z")
        val splitChannel = MultiVolumeWritableByteChannel(baseFile, splitSizeBytes = 50L)

        val srcData = ByteArray(120) { it.toByte() }
        splitChannel.write(ByteBuffer.wrap(srcData))

        // Seek back to 0
        splitChannel.position(0)

        // Read all 120 bytes into a new buffer
        val dstBuffer = ByteBuffer.allocate(120)
        var totalRead = 0
        while (dstBuffer.hasRemaining()) {
            val r = splitChannel.read(dstBuffer)
            if (r == -1) break
            totalRead += r
        }

        assertEquals(120, totalRead)
        assertArrayEquals(srcData, dstBuffer.array())

        // Reading past EOF returns -1
        val extra = ByteBuffer.allocate(10)
        assertEquals(-1, splitChannel.read(extra))

        splitChannel.close()
    }

    @Test(expected = ClosedChannelException::class)
    fun write_afterClose_throwsClosedChannelException() {
        val baseFile = File(tempFolder.root, "close_test.7z")
        val splitChannel = MultiVolumeWritableByteChannel(baseFile, splitSizeBytes = 100L)
        splitChannel.close()

        assertFalse(splitChannel.isOpen)
        splitChannel.write(ByteBuffer.wrap(byteArrayOf(1, 2, 3)))
    }
}
