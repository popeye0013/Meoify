package com.meoify.music.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meoify.music.MeoifyApplication

/** Handles Pause / Resume / Cancel all from the ongoing download notification. */
class DownloadNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = (context.applicationContext as? MeoifyApplication)?.container?.downloadManager
            ?: return
        when (intent.action) {
            ACTION_PAUSE -> manager.pauseAll()
            ACTION_RESUME -> {
                manager.resumeAll()
                context.startForegroundService(DownloadService.startIntent(context))
            }
            ACTION_CANCEL -> manager.cancelAll()
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.meoify.music.download.PAUSE"
        const val ACTION_RESUME = "com.meoify.music.download.RESUME"
        const val ACTION_CANCEL = "com.meoify.music.download.CANCEL"
    }
}
