package com.meoify.music.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meoify.music.MainActivity
import com.meoify.music.R
import com.meoify.music.data.model.DownloadFailure
import com.meoify.music.data.model.formatDownloadBytes

object DownloadNotifications {

    const val CHANNEL_ID = "downloads"
    const val COMPLETED_CHANNEL_ID = "downloads_completed"
    const val QUIET_COMPLETED_CHANNEL_ID = "downloads_completed_quiet"
    const val NOTIFICATION_ID = 4101

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Progress of songs saved for offline playback" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                COMPLETED_CHANNEL_ID,
                "Playlist downloads completed",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "A playlist or album finished downloading" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                QUIET_COMPLETED_CHANNEL_ID,
                "Song downloads completed",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Individual songs saved without a notification sound" },
        )
    }

    fun showQueued(context: Context, title: String, count: Int = 1) {
        val notification = base(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (count > 1) "Preparing $count songs" else "Preparing download")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .addAction(pauseAction(context))
            .addAction(cancelAction(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun showProgress(
        context: Context,
        title: String,
        downloaded: Long,
        total: Long?,
        paused: Boolean,
        queueSize: Int,
    ) {
        val queueSuffix = if (queueSize > 1) " • $queueSize songs" else ""
        val finished = total != null && total > 0L && downloaded >= total
        val progressText = when {
            paused -> "Paused"
            downloaded <= 0L -> "Downloading$queueSuffix"
            finished -> "Saving$queueSuffix"
            total != null && total > 0L ->
                "${formatDownloadBytes(downloaded)} / ${formatDownloadBytes(total)}$queueSuffix"
            else -> formatDownloadBytes(downloaded) + queueSuffix
        }
        val builder = base(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(progressText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (!paused) {
            if (total != null && total > 0L && !finished) {
                builder.setProgress(100, ((downloaded * 100L) / total).toInt().coerceIn(0, 100), false)
            } else {
                builder.setProgress(0, 0, true)
            }
            builder.addAction(pauseAction(context))
        } else {
            builder.addAction(resumeAction(context))
        }
        builder.addAction(cancelAction(context))
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    fun showPaused(context: Context, title: String) {
        showProgress(context, title, 0L, null, paused = true, queueSize = 0)
    }

    fun showCompleted(
        context: Context,
        title: String,
        count: Int = 1,
        sound: Boolean = false,
    ) {
        // Clear the ongoing progress notification; otherwise it stays pinned at
        // "X / X MB" forever next to this completion notice.
        cancelProgress(context)
        val channel = if (sound) COMPLETED_CHANNEL_ID else QUIET_COMPLETED_CHANNEL_ID
        val text = if (count > 1) {
            "$count songs saved for offline playback"
        } else {
            "Saved for offline playback"
        }
        val notification = base(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification) }
    }

    fun showFailure(
        context: Context,
        title: String,
        failure: DownloadFailure,
        sound: Boolean = false,
    ) {
        cancelProgress(context)
        val reason = when (failure) {
            DownloadFailure.STORAGE_FULL -> "Not enough free storage"
            DownloadFailure.STORAGE_ACCESS -> "Storage isn't available"
            DownloadFailure.NETWORK -> "Check your connection"
            DownloadFailure.UNSUPPORTED_FORMAT -> "Unsupported audio format"
            DownloadFailure.OTHER -> "Tap to retry from Downloads"
        }
        val channel = if (sound) COMPLETED_CHANNEL_ID else QUIET_COMPLETED_CHANNEL_ID
        val notification = base(context, channel)
            .setContentTitle("Couldn't download $title")
            .setContentText(reason)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification) }
    }

    fun cancelProgress(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    fun cancel(context: Context) = cancelProgress(context)

    private fun base(context: Context, channel: String): NotificationCompat.Builder {
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(intent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun pauseAction(context: Context): NotificationCompat.Action =
        action(context, DownloadNotificationReceiver.ACTION_PAUSE, "Pause", R.drawable.ic_notification)

    private fun resumeAction(context: Context): NotificationCompat.Action =
        action(context, DownloadNotificationReceiver.ACTION_RESUME, "Resume", R.drawable.ic_notification)

    private fun cancelAction(context: Context): NotificationCompat.Action =
        action(context, DownloadNotificationReceiver.ACTION_CANCEL, "Cancel", R.drawable.ic_notification)

    private fun action(
        context: Context,
        action: String,
        label: String,
        icon: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, DownloadNotificationReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, label, pendingIntent)
    }
}
