package com.Hrshd1eux.filemanager.feature.viewer.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import com.Hrshd1eux.filemanager.core.storage.util.FileIntentDispatcher
import com.Hrshd1eux.filemanager.feature.browser.components.DeleteConfirmationDialog
import com.Hrshd1eux.filemanager.feature.browser.components.DestinationPickerDialog
import com.Hrshd1eux.filemanager.feature.browser.components.OperationProgressDialog
import com.Hrshd1eux.filemanager.feature.properties.FilePropertiesSheet
import com.Hrshd1eux.filemanager.feature.viewer.video.model.VideoMetadata
import com.Hrshd1eux.filemanager.feature.viewer.video.util.VideoTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun VideoViewerScreen(
    initialPath: String,
    categoryName: String? = null,
    directoryPath: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val seekTargetMs by viewModel.seekTargetMs.collectAsStateWithLifecycle()
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
    val isOperating by viewModel.isOperating.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Dialog & Sheet States (opening any dialog does not close the viewer!)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPropertiesSheet by remember { mutableStateOf(false) }
    var showCopyPicker by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }

    val currentVideo = viewModel.currentVideo

    LaunchedEffect(initialPath, categoryName, directoryPath) {
        viewModel.initialize(initialPath, categoryName, directoryPath)
    }

    // Auto-hide controls when playing
    LaunchedEffect(showControls, playbackState) {
        if (showControls && playbackState is VideoPlaybackState.Playing) {
            delay(4000)
            showControls = false
        }
    }

    // Fullscreen system bars management
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val win = activity?.window
            if (win != null) {
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Audio Focus and Lifecycle Observers
    DisposableEffect(lifecycleOwner, playbackState) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    viewModel.onAudioFocusLost()
                }
            }
        }
        var focusRequest: AudioFocusRequest? = null

        if (playbackState is VideoPlaybackState.Playing) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(afChangeListener)
                    .build()
                focusRequest = req
                audioManager?.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    afChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.onLifecyclePause()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(afChangeListener)
            }
        }
    }

    // Handle back navigation: exit fullscreen first if active
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        when (val state = listState) {
            is VideoViewerListState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            is VideoViewerListState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No video files found",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }
            is VideoViewerListState.Ready -> {
                if (currentVideo != null) {
                    NativeVideoPlayer(
                        node = currentVideo,
                        playbackState = playbackState,
                        seekTargetMs = seekTargetMs,
                        metadata = metadata,
                        onPrepared = { dur -> viewModel.onPrepared(dur) },
                        onCompleted = { viewModel.onCompleted() },
                        onError = { what, extra -> viewModel.onError(what, extra) },
                        onPositionUpdate = { pos -> viewModel.updateCurrentPosition(pos) },
                        onSeekHandled = { viewModel.clearSeekTarget() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Error Overlay
                if (playbackState is VideoPlaybackState.Error && currentVideo != null) {
                    val errorState = playbackState as VideoPlaybackState.Error
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CorruptedVideoCard(
                            node = currentVideo,
                            message = errorState.message,
                            isUnsupported = errorState.isUnsupportedCodec,
                            onRetry = { viewModel.retryCurrent() },
                            onOpenWith = { FileIntentDispatcher.openWith(context, currentVideo) }
                        )
                    }
                }

                // Center Play/Pause / Rewind / Forward overlay controls
                AnimatedVisibility(
                    visible = showControls && playbackState !is VideoPlaybackState.Error,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CenterPlaybackControls(
                        playbackState = playbackState,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onRewind = { viewModel.seekRelative(-10_000L) },
                        onFastForward = { viewModel.seekRelative(10_000L) },
                        onReplay = {
                            viewModel.onSeekEnd(0L)
                            viewModel.onPlay()
                        }
                    )
                }

                // Top Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    VideoTopOverlayBar(
                        node = currentVideo,
                        currentIndex = currentIndex,
                        totalCount = videos.size,
                        metadata = metadata,
                        onBack = onNavigateBack,
                        onProperties = {
                            viewModel.onPause()
                            showPropertiesSheet = true
                        }
                    )
                }

                // Bottom Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    VideoBottomOverlayBar(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        hasPrevious = viewModel.hasPrevious,
                        hasNext = viewModel.hasNext,
                        isFullscreen = isFullscreen,
                        onSeekStart = { target -> viewModel.onSeekStart(target) },
                        onSeekEnd = { target -> viewModel.onSeekEnd(target) },
                        onPrevious = { viewModel.goToPrevious() },
                        onNext = { viewModel.goToNext() },
                        onToggleFullscreen = { isFullscreen = !isFullscreen },
                        onShare = {
                            viewModel.onPause()
                            currentVideo?.let { FileIntentDispatcher.share(context, it) }
                        },
                        onCopy = {
                            viewModel.onPause()
                            showCopyPicker = true
                        },
                        onMove = {
                            viewModel.onPause()
                            showMovePicker = true
                        },
                        onDelete = {
                            viewModel.onPause()
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    // Single Delete Dialog
    if (showDeleteDialog && currentVideo != null) {
        DeleteConfirmationDialog(
            node = currentVideo,
            onConfirmDelete = { permanent ->
                showDeleteDialog = false
                viewModel.deleteCurrentVideo(permanent = permanent, onAllDeleted = onNavigateBack)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Properties Sheet
    if (showPropertiesSheet && currentVideo != null) {
        FilePropertiesSheet(
            node = currentVideo,
            onDismiss = { showPropertiesSheet = false }
        )
    }

    // Copy Destination Picker
    if (showCopyPicker && currentVideo != null) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Copy video to",
            confirmButtonText = "Copy Here",
            onDestinationSelected = { dest, verifyIntegrity ->
                showCopyPicker = false
                viewModel.copyCurrentVideo(dest, verifyIntegrity)
            },
            onDismiss = { showCopyPicker = false }
        )
    }

    // Move Destination Picker
    if (showMovePicker && currentVideo != null) {
        DestinationPickerDialog(
            storageProvider = remember { LocalFileSystemProvider() },
            title = "Move video to",
            confirmButtonText = "Move Here",
            onDestinationSelected = { dest, verifyIntegrity ->
                showMovePicker = false
                viewModel.moveCurrentVideo(dest, verifyIntegrity, onAllDeleted = onNavigateBack)
            },
            onDismiss = { showMovePicker = false }
        )
    }

    // Operation Progress Dialog
    if (isOperating && operationProgress != null) {
        OperationProgressDialog(
            progress = operationProgress!!,
            title = "Processing Video…",
            onCancel = { /* background execution completes */ }
        )
    }
}

@Composable
private fun NativeVideoPlayer(
    node: StorageNode,
    playbackState: VideoPlaybackState,
    seekTargetMs: Long?,
    metadata: VideoMetadata?,
    onPrepared: (durationMs: Long) -> Unit,
    onCompleted: () -> Unit,
    onError: (what: Int, extra: Int) -> Unit,
    onPositionUpdate: (posMs: Long) -> Unit,
    onSeekHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // Re-create MediaPlayer when active file changes
    DisposableEffect(node.path) {
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { player ->
                onPrepared(player.duration.toLong().coerceAtLeast(0L))
                player.start()
            }
            setOnCompletionListener {
                onCompleted()
            }
            setOnErrorListener { _, what, extra ->
                onError(what, extra)
                true
            }
            setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) {
                    videoWidth = w
                    videoHeight = h
                }
            }
            runCatching {
                setDataSource(node.path)
                surfaceHolder?.let { setDisplay(it) }
                prepareAsync()
            }.onFailure { e ->
                onError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1004)
            }
        }
        mediaPlayer = mp

        onDispose {
            runCatching {
                mp.stop()
                mp.reset()
                mp.release()
            }
            mediaPlayer = null
        }
    }

    // Synchronize play/pause commands from ViewModel to MediaPlayer
    LaunchedEffect(playbackState) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        when (playbackState) {
            is VideoPlaybackState.Playing -> {
                if (!mp.isPlaying) runCatching { mp.start() }
            }
            is VideoPlaybackState.Paused, is VideoPlaybackState.Completed -> {
                if (mp.isPlaying) runCatching { mp.pause() }
            }
            else -> Unit
        }
    }

    // Handle seek requests
    LaunchedEffect(seekTargetMs) {
        if (seekTargetMs != null) {
            val mp = mediaPlayer
            if (mp != null) {
                runCatching {
                    mp.seekTo(seekTargetMs.toInt())
                }
            }
            onSeekHandled()
        }
    }

    // Position polling ticker
    LaunchedEffect(playbackState) {
        while (playbackState is VideoPlaybackState.Playing) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                runCatching {
                    onPositionUpdate(mp.currentPosition.toLong())
                }
            }
            delay(250)
        }
    }

    val aspectRatio = remember(videoWidth, videoHeight, metadata) {
        val metaAspect = metadata?.aspectRatio ?: (16f / 9f)
        if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else {
            metaAspect
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
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
    }
}

@Composable
private fun CenterPlaybackControls(
    playbackState: VideoPlaybackState,
    onTogglePlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rewind 10s
        IconButton(
            onClick = onRewind,
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.FastRewind,
                contentDescription = "Rewind 10 seconds",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Center Play / Pause / Replay / Spinner
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                .clickable {
                    if (playbackState is VideoPlaybackState.Completed) {
                        onReplay()
                    } else {
                        onTogglePlayPause()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when (playbackState) {
                is VideoPlaybackState.Preparing -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                }
                is VideoPlaybackState.Playing -> {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause video",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                is VideoPlaybackState.Completed -> {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Replay video",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        // Fast Forward 10s
        IconButton(
            onClick = onFastForward,
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = "Fast-forward 10 seconds",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun VideoTopOverlayBar(
    node: StorageNode?,
    currentIndex: Int,
    totalCount: Int,
    metadata: VideoMetadata?,
    onBack: () -> Unit,
    onProperties: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = node?.name ?: "Video",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val details = buildString {
                    if (metadata != null && metadata.visualWidth > 0) {
                        append(metadata.formattedDimensions)
                    }
                    if (node != null && node.size > 0L) {
                        if (isNotEmpty()) append(" • ")
                        append(FileFormatter.formatFileSize(node.size))
                    }
                }

                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (totalCount > 1) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "${currentIndex + 1} / $totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(
                onClick = onProperties,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Video properties",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun VideoBottomOverlayBar(
    currentPositionMs: Long,
    durationMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    isFullscreen: Boolean,
    onSeekStart: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Seek progress bar & time indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = VideoTimeFormatter.formatDuration(currentPositionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.width(46.dp)
                )

                var isDragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableFloatStateOf(0f) }

                Slider(
                    value = if (isDragging) dragValue else currentPositionMs.toFloat().coerceAtMost(durationMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = { newValue ->
                        isDragging = true
                        dragValue = newValue
                        onSeekStart(newValue.toLong())
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeekEnd(dragValue.toLong())
                    },
                    valueRange = 0f..(durationMs.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Text(
                    text = VideoTimeFormatter.formatDuration(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(46.dp)
                )
            }

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous video
                IconButton(
                    onClick = onPrevious,
                    enabled = hasPrevious,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous video",
                        tint = if (hasPrevious) Color.White else Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Share
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Copy
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Move
                IconButton(
                    onClick = onMove,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = "Move video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Delete
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete video",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Fullscreen toggle
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Next video
                IconButton(
                    onClick = onNext,
                    enabled = hasNext,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next video",
                        tint = if (hasNext) Color.White else Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CorruptedVideoCard(
    node: StorageNode,
    message: String,
    isUnsupported: Boolean,
    onRetry: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E24)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = if (isUnsupported) "Unsupported Video Format" else "Unable to Play Video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Hardware playback relies on decoders available on this device. Non-standard codecs or proprietary containers may require an external player.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
                Button(
                    onClick = onOpenWith,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open with")
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
