package com.stillshelf.app.playback.service

import android.app.Notification
import android.content.Context
import com.stillshelf.app.playback.notification.PlaybackForegroundService

object PlaybackServiceController {
    fun startOrUpdate(context: Context, notification: Notification) {
        PlaybackForegroundService.startOrUpdate(context, notification)
    }

    fun stop(context: Context) {
        PlaybackForegroundService.stop(context)
    }
}
