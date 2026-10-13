package com.Hrshd1eux.filemanager.feature.viewer.video.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pure Kotlin utility for formatting video playback positions and durations.
 */
object VideoTimeFormatter {

    /**
     * Formats milliseconds into standard media duration string:
     * - "mm:ss" for durations under 1 hour (e.g., "04:15")
     * - "hh:mm:ss" for durations of 1 hour or more (e.g., "01:23:45")
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
     * Clamps seek position between 0 and duration.
     */
    fun clampPosition(targetMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return targetMs.coerceIn(0L, durationMs)
    }
}
