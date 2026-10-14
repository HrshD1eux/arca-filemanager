package com.Hrshd1eux.filemanager.feature.viewer.audio.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pure Kotlin utility for formatting audio durations, progress fractions, and position clamping.
 */
object AudioTimeFormatter {

    /**
     * Formats milliseconds into standard media duration string:
     * - "mm:ss" for durations under 1 hour (e.g., "03:45")
     * - "hh:mm:ss" for durations of 1 hour or more (e.g., "01:12:30")
     */
    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "00:00"

        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Calculates playback progress fraction between 0.0f and 1.0f.
     */
    fun calculateProgress(currentPositionMs: Long, totalDurationMs: Long): Float {
        if (totalDurationMs <= 0L) return 0f
        return (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Clamps seek position between 0 and duration.
     */
    fun clampPosition(targetMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return targetMs.coerceIn(0L, durationMs)
    }

    /**
     * Formats bitrate for display badge (e.g. "320 kbps").
     */
    fun formatBitrate(bitrateKbps: Int?): String? {
        if (bitrateKbps == null || bitrateKbps <= 0) return null
        return "$bitrateKbps kbps"
    }
}
