package com.Hrshd1eux.filemanager.feature.viewer.audio

import com.Hrshd1eux.filemanager.feature.viewer.audio.util.AudioTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTimeFormatterTest {

    @Test
    fun `formatDuration formats zero and negative durations as 00 00`() {
        assertEquals("00:00", AudioTimeFormatter.formatDuration(0L))
        assertEquals("00:00", AudioTimeFormatter.formatDuration(-500L))
    }

    @Test
    fun `formatDuration formats seconds and minutes correctly`() {
        assertEquals("00:05", AudioTimeFormatter.formatDuration(5_000L))
        assertEquals("00:45", AudioTimeFormatter.formatDuration(45_000L))
        assertEquals("03:25", AudioTimeFormatter.formatDuration(205_000L))
        assertEquals("59:59", AudioTimeFormatter.formatDuration(3_599_000L))
    }

    @Test
    fun `formatDuration formats hours correctly`() {
        assertEquals("01:00:00", AudioTimeFormatter.formatDuration(3_600_000L))
        assertEquals("01:23:45", AudioTimeFormatter.formatDuration(5_025_000L))
        assertEquals("12:34:56", AudioTimeFormatter.formatDuration(45_296_000L))
    }

    @Test
    fun `calculateProgress returns correct ratio bounded between 0 and 1`() {
        assertEquals(0f, AudioTimeFormatter.calculateProgress(0L, 100_000L), 0.001f)
        assertEquals(0.5f, AudioTimeFormatter.calculateProgress(50_000L, 100_000L), 0.001f)
        assertEquals(1f, AudioTimeFormatter.calculateProgress(100_000L, 100_000L), 0.001f)
        assertEquals(1f, AudioTimeFormatter.calculateProgress(150_000L, 100_000L), 0.001f)
        assertEquals(0f, AudioTimeFormatter.calculateProgress(-50L, 100_000L), 0.001f)
        assertEquals(0f, AudioTimeFormatter.calculateProgress(50_000L, 0L), 0.001f)
    }

    @Test
    fun `clampPosition clamps to valid range`() {
        assertEquals(0L, AudioTimeFormatter.clampPosition(-100L, 50_000L))
        assertEquals(25_000L, AudioTimeFormatter.clampPosition(25_000L, 50_000L))
        assertEquals(50_000L, AudioTimeFormatter.clampPosition(60_000L, 50_000L))
        assertEquals(0L, AudioTimeFormatter.clampPosition(10_000L, 0L))
    }

    @Test
    fun `formatBitrate formats kbps badge or returns null`() {
        assertEquals(null, AudioTimeFormatter.formatBitrate(null))
        assertEquals(null, AudioTimeFormatter.formatBitrate(0))
        assertEquals(null, AudioTimeFormatter.formatBitrate(-1))
        assertEquals("128 kbps", AudioTimeFormatter.formatBitrate(128))
        assertEquals("320 kbps", AudioTimeFormatter.formatBitrate(320))
    }
}
