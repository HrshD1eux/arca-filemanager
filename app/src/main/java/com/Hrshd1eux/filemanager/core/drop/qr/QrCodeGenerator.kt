package com.Hrshd1eux.filemanager.core.drop.qr

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Pure-Kotlin QR Code encoder (Byte mode, Error Correction Level M, Versions 1..10).
 * Zero third-party SDK dependencies. Supports up to 213 UTF-8 bytes (more than enough
 * for LAN URLs such as "http://192.168.100.254:8765?pin=123456").
 */
object QrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocksGroup1: Int,
        val dataCodewordsGroup1: Int,
        val numBlocksGroup2: Int,
        val dataCodewordsGroup2: Int,
        val alignmentCenters: IntArray
    ) {
        val totalDataCodewords: Int
            get() = (numBlocksGroup1 * dataCodewordsGroup1) + (numBlocksGroup2 * dataCodewordsGroup2)
    }

    // Standard ISO/IEC 18004 EC Level M tables for Versions 1..10
    private val versionSpecs = listOf(
        VersionSpec(1, 26, 10, 1, 16, 0, 0, intArrayOf()),
        VersionSpec(2, 44, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 70, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 100, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 134, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 172, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 196, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 242, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
        VersionSpec(9, 292, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
        VersionSpec(10, 346, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50))
    )

    private val remainderBitsByVersion = intArrayOf(0, 0, 7, 7, 7, 7, 7, 0, 0, 0, 0)

    // GF(256) log / exp tables with primitive polynomial 0x11D
    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x and 0x100 != 0) {
                x = x xor 0x11D
            }
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    /**
     * Generates a monochrome Bitmap containing the QR code with a quiet zone border.
     */
    fun generateBitmap(
        content: String,
        sizePx: Int = 512,
        quietZoneModules: Int = 4,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        val matrix = encodeMatrix(content) ?: return null
        val modules = matrix.size
        val totalModules = modules + quietZoneModules * 2
        val scale = (sizePx / totalModules).coerceAtLeast(1)
        val outputSize = totalModules * scale

        val pixels = IntArray(outputSize * outputSize) { backgroundColor }
        for (y in 0 until modules) {
            val pyBase = (y + quietZoneModules) * scale
            for (x in 0 until modules) {
                if (matrix[y][x]) {
                    val pxBase = (x + quietZoneModules) * scale
                    for (dy in 0 until scale) {
                        val rowOffset = (pyBase + dy) * outputSize + pxBase
                        for (dx in 0 until scale) {
                            pixels[rowOffset + dx] = foregroundColor
                        }
                    }
                }
            }
        }

        val bitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, outputSize, 0, 0, outputSize, outputSize)
        return bitmap
    }

    /**
     * Encodes UTF-8 [content] into a boolean QR module matrix (true = dark module).
     */
    fun encodeMatrix(content: String): Array<BooleanArray>? {
        val payload = content.toByteArray(Charsets.UTF_8)
        if (payload.isEmpty()) return null

        val spec = versionSpecs.firstOrNull { spec ->
            val charCountBits = if (spec.version < 10) 8 else 16
            val neededBits = 4 + charCountBits + payload.size * 8
            neededBits <= spec.totalDataCodewords * 8
        } ?: return null

        val dataCodewords = encodeDataCodewords(payload, spec)
        val finalStream = interleaveBlocks(dataCodewords, spec)

        val size = 17 + spec.version * 4
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        drawFunctionPatterns(spec, modules, isFunction)
        placeDataBits(finalStream, modules, isFunction)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            applyMask(mask, modules, isFunction)
            drawFormatInfo(mask, modules)
            val penalty = evaluatePenalty(modules)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
            }
            applyMask(mask, modules, isFunction) // revert mask
        }

        applyMask(bestMask, modules, isFunction)
        drawFormatInfo(bestMask, modules)
        return modules
    }

    private fun encodeDataCodewords(payload: ByteArray, spec: VersionSpec): IntArray {
        val capacityBits = spec.totalDataCodewords * 8
        val bits = ArrayList<Int>(capacityBits)

        fun appendBits(value: Int, length: Int) {
            for (i in (length - 1) downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator (0100)
        appendBits(0b0100, 4)
        val countBits = if (spec.version < 10) 8 else 16
        appendBits(payload.size, countBits)

        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        // Terminator (up to 4 zeros)
        val terminatorLen = minOf(4, capacityBits - bits.size)
        repeat(terminatorLen) { bits.add(0) }

        // Pad to byte boundary
        while (bits.size % 8 != 0) {
            bits.add(0)
        }

        val codewords = IntArray(spec.totalDataCodewords)
        val byteCount = bits.size / 8
        for (i in 0 until byteCount) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bits[i * 8 + b]
            }
            codewords[i] = v
        }

        val padBytes = intArrayOf(0xEC, 0x11)
        var padIndex = 0
        for (i in byteCount until spec.totalDataCodewords) {
            codewords[i] = padBytes[padIndex]
            padIndex = (padIndex + 1) and 1
        }
        return codewords
    }

    private fun interleaveBlocks(data: IntArray, spec: VersionSpec): IntArray {
        val totalBlocks = spec.numBlocksGroup1 + spec.numBlocksGroup2
        val dataBlocks = ArrayList<IntArray>(totalBlocks)
        val ecBlocks = ArrayList<IntArray>(totalBlocks)

        var offset = 0
        repeat(spec.numBlocksGroup1) {
            val block = data.copyOfRange(offset, offset + spec.dataCodewordsGroup1)
            offset += spec.dataCodewordsGroup1
            dataBlocks.add(block)
            ecBlocks.add(computeReedSolomon(block, spec.ecCodewordsPerBlock))
        }
        repeat(spec.numBlocksGroup2) {
            val block = data.copyOfRange(offset, offset + spec.dataCodewordsGroup2)
            offset += spec.dataCodewordsGroup2
            dataBlocks.add(block)
            ecBlocks.add(computeReedSolomon(block, spec.ecCodewordsPerBlock))
        }

        val result = ArrayList<Int>(spec.totalCodewords)
        val maxDataLen = maxOf(spec.dataCodewordsGroup1, spec.dataCodewordsGroup2)
        for (i in 0 until maxDataLen) {
            for (block in dataBlocks) {
                if (i < block.size) result.add(block[i])
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (ec in ecBlocks) {
                result.add(ec[i])
            }
        }
        return result.toIntArray()
    }

    private fun computeReedSolomon(data: IntArray, ecCount: Int): IntArray {
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            gen = polyMultiply(gen, intArrayOf(1, gfExp[i]))
        }

        val remainder = IntArray(ecCount)
        for (d in data) {
            val factor = d xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1)
            remainder[ecCount - 1] = 0
            if (factor != 0) {
                for (j in 0 until ecCount) {
                    remainder[j] = remainder[j] xor gfMul(gen[j + 1], factor)
                }
            }
        }
        return remainder
    }

    private fun polyMultiply(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(a.size + b.size - 1)
        for (i in a.indices) {
            for (j in b.indices) {
                out[i + j] = out[i + j] xor gfMul(a[i], b[j])
            }
        }
        return out
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun drawFunctionPatterns(
        spec: VersionSpec,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        val size = modules.size

        fun setFunc(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = dark
                isFunction[y][x] = true
            }
        }

        fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(abs(dx), abs(dy))
                    val dark = dist == 0 || dist == 1 || dist == 3
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) {
                        setFunc(x, y, dist <= 3 && dark)
                    }
                }
            }
        }

        // Finder patterns + separators
        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = (i % 2 == 0)
            if (!isFunction[6][i]) setFunc(i, 6, dark)
            if (!isFunction[i][6]) setFunc(6, i, dark)
        }

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (cy in centers) {
            for (cx in centers) {
                if (isFunction[cy][cx]) continue
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val dist = maxOf(abs(dx), abs(dy))
                        setFunc(cx + dx, cy + dy, dist != 1)
                    }
                }
            }
        }

        // Dark module
        setFunc(8, size - 8, true)

        // Reserve format information strips
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8][i] = true
                isFunction[i][8] = true
            }
        }
        for (i in 0..7) {
            isFunction[8][size - 1 - i] = true
            if (i < 7) {
                isFunction[size - 1 - i][8] = true
            }
        }

        // Version information (Versions 7..10)
        if (spec.version >= 7) {
            var rem = spec.version
            repeat(12) {
                rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            }
            val bits = (spec.version shl 12) or rem
            for (i in 0 until 18) {
                val dark = ((bits ushr i) and 1) != 0
                val a = size - 11 + (i % 3)
                val b = i / 3
                setFunc(a, b, dark)
                setFunc(b, a, dark)
            }
        }
    }

    private fun placeDataBits(
        codewords: IntArray,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        val size = modules.size
        val totalDataBits = codewords.size * 8
        var bitIdx = 0

        var right = size - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until size) {
                val upward = ((right + 1) and 2) == 0
                val y = if (upward) size - 1 - vert else vert
                for (j in 0..1) {
                    val x = right - j
                    if (!isFunction[y][x]) {
                        val dark = if (bitIdx < totalDataBits) {
                            ((codewords[bitIdx ushr 3] ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[y][x] = dark
                        bitIdx++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(
        mask: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        val size = modules.size
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isFunction[y][x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> ((y / 2) + (x / 3)) % 2 == 0
                    5 -> ((x * y) % 2) + ((x * y) % 3) == 0
                    6 -> ((((x * y) % 2) + ((x * y) % 3)) % 2) == 0
                    7 -> ((((x + y) % 2) + ((x * y) % 3)) % 2) == 0
                    else -> false
                }
                if (invert) {
                    modules[y][x] = !modules[y][x]
                }
            }
        }
    }

    private fun drawFormatInfo(mask: Int, modules: Array<BooleanArray>) {
        val size = modules.size
        // EC Level M = 00
        val data = (0b00 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) {
            modules[8][i] = ((bits ushr i) and 1) != 0
        }
        modules[8][7] = ((bits ushr 6) and 1) != 0
        modules[8][8] = ((bits ushr 7) and 1) != 0
        modules[7][8] = ((bits ushr 8) and 1) != 0
        for (i in 9..14) {
            modules[14 - i][8] = ((bits ushr i) and 1) != 0
        }

        for (i in 0..7) {
            modules[8][size - 1 - i] = ((bits ushr i) and 1) != 0
        }
        for (i in 8..14) {
            modules[size - 15 + i][8] = ((bits ushr i) and 1) != 0
        }
    }

    private fun evaluatePenalty(modules: Array<BooleanArray>): Int {
        val size = modules.size
        var penalty = 0

        // Rule 1: 5+ consecutive same-color modules in rows and columns
        for (y in 0 until size) {
            var runColor = modules[y][0]
            var runLen = 1
            for (x in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (x in 0 until size) {
            var runColor = modules[0][x]
            var runLen = 1
            for (y in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }

        // Rule 2: 2x2 blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (modules[y][x + 1] == c && modules[y + 1][x] == c && modules[y + 1][x + 1] == c) {
                    penalty += 3
                }
            }
        }

        // Rule 3: 1:1:3:1:1 finder-like patterns with 4-module white margin
        val patternA = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        val patternB = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)
        for (y in 0 until size) {
            for (x in 0..size - 11) {
                var matchA = true
                var matchB = true
                for (k in 0..10) {
                    if (modules[y][x + k] != patternA[k]) matchA = false
                    if (modules[y][x + k] != patternB[k]) matchB = false
                }
                if (matchA || matchB) penalty += 40
            }
        }
        for (x in 0 until size) {
            for (y in 0..size - 11) {
                var matchA = true
                var matchB = true
                for (k in 0..10) {
                    if (modules[y + k][x] != patternA[k]) matchA = false
                    if (modules[y + k][x] != patternB[k]) matchB = false
                }
                if (matchA || matchB) penalty += 40
            }
        }

        // Rule 4: Dark/light ratio balance
        var darkCount = 0
        for (row in modules) {
            for (cell in row) if (cell) darkCount++
        }
        val total = size * size
        val percent = (darkCount * 100) / total
        val prev5 = (percent / 5) * 5
        val next5 = prev5 + 5
        penalty += minOf(abs(prev5 - 50), abs(next5 - 50)) / 5 * 10

        return penalty
    }
}
