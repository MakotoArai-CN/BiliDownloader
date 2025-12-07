package com.bilidownloader.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class BiliApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "下载通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示视频下载进度和状态"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: BiliApp
            private set

        const val DOWNLOAD_CHANNEL_ID = "bili_download_channel"
    }
}