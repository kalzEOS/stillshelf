package com.stillshelf.app.playback.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.navidrome.NavidromePlayerController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class PlaybackActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        if (action.isBlank()) return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlaybackActionReceiverEntryPoint::class.java
        )
        when {
            action.startsWith("com.stillshelf.app.navidrome.playback.action.") -> {
                entryPoint.navidromePlayerController().handleExternalPlaybackAction(action)
            }
            else -> {
                entryPoint.playbackController().handleExternalPlaybackAction(action)
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackActionReceiverEntryPoint {
    fun playbackController(): PlaybackController
    fun navidromePlayerController(): NavidromePlayerController
}
