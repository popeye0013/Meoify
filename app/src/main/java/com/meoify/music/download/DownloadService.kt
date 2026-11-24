package com.meoify.music.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.DownloadState

/**
 * Keeps downloads alive in the background with a foreground notification.
 * The queue itself lives in DownloadManager so it survives service restarts.
 */
class DownloadService : Service() {

    private val manager get() = (application as MeoifyApplication).container.downloadManager

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.createChannels(this)
        startForeground(DownloadNotifications.NOTIFICATION_ID, foregroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DownloadNotifications.createChannels(this)
        manager.startQueue { stopSelfResult(startId) }
        // If the queue is already empty, stop immediately instead of lingering.
        if (!manager.isRunning()) stopSelfResult(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun foregroundNotification(): android.app.Notification {
        val title = manager.queue.value
            .firstOrNull {
                it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED
            }
            ?.track?.title
            ?: "Preparing downloads"
        return androidx.core.app.NotificationCompat.Builder(this, DownloadNotifications.CHANNEL_ID)
            .setSmallIcon(com.meoify.music.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Preparing download")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }

    companion object {
        fun startIntent(context: Context): Intent =
            Intent(context, DownloadService::class.java)

        fun start(context: Context) {
            runCatching { context.startForegroundService(startIntent(context)) }
        }
    }
}
