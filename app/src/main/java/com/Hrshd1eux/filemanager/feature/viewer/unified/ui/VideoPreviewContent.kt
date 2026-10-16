package com.Hrshd1eux.filemanager.feature.viewer.unified.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.Hrshd1eux.filemanager.feature.viewer.video.model.VideoMetadata
import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoTimeFormatter
import kotlinx.coroutines.delay
import java.io.File

/**
 * Video player content with playback controls and timeline slider.
 */
@Composable
fun VideoPreviewContent(
    file: File,
    metadata: VideoMetadata?,
    modifier: Modifier = Modifier
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(metadata?.durationMs ?: 0L) }
    var videoWidth by remember { mutableIntStateOf(metadata?.visualWidth ?: 0) }
    var videoHeight by remember { mutableIntStateOf(metadata?.visualHeight ?: 0) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    DisposableEffect(file.path) {
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                durationMs = mp.duration.toLong().coerceAtLeast(0L)
                mp.start()
                isPlaying = true
                isCompleted = false
            }
            setOnCompletionListener {
                isPlaying = false
                isCompleted = true
            }
            setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) {
                    videoWidth = w
                    videoHeight = h
                }
            }
            runCatching {
                setDataSource(file.path)
                surfaceHolder?.let { setDisplay(it) }
                prepareAsync()
            }
        }
        mediaPlayer = player

        onDispose {
            runCatching {
                player.stop()
                player.reset()
                player.release()
            }
            mediaPlayer = null
        }
    }

    // Position polling
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                currentPositionMs = mp.currentPosition.toLong()
            }
            delay(250)
        }
    }

    val aspectRatio = remember(videoWidth, videoHeight, metadata) {
        if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else {
            metadata?.aspectRatio ?: (16f / 9f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surfaceHolder = holder
                            mediaPlayer?.setDisplay(holder)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            surfaceHolder = null
                            mediaPlayer?.setDisplay(null)
                        }
                    })
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
        )

        // Overlay playback controls at bottom of video
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val activePos = if (isDraggingSlider) dragPositionMs.toLong() else currentPositionMs
            val sliderMax = durationMs.coerceAtLeast(1L).toFloat()

            // Seek slider
            Slider(
                value = activePos.toFloat().coerceIn(0f, sliderMax),
                onValueChange = {
                    isDraggingSlider = true
                    dragPositionMs = it
                },
                onValueChangeFinished = {
                    mediaPlayer?.seekTo(dragPositionMs.toInt())
                    currentPositionMs = dragPositionMs.toLong()
                    isDraggingSlider = false
                },
                valueRange = 0f..sliderMax,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${VideoTimeFormatter.formatDuration(activePos)} / ${VideoTimeFormatter.formatDuration(durationMs)}",
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 4.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            val newPos = (currentPositionMs - 10000L).coerceAtLeast(0L)
                            mediaPlayer?.seekTo(newPos.toInt())
                            currentPositionMs = newPos
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White
                        )
                    }

                    // Play / Pause / Replay
                    Surface(
                        onClick = {
                            val mp = mediaPlayer ?: return@Surface
                            if (isCompleted) {
                                mp.seekTo(0)
                                mp.start()
                                isPlaying = true
                                isCompleted = false
                            } else if (isPlaying) {
                                mp.pause()
                                isPlaying = false
                            } else {
                                mp.start()
                                isPlaying = true
                            }
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isCompleted -> Icons.Default.Refresh
                                    isPlaying -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White
                            )
                        }
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            val newPos = (currentPositionMs + 10000L).coerceAtMost(durationMs)
                            mediaPlayer?.seekTo(newPos.toInt())
                            currentPositionMs = newPos
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Fast forward 10 seconds",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
