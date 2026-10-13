package com.Hrshd1eux.filemanager.feature.viewer.video

import com.Hrshd1eux.filemanager.feature.viewer.video.model.VideoMetadata
import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTimeFormatterTest {

    @Test
    fun formatDuration_zeroAndNegativeReturnDoubleZero() {
        assertEquals("00:00", VideoTimeFormatter.formatDuration(0L))
        assertEquals("00:00", VideoTimeFormatter.formatDuration(-500L))
    }

    @Test
    fun formatDuration_secondsAndMinutesUnderOneHour() {
        // 45 seconds
        assertEquals("00:45", VideoTimeFormatter.formatDuration(45_000L))

        // 4 minutes 12 seconds
        assertEquals("04:12", VideoTimeFormatter.formatDuration((4 * 60 + 12) * 1000L))

        // 59 minutes 59 seconds
        assertEquals("59:59", VideoTimeFormatter.formatDuration((59 * 60 + 59) * 1000L))
    }

    @Test
    fun formatDuration_oneHourAndAboveIncludesHours() {
        // Exactly 1 hour
        assertEquals("01:00:00", VideoTimeFormatter.formatDuration(3600 * 1000L))

        // 2 hours 15 minutes 30 seconds
        val twoHours15Min = (2 * 3600 + 15 * 60 + 30) * 1000L
        assertEquals("02:15:30", VideoTimeFormatter.formatDuration(twoHours15Min))
    }

    @Test
    fun clampPosition_boundsChecking() {
        val duration = 60_000L

        assertEquals(0L, VideoTimeFormatter.clampPosition(-100L, duration))
        assertEquals(30_000L, VideoTimeFormatter.clampPosition(30_000L, duration))
        assertEquals(60_000L, VideoTimeFormatter.clampPosition(80_000L, duration))
        assertEquals(0L, VideoTimeFormatter.clampPosition(500L, 0L))
    }

    @Test
    fun videoMetadata_rotationAndAspectCalculations() {
        val landscape = VideoMetadata(width = 1920, height = 1080, durationMs = 120_000L, rotationDegrees = 0)
        assertEquals(1920, landscape.visualWidth)
        assertEquals(1080, landscape.visualHeight)
        assertEquals(1920f / 1080f, landscape.aspectRatio, 0.01f)
        assertEquals("1920 × 1080", landscape.formattedDimensions)
        assertEquals("02:00", landscape.formattedDuration)

        val portraitRotated = VideoMetadata(width = 1920, height = 1080, durationMs = 65_000L, rotationDegrees = 90)
        assertEquals(1080, portraitRotated.visualWidth)
        assertEquals(1920, portraitRotated.visualHeight)
        assertEquals(1080f / 1920f, portraitRotated.aspectRatio, 0.01f)
        assertEquals("1080 × 1920", portraitRotated.formattedDimensions)
        assertEquals("01:05", portraitRotated.formattedDuration)
    }
}
