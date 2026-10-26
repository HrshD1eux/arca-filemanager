package com.Hrshd1eux.filemanager.core.ftp.server

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service hosting the ArcaFtpServer to ensure uninterrupted
 * transfers even when ARCA is in the background or the screen is turned off.
 */
class FtpServerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var ftpServer: ArcaFtpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val ACTION_START = "com.Hrshd1eux.filemanager.ftp.START"
        const val ACTION_STOP = "com.Hrshd1eux.filemanager.ftp.STOP"
        private const val NOTIFICATION_ID = 21210
        private const val CHANNEL_ID = "arca_ftp_server_channel"
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
                stopFtpServer()
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_START, null -> {
                startFtpServer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startFtpServer() {
        val manager = FtpServerManager.getInstance()
        val config = FtpServerPreferences.loadConfig(this)
        val ip = FtpNetworkUtil.getLocalIpAddress() ?: "127.0.0.1"

        // Build & start foreground notification
        val notification = buildNotification(ip, config.port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                val server = ArcaFtpServer(
                    config = config,
                    appContext = applicationContext,
                    onStateChanged = { running, clients, sent, received ->
                        manager.onServerRunningStateChanged(running, clients, sent, received)
                    },
                    onLog = { log ->
                        manager.addLog(log)
                    }
                )
                ftpServer = server
                manager.attachServer(server)

                val result = server.start()
                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    manager.setErrorMessage(ex?.localizedMessage ?: "Failed to bind to port ${config.port}")
                    stopFtpServer()
                    stopForegroundCompat()
                    stopSelf()
                }
            } catch (e: Exception) {
                manager.setErrorMessage(e.localizedMessage ?: "FTP Server initialization error")
                stopFtpServer()
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private fun stopFtpServer() {
        val manager = FtpServerManager.getInstance()
        manager.detachServer()
        serviceScope.launch {
            ftpServer?.stop()
            ftpServer = null
        }
    }

    private fun buildNotification(ip: String, port: Int): Notification {
        val url = FtpNetworkUtil.formatFtpUrl(ip, port)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, FtpServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ARCA FTP Server Active")
            .setContentText("$url • Tap to open")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FTP Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of ARCA local FTP server when transferring files"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ARCA:FtpServerWakeLock")?.apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours timeout
            }

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ARCA:FtpServerWifiLock")?.apply {
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
        stopFtpServer()
        releaseLocks()
        serviceScope.cancel()
    }
}
