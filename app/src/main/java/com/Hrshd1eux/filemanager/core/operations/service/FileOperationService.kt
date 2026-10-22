package com.Hrshd1eux.filemanager.core.operations.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.Hrshd1eux.filemanager.MainActivity
import com.Hrshd1eux.filemanager.R
import com.Hrshd1eux.filemanager.core.operations.queue.OperationManager
import com.Hrshd1eux.filemanager.core.operations.queue.OperationStatus
import com.Hrshd1eux.filemanager.core.operations.queue.QueuedOperation
import com.Hrshd1eux.filemanager.core.privacy.NotificationPrivacyGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FileOperationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Immediate startForeground call to strictly satisfy Android 8+ foreground service launch timeout
        val initialNotification = buildInitialNotification()
        startForegroundWithType(initialNotification)

        observeOperations()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ACTIVE) {
            OperationManager.getInstance().cancelActive()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for active file and archive operations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeOperations() {
        val operationManager = OperationManager.getInstance()
        observerJob = serviceScope.launch {
            combine(
                operationManager.activeOperation,
                operationManager.waitingQueue
            ) { active, waiting ->
                active to waiting.size
            }.collect { (activeOp, waitingCount) ->
                if (activeOp != null && activeOp.status == OperationStatus.RUNNING) {
                    val notification = buildNotification(activeOp, waitingCount)
                    startForegroundWithType(notification)
                } else if (activeOp == null && waitingCount == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun buildInitialNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("File Operations")
            .setContentText("Processing operations in background...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildNotification(operation: QueuedOperation, waitingCount: Int): Notification {
        val percent = (operation.progress.progressRatio * 100).toInt()

        // Privacy rule: Zero sensitive file or path metadata in notifications
        val safeTitle = NotificationPrivacyGuard.sanitizeNotificationTitle(operation.title)
        val waitingSuffix = if (waitingCount > 0) " (+$waitingCount waiting)" else ""
        val safeContent = "Processing • $percent%$waitingSuffix"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, FileOperationService::class.java).apply {
            action = ACTION_CANCEL_ACTIVE
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(safeTitle)
            .setContentText(safeContent)
            .setProgress(100, percent, operation.progress.totalBytes == 0L && operation.progress.totalFiles == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "arca_operations_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_CANCEL_ACTIVE = "com.Hrshd1eux.filemanager.ACTION_CANCEL_ACTIVE"

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, FileOperationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
