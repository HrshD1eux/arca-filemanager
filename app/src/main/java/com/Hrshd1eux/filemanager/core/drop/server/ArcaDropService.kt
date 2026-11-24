package com.Hrshd1eux.filemanager.core.drop.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.Hrshd1eux.filemanager.MainActivity
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.drop.model.DropReceivingProgress
import com.Hrshd1eux.filemanager.core.network.util.LocalLanNetworkUtil
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Service keeping ARCA Drop active reliably during background transfers
 * and screen-off multi-GB browser downloads/uploads.
 */
class ArcaDropService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var server: ArcaDropServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val ACTION_START = "com.Hrshd1eux.filemanager.drop.START"
        const val ACTION_STOP = "com.Hrshd1eux.filemanager.drop.STOP"
        private const val NOTIFICATION_ID = 21211
        private const val CHANNEL_ID = "arca_drop_server_channel"

        fun start(context: Context) {
            val intent = Intent(context, ArcaDropService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ArcaDropService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopDropServer()
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_START, null -> {
                startDropServer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDropServer() {
        val manager = ArcaDropManager.getInstance()
        val existing = server
        if (existing != null && existing.isRunning) {
            val ip = LocalLanNetworkUtil.getLocalIpAddress()
            manager.onServerStarted(existing.effectivePort, existing.sessionPin, ip)
            return
        }

        server?.stop()
        server = null

        val initialIp = LocalLanNetworkUtil.getLocalIpAddress()
        val initialNotif = buildNotification(initialIp, ArcaDropServer.DEFAULT_PORT, "••••••")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        serviceScope.launch {
            try {
                val dropServer = ArcaDropServer(
                    context = applicationContext,
                    receiveDirectoryProvider = { manager.getReceiveDirectoryFile() },
                    itemsProvider = { manager.state.value.sharedItems },
                    sharedTextProvider = { manager.state.value.sharedText },
                    onIpChanged = { updatedIp ->
                        manager.onIpUpdated(updatedIp)
                        val activePort = server?.effectivePort ?: ArcaDropServer.DEFAULT_PORT
                        val activePin = server?.sessionPin ?: ""
                        updateNotification(updatedIp, activePort, activePin)
                    },
                    onFileReceived = { name, savedPath, size ->
                        manager.onFileReceived(name, savedPath, size)
                    },
                    onTextReceived = { text ->
                        manager.onTextReceived(text)
                    },
                    onTransferProgress = { sentDelta, receivedDelta ->
                        manager.onTransferProgress(sentDelta, receivedDelta)
                    },
                    onReceivingProgress = { progress ->
                        manager.onReceivingProgress(progress)
                    },
                    onActiveConnectionsChanged = { count ->
                        manager.onActiveConnectionsChanged(count)
                    },
                    onLog = { msg, err ->
                        manager.appendLog(msg, err)
                    },
                    onIdleTimeout = {
                        manager.onServerStopped(applicationContext)
                        stopForegroundCompat()
                        stopSelf()
                    }
                )
                dropServer.start()
                server = dropServer
                val liveIp = LocalLanNetworkUtil.getLocalIpAddress()
                manager.onServerStarted(dropServer.effectivePort, dropServer.sessionPin, liveIp)
                updateNotification(liveIp, dropServer.effectivePort, dropServer.sessionPin, null)

                // Live state observer for transfer progress updates
                serviceScope.launch {
                    manager.state.collect { st ->
                        if (st.isRunning) {
                            val activePort = server?.effectivePort ?: st.port
                            val activePin = server?.sessionPin ?: st.pin
                            updateNotification(st.ipAddress, activePort, activePin, st.activeReceiving)
                        }
                    }
                }
            } catch (e: Exception) {
                manager.appendLog("Failed to start ARCA Drop: ${e.message ?: "Socket error"}", true)
                manager.onServerStopped(applicationContext)
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun stopDropServer() {
        server?.stop()
        server = null
        ArcaDropManager.getInstance().onServerStopped(applicationContext)
    }

    private fun updateNotification(
        ip: String?,
        port: Int,
        pin: String,
        receiving: DropReceivingProgress? = null
    ) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(ip, port, pin, receiving))
    }

    private fun buildNotification(
        ip: String?,
        port: Int,
        pin: String,
        receiving: DropReceivingProgress? = null
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ArcaDropService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (receiving != null) {
            val speedPart = if (receiving.speedBytesPerSec > 0) " • ${FileFormatter.formatFileSize(receiving.speedBytesPerSec)}/s" else ""
            val contentText = "${FileFormatter.formatFileSize(receiving.bytesReceived)} / ${FileFormatter.formatFileSize(receiving.totalBytes)} (${FileFormatter.formatFileSize(receiving.bytesRemaining)} left)$speedPart"

            builder.setContentTitle("Receiving: ${receiving.fileName} (${receiving.percentage}%)")
                .setContentText(contentText)
                .setProgress(100, receiving.percentage, false)
        } else {
            val urlText = if (ip != null) "http://$ip:$port • PIN $pin" else "Connect Wi-Fi or Hotspot • Port $port"
            builder.setContentTitle("ARCA Drop Active")
                .setContentText(urlText)
                .setProgress(0, 0, false)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ARCA Drop",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows local Wi-Fi URL & PIN while ARCA Drop is active"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ARCA:ArcaDropWakeLock")?.apply {
                acquire(6 * 60 * 60 * 1000L)
            }

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val lockMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm?.createWifiLock(lockMode, "ARCA:ArcaDropWifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDropServer()
        releaseLocks()
        serviceScope.cancel()
    }
}
