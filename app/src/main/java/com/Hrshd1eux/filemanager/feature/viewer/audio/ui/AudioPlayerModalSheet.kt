package com.Hrshd1eux.filemanager.feature.viewer.audio.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioPlaybackState
import com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController
import com.Hrshd1eux.filemanager.feature.viewer.audio.util.AudioTimeFormatter
import java.io.File

/**
 * Expandable full audio player modal sheet with scrub controls, tracklist, and file actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerModalSheet(
    controller: AudioPlaybackController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val currentTrack by controller.currentTrack.collectAsStateWithLifecycle()
    val metadata by controller.metadata.collectAsStateWithLifecycle()
    val currentPositionMs by controller.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by controller.durationMs.collectAsStateWithLifecycle()
    val artworkBytes by controller.artworkBytes.collectAsStateWithLifecycle()
    val playlist by controller.playlist.collectAsStateWithLifecycle()
    val currentIndex by controller.currentIndex.collectAsStateWithLifecycle()

    var showPlaylistView by remember { mutableStateOf(false) }
    var inspectedNode by remember { mutableStateOf<StorageNode?>(null) }
    var nodeToDelete by remember { mutableStateOf<StorageNode?>(null) }

    // Scrub state: when dragging, track thumb position locally
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val activePositionMs = if (isDraggingSlider) dragPositionMs.toLong() else currentPositionMs

    val title = metadata?.title?.ifBlank { null }
        ?: currentTrack?.name?.substringBeforeLast('.')
        ?: currentTrack?.name
        ?: "Audio"
    val artist = metadata?.artist ?: "Unknown Artist"
    val album = metadata?.album

    val artworkBitmap = remember(artworkBytes) {
        artworkBytes?.let {
            runCatching {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }.getOrNull()
        }
    }

    val hasPrevious = currentIndex > 0 || currentPositionMs > 3000L
    val hasNext = currentIndex < playlist.size - 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header: Collapse Button, Title, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player"
                    )
                }

                Text(
                    text = if (showPlaylistView) "Now Playing Queue (${playlist.size})" else "Now Playing",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row {
                    // Queue toggle
                    IconButton(onClick = { showPlaylistView = !showPlaylistView }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = if (showPlaylistView) "Show Player" else "Show Playlist Queue",
                            tint = if (showPlaylistView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Properties
                    IconButton(
                        onClick = { currentTrack?.let { inspectedNode = it } },
                        enabled = currentTrack != null
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Track Properties"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (showPlaylistView) {
                // Playlist / Queue View
                PlaylistQueueView(
                    playlist = playlist,
                    currentIndex = currentIndex,
                    onSelectTrack = { index ->
                        controller.playTrackAt(index)
                        showPlaylistView = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                )
            } else {
                // Main Player View
                // Artwork / Visualizer Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (artworkBitmap != null) {
                        Image(
                            bitmap = artworkBitmap.asImageBitmap(),
                            contentDescription = "Album Artwork",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Error Card if state is Error
                val errorState = playbackState as? AudioPlaybackState.Error
                if (errorState != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasNext) {
                                TextButton(onClick = { controller.playNext() }) {
                                    Text("Skip Next")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Track Title & Artist
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (!album.isNullOrBlank()) "$artist — $album" else artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Bitrate / Extension badges
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val ext = currentTrack?.name?.substringAfterLast('.', "")?.uppercase()
                        if (!ext.isNullOrBlank()) {
                            BadgeChip(text = ext)
                        }
                        metadata?.bitrateKbps?.let {
                            BadgeChip(text = "$it kbps")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Seek Timeline Slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                    val sliderValue = activePositionMs.toFloat().coerceIn(0f, sliderMax)

                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isDraggingSlider = true
                            dragPositionMs = it
                        },
                        onValueChangeFinished = {
                            controller.seekTo(dragPositionMs.toLong())
                            isDraggingSlider = false
                        },
                        valueRange = 0f..sliderMax,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = AudioTimeFormatter.formatDuration(activePositionMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AudioTimeFormatter.formatDuration(durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Playback Control Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Track
                    IconButton(
                        onClick = { controller.playPrevious() },
                        enabled = hasPrevious,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous Track",
                            tint = if (hasPrevious) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Skip Backward 10s
                    IconButton(
                        onClick = { controller.skipBackward(10) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10 seconds",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Play / Pause / Replay Large Button
                    Surface(
                        onClick = { controller.togglePlayPause() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(68.dp),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val isPlaying = playbackState is AudioPlaybackState.Playing
                            val isCompleted = playbackState is AudioPlaybackState.Completed
                            Icon(
                                imageVector = when {
                                    isCompleted -> Icons.Default.Refresh
                                    isPlaying -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Skip Forward 10s
                    IconButton(
                        onClick = { controller.skipForward(10) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10 seconds",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Next Track
                    IconButton(
                        onClick = { controller.playNext() },
                        enabled = hasNext,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Track",
                            tint = if (hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom File Action Row: Share, Delete, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share
                    TextButton(
                        onClick = {
                            currentTrack?.let { FileIntentDispatcher.share(context, it) }
                        },
                        enabled = currentTrack != null
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }

                    // Delete
                    TextButton(
                        onClick = { currentTrack?.let { nodeToDelete = it } },
                        enabled = currentTrack != null,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }

                    // Stop and Dismiss
                    TextButton(
                        onClick = {
                            controller.stopAndDismiss()
                            onDismiss()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }

    // Properties Bottom Sheet
    inspectedNode?.let { node ->
        FilePropertiesSheet(
            node = node,
            onDismiss = { inspectedNode = null }
        )
    }

    // Delete Confirmation Dialog
    nodeToDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text("Delete Audio File?") },
            text = { Text("Permanently delete \"${node.name}\" from device storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        val file = File(node.path)
                        runCatching { file.delete() }
                        controller.removeTrack(node)
                        nodeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaylistQueueView(
    playlist: List<StorageNode>,
    currentIndex: Int,
    onSelectTrack: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlist.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Playlist is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(playlist, key = { _, node -> node.path }) { index, track ->
                val isPlayingCurrent = index == currentIndex
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTrack(index) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlayingCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlayingCurrent) Icons.Default.MusicNote else Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = if (isPlayingCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isPlayingCurrent) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isPlayingCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = FileFormatter.formatFileSize(track.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPlayingCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
