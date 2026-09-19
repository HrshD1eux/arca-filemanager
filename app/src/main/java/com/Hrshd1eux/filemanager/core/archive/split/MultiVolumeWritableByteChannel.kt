package com.Hrshd1eux.filemanager.core.archive.split

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption
import kotlin.math.max
import kotlin.math.min

/**
 * A [SeekableByteChannel] implementation that dynamically splits written output into consecutive
 * volumes of size [splitSizeBytes] (e.g. backup.7z.001, backup.7z.002, etc.).
 *
 * Designed specifically to support multi-volume 7z archive creation:
 * - Sequentially segments output data across volume boundaries.
 * - Supports seeking backwards across volume boundaries (e.g. when SevenZOutputFile seeks back
 *   to position 0 at finish() to write the 32-byte header into the first volume).
 */
class MultiVolumeWritableByteChannel(
    private val baseFile: File,
    val splitSizeBytes: Long,
    private val namingStrategy: (baseFile: File, volumeIndex: Int) -> File = { base, idx ->
        SplitArchiveResolver.getSplitVolumeName(base, idx)
    }
) : SeekableByteChannel {

    init {
        require(splitSizeBytes > 0) { "Split size must be greater than 0 bytes: $splitSizeBytes" }
    }

    private var isOpen = true
    private var globalPosition = 0L
    private var totalBytesWritten = 0L

    private val createdVolumes = mutableListOf<File>()
    private val openChannels = mutableMapOf<Int, FileChannel>()

    override fun isOpen(): Boolean = isOpen

    @Synchronized
    override fun position(): Long {
        ensureOpen()
        return globalPosition
    }

    @Synchronized
    override fun position(newPosition: Long): SeekableByteChannel {
        ensureOpen()
        require(newPosition >= 0) { "Position must be non-negative: $newPosition" }
        globalPosition = newPosition
        return this
    }

    @Synchronized
    override fun size(): Long {
        ensureOpen()
        return max(totalBytesWritten, globalPosition)
    }

    @Synchronized
    override fun write(src: ByteBuffer): Int {
        ensureOpen()
        val initialRemaining = src.remaining()
        if (initialRemaining == 0) return 0

        var writtenSoFar = 0

        while (src.hasRemaining()) {
            val volumeIndex = (globalPosition / splitSizeBytes).toInt() + 1
            val localOffset = globalPosition % splitSizeBytes
            val spaceInVolume = splitSizeBytes - localOffset
            val bytesToWrite = min(src.remaining().toLong(), spaceInVolume).toInt()

            val channel = getOrCreateChannel(volumeIndex)
            channel.position(localOffset)

            val bytesWritten: Int
            if (bytesToWrite < src.remaining()) {
                val originalLimit = src.limit()
                src.limit(src.position() + bytesToWrite)
                bytesWritten = channel.write(src)
                src.limit(originalLimit)
            } else {
                bytesWritten = channel.write(src)
            }

            if (bytesWritten <= 0) {
                break
            }

            globalPosition += bytesWritten
            totalBytesWritten = max(totalBytesWritten, globalPosition)
            writtenSoFar += bytesWritten
        }

        return writtenSoFar
    }

    @Synchronized
    override fun read(dst: ByteBuffer): Int {
        ensureOpen()
        if (globalPosition >= totalBytesWritten) {
            return -1
        }

        val volumeIndex = (globalPosition / splitSizeBytes).toInt() + 1
        val localOffset = globalPosition % splitSizeBytes
        val availableInVolume = splitSizeBytes - localOffset
        val availableTotal = totalBytesWritten - globalPosition
        val toRead = min(min(dst.remaining().toLong(), availableInVolume), availableTotal).toInt()

        if (toRead <= 0) {
            return 0
        }

        val channel = getOrCreateChannel(volumeIndex)
        channel.position(localOffset)

        val originalLimit = dst.limit()
        dst.limit(dst.position() + toRead)
        val readCount = channel.read(dst)
        dst.limit(originalLimit)

        if (readCount > 0) {
            globalPosition += readCount
        }

        return readCount
    }

    @Synchronized
    override fun truncate(size: Long): SeekableByteChannel {
        ensureOpen()
        require(size >= 0) { "Size must be non-negative: $size" }

        if (size < totalBytesWritten) {
            totalBytesWritten = size
            globalPosition = min(globalPosition, size)

            // Truncate channels above size
            val targetVolumeIndex = (size / splitSizeBytes).toInt() + 1
            val localOffset = size % splitSizeBytes

            val iterator = openChannels.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key > targetVolumeIndex) {
                    entry.value.close()
                    iterator.remove()
                } else if (entry.key == targetVolumeIndex) {
                    entry.value.truncate(localOffset)
                }
            }
        }

        return this
    }

    @Synchronized
    override fun close() {
        if (!isOpen) return
        isOpen = false
        var exception: IOException? = null

        for (channel in openChannels.values) {
            try {
                channel.force(true)
                channel.close()
            } catch (e: IOException) {
                if (exception == null) exception = e
            }
        }
        openChannels.clear()

        if (exception != null) {
            throw exception
        }
    }

    /**
     * Returns an ordered list of all volume files created during writing.
     */
    @Synchronized
    fun getCreatedVolumes(): List<File> = createdVolumes.toList()

    private fun getOrCreateChannel(volumeIndex: Int): FileChannel {
        val existing = openChannels[volumeIndex]
        if (existing != null && existing.isOpen) {
            return existing
        }

        val volumeFile = namingStrategy(baseFile, volumeIndex)
        volumeFile.parentFile?.mkdirs()

        val newChannel = FileChannel.open(
            volumeFile.toPath(),
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE
        )

        openChannels[volumeIndex] = newChannel
        if (!createdVolumes.contains(volumeFile)) {
            createdVolumes.add(volumeFile)
        }

        return newChannel
    }

    private fun ensureOpen() {
        if (!isOpen) {
            throw ClosedChannelException()
        }
    }
}
