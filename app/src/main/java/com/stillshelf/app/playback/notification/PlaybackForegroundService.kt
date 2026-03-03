package com.stillshelf.app.playback.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class PlaybackForegroundService : Service() {
    companion object {
        private const val ACTION_UPDATE = "com.stillshelf.app.playback.service.UPDATE"
        private const val ACTION_STOP = "com.stillshelf.app.playback.service.STOP"
        private const val NOTIFICATION_ID = 1101
        private const val CHANNEL_ID = "stillshelf_playback_v3"
        @Volatile
        private var latestNotification: Notification? = null

        fun startOrUpdate(context: Context, notification: Notification) {
            latestNotification = notification
            val intent = Intent(context, PlaybackForegroundService::class.java).apply {
                action = ACTION_UPDATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // Start foreground immediately to avoid startForegroundService timeout races.
        startForegroundCompat(latestNotification ?: buildFallbackNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                latestNotification = null
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE, null -> {
                val notification = latestNotification ?: buildFallbackNotification()
                startForegroundCompat(notification)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfoCompat.mediaPlaybackType()
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun buildFallbackNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("StillShelf")
            .setContentText("Preparing playback...")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        foregroundStarted = false
    }
}

private object ServiceInfoCompat {
    fun mediaPlaybackType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
    }
}
