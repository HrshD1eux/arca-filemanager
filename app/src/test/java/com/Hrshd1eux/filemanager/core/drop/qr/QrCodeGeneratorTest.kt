package com.Hrshd1eux.filemanager.core.drop.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeGeneratorTest {

    @Test
    fun encodeMatrix_emptyAndTooLongPayloadsReturnNull() {
        assertNull(QrCodeGenerator.encodeMatrix(""))
        assertNull(QrCodeGenerator.encodeMatrix("a".repeat(300)))
    }

    @Test
    fun encodeMatrix_typicalLanUrlProducesValidSquareFinderPatterns() {
        val url = "http://192.168.100.254:8765?pin=123456"
        val matrix = QrCodeGenerator.encodeMatrix(url)
        assertNotNull(matrix)
        val grid = matrix!!
        val size = grid.size
        assertTrue("QR size must be valid ISO dimension >= 21 and (size-17)%4==0", size >= 21 && (size - 17) % 4 == 0)
        assertTrue(grid.all { it.size == size })

        // Verify 7x7 finder patterns at top-left, top-right, and bottom-left corners
        assertFinderPatternAt(grid, 0, 0)
        assertFinderPatternAt(grid, size - 7, 0)
        assertFinderPatternAt(grid, 0, size - 7)
    }

    @Test
    fun encodeMatrix_isDeterministicForSamePayload() {
        val url = "http://10.0.0.15:8765?pin=654321"
        val first = QrCodeGenerator.encodeMatrix(url)!!
        val second = QrCodeGenerator.encodeMatrix(url)!!
        assertEquals(first.size, second.size)
        for (y in first.indices) {
            assertTrue(first[y].contentEquals(second[y]))
        }
    }

    private fun assertFinderPatternAt(grid: Array<BooleanArray>, startX: Int, startY: Int) {
        for (dy in 0 until 7) {
            for (dx in 0 until 7) {
                val expectedDark =
                    dx == 0 || dx == 6 || dy == 0 || dy == 6 ||
                        (dx in 2..4 && dy in 2..4)
                assertEquals(
                    "Finder mismatch at ($dx,$dy) from ($startX,$startY)",
                    expectedDark,
                    grid[startY + dy][startX + dx]
                )
            }
        }
    }
}
