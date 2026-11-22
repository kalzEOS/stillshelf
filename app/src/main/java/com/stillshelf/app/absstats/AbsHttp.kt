package com.stillshelf.app.absstats

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Cancellation closes the socket as well as ending the UI's loading state. */
internal suspend fun OkHttpClient.absRequest(request: Request): String = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            try {
                val text = response.use {
                    check(it.isSuccessful) { "Audiobookshelf request failed (${it.code})" }
                    val source = checkNotNull(it.body).source()
                    check(!source.request(8_000_001)) { "Statistics response too large" }
                    source.readUtf8()
                }
                continuation.resume(text)
            } catch (e: Exception) { continuation.resumeWithException(e) }
        }
    })
}
