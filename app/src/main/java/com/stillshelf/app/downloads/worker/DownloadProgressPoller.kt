package com.stillshelf.app.downloads.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadProgressPoller(
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 1000L,
    private val onTick: suspend () -> Unit
) {
    private var pollingJob: Job? = null

    fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                onTick()
                delay(pollIntervalMs)
            }
        }
    }
}
