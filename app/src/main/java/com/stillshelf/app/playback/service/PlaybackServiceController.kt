package com.stillshelf.app.playback.service

import android.app.Notification
import android.content.Context
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.playback.notification.PlaybackForegroundService

internal fun shouldClaimPlaybackServiceOwnership(
    activeOwner: BackendProvider?,
    serviceIsActive: Boolean,
    requestedOwner: BackendProvider
): Boolean {
    return activeOwner == null ||
        activeOwner == requestedOwner ||
        !serviceIsActive
}

internal fun shouldStopPlaybackServiceForOwner(
    activeOwner: BackendProvider?,
    requestedOwner: BackendProvider
): Boolean {
    return activeOwner == requestedOwner
}

object PlaybackServiceController {
    private val ownershipLock = Any()
    private var activeOwner: BackendProvider? = null

    fun startOrUpdate(
        context: Context,
        notification: Notification,
        owner: BackendProvider
    ) {
        synchronized(ownershipLock) {
            if (!shouldClaimPlaybackServiceOwnership(
                    activeOwner = activeOwner,
                    serviceIsActive = PlaybackForegroundService.isActive(),
                    requestedOwner = owner
                )
            ) {
                return
            }
            activeOwner = owner
            PlaybackForegroundService.startOrUpdate(context, notification)
        }
    }

    fun stop(context: Context, owner: BackendProvider): Boolean {
        synchronized(ownershipLock) {
            if (!shouldStopPlaybackServiceForOwner(activeOwner, owner)) {
                return false
            }
            activeOwner = null
            PlaybackForegroundService.stop(context)
            return true
        }
    }

    fun isActive(): Boolean = PlaybackForegroundService.isActive()
}
