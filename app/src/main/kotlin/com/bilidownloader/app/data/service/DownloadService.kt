package com.bilidownloader.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bilidownloader.app.BiliApp
import com.bilidownloader.app.R
import com.bilidownloader.app.ui.MainActivity

class DownloadService : Service() {

    private var notificationId = 1001
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "视频下载"
                startForegroundDownload(title)
            }
            ACTION_UPDATE_PROGRESS -> {
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "下载中"
                updateProgress(title, progress)
            }
            ACTION_COMPLETE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "下载完成"
                showCompletionNotification(title)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_ERROR -> {
                val error = intent.getStringExtra(EXTRA_ERROR) ?: "下载失败"
                showErrorNotification(error)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundDownload(title: String) {
        val notification = createNotificationBuilder(title, 0)
            .setOngoing(true)
            .build()

        startForeground(notificationId, notification)
    }

    private fun updateProgress(title: String, progress: Int) {
        val notification = createNotificationBuilder(title, progress)
            .setOngoing(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun showCompletionNotification(title: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, BiliApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId++, notification)
    }

    private fun showErrorNotification(error: String) {
        val notification = NotificationCompat.Builder(this, BiliApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("下载失败")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId++, notification)
    }

    private fun createNotificationBuilder(title: String, progress: Int): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, BiliApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, progress == 0)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_DOWNLOAD = "com.bilidownloader.app.action.START_DOWNLOAD"
        const val ACTION_UPDATE_PROGRESS = "com.bilidownloader.app.action.UPDATE_PROGRESS"
        const val ACTION_COMPLETE = "com.bilidownloader.app.action.COMPLETE"
        const val ACTION_ERROR = "com.bilidownloader.app.action.ERROR"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_ERROR = "extra_error"
    }
}