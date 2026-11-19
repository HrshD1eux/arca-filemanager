package com.Hrshd1eux.filemanager.feature.viewer.audio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.Hrshd1eux.filemanager.MainActivity
import com.Hrshd1eux.filemanager.feature.viewer.audio.model.AudioPlaybackState
import com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Audio Playback Service with MediaSession integration.
 * Keeps playback active when screen is off or app is backgrounded.
 * Renders native media notification with lockscreen playback controls.
 */
class AudioPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playbackObserverJob: Job? = null
    private var mediaSession: MediaSession? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        initMediaSession()
        observeController()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = AudioPlaybackController.getInstance(applicationContext)
        when (intent?.action) {
            ACTION_PLAY -> controller.play()
            ACTION_PAUSE -> controller.pause()
            ACTION_TOGGLE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.playNext()
            ACTION_PREV -> controller.playPrevious()
            ACTION_STOP -> {
                controller.stopAndDismiss()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun initMediaSession() {
        val controller = AudioPlaybackController.getInstance(applicationContext)
        mediaSession = MediaSession(this, "ArcaAudioSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { controller.play() }
                override fun onPause() { controller.pause() }
                override fun onSkipToNext() { controller.playNext() }
                override fun onSkipToPrevious() { controller.playPrevious() }
                override fun onSeekTo(pos: Long) { controller.seekTo(pos) }
                override fun onStop() {
                    controller.stopAndDismiss()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    private fun observeController() {
        val controller = AudioPlaybackController.getInstance(applicationContext)
        playbackObserverJob?.cancel()
        playbackObserverJob = serviceScope.launch {
            controller.playbackState.collect { state ->
                when (state) {
                    is AudioPlaybackState.Playing -> updateNotificationAndSession(state, isPlaying = true)
                    is AudioPlaybackState.Paused -> updateNotificationAndSession(state, isPlaying = false)
                    is AudioPlaybackState.Completed -> updateNotificationAndSession(state, isPlaying = false)
                    is AudioPlaybackState.Idle, is AudioPlaybackState.Error -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    is AudioPlaybackState.Preparing -> {
                        // Keep current notification during track load
                    }
                }
            }
        }
    }

    private fun updateNotificationAndSession(state: AudioPlaybackState, isPlaying: Boolean) {
        val controller = AudioPlaybackController.getInstance(applicationContext)
        val track = controller.currentTrack.value ?: return
        val metadata = controller.metadata.value
        val artworkBytes = controller.artworkBytes.value

        val title = metadata?.title?.takeIf { it.isNotBlank() } ?: track.name
        val artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: "ARCA Audio Player"
        val duration = controller.durationMs.value
        val position = controller.currentPositionMs.value

        val albumArtBitmap = artworkBytes?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }

        // Update MediaSession
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                position,
                1.0f
            )
        mediaSession?.setPlaybackState(stateBuilder.build())

        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        if (albumArtBitmap != null) {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArtBitmap)
        }
        mediaSession?.setMetadata(metaBuilder.build())

        // Build notification actions
        val prevPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePendingIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPendingIntent = PendingIntent.getService(
            this, 3,
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 4,
            Intent(this, AudioPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_previous),
            "Previous",
            prevPendingIntent
        ).build()

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseAction = Notification.Action.Builder(
            Icon.createWithResource(this, playPauseIcon),
            playPauseTitle,
            togglePendingIntent
        ).build()

        val nextAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_next),
            "Next",
            nextPendingIntent
        ).build()

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val notificationBuilder = Notification.Builder(this, CHANNEL_ID)
            .setStyle(mediaStyle)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setOngoing(isPlaying)

        if (albumArtBitmap != null) {
            notificationBuilder.setLargeIcon(albumArtBitmap)
        }

        val notification = notificationBuilder.build()

        if (isPlaying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback controls and audio timeline"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        playbackObserverJob?.cancel()
        serviceScope.cancel()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "arca_audio_playback_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_PLAY = "com.Hrshd1eux.filemanager.ACTION_AUDIO_PLAY"
        const val ACTION_PAUSE = "com.Hrshd1eux.filemanager.ACTION_AUDIO_PAUSE"
        const val ACTION_TOGGLE = "com.Hrshd1eux.filemanager.ACTION_AUDIO_TOGGLE"
        const val ACTION_NEXT = "com.Hrshd1eux.filemanager.ACTION_AUDIO_NEXT"
        const val ACTION_PREV = "com.Hrshd1eux.filemanager.ACTION_AUDIO_PREV"
        const val ACTION_STOP = "com.Hrshd1eux.filemanager.ACTION_AUDIO_STOP"

        fun start(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
