package com.Hrshd1eux.filemanager.feature.viewer.audio.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioPlaybackState
import com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController
import com.Hrshd1eux.filemanager.feature.viewer.audio.util.AudioTimeFormatter

/**
 * Docked mini player bar that lets users browse files while audio plays predictably.
 */
@Composable
fun AudioMiniPlayer(
    controller: AudioPlaybackController,
    modifier: Modifier = Modifier
) {
    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val currentTrack by controller.currentTrack.collectAsStateWithLifecycle()
    val metadata by controller.metadata.collectAsStateWithLifecycle()
    val currentPositionMs by controller.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by controller.durationMs.collectAsStateWithLifecycle()
    val artworkBytes by controller.artworkBytes.collectAsStateWithLifecycle()
    val playlist by controller.playlist.collectAsStateWithLifecycle()
    val currentIndex by controller.currentIndex.collectAsStateWithLifecycle()

    val isVisible = playbackState !is AudioPlaybackState.Idle && currentTrack != null

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val title = metadata?.title?.ifBlank { null }
            ?: currentTrack?.name?.substringBeforeLast('.')
            ?: currentTrack?.name
            ?: "Audio"
        val artist = metadata?.artist ?: "Unknown Artist"

        val artworkBitmap = remember(artworkBytes) {
            artworkBytes?.let {
                runCatching {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }.getOrNull()
            }
        }

        val progress = AudioTimeFormatter.calculateProgress(currentPositionMs, durationMs)
        val hasNext = currentIndex < playlist.size - 1

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clickable { controller.expandPlayer() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top micro progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork / Music Icon thumbnail
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkBitmap != null) {
                            Image(
                                bitmap = artworkBitmap.asImageBitmap(),
                                contentDescription = "Album Artwork",
                                modifier = Modifier.size(44.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Track Info
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (durationMs > 0L) {
                                "$artist • ${AudioTimeFormatter.formatDuration(currentPositionMs)} / ${AudioTimeFormatter.formatDuration(durationMs)}"
                            } else {
                                artist
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Play / Pause / Replay Button
                    IconButton(
                        onClick = { controller.togglePlayPause() }
                    ) {
                        val isPlaying = playbackState is AudioPlaybackState.Playing
                        val isCompleted = playbackState is AudioPlaybackState.Completed
                        Icon(
                            imageVector = when {
                                isCompleted -> Icons.Default.Refresh
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Next Track Button
                    IconButton(
                        onClick = { controller.playNext() },
                        enabled = hasNext
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Track",
                            tint = if (hasNext) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }

                    // Close / Stop Button
                    IconButton(
                        onClick = { controller.stopAndDismiss() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close player",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
