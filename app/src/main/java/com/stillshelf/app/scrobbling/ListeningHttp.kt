package com.stillshelf.app.scrobbling

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.stillshelf.app.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ListeningApiException(val status: Int, val retryable: Boolean, message: String) : IOException(message)
class ListeningConnectionException(message: String) : IOException(message)

@Singleton
class ListeningHttp internal constructor(private val client: OkHttpClient) {
    // No logging interceptors or cross-origin redirects carrying provider credentials.
    @Inject constructor() : this(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build())
    private val listenBrainzGate = Mutex()
    private var nextListenBrainzRequest = 0L

    suspend fun text(request: Request): Pair<String, String?> {
        if (request.url.host == "api.listenbrainz.org") listenBrainzGate.withLock {
            val wait = nextListenBrainzRequest - System.nanoTime()
            if (wait > 0) delay((wait + 999_999) / 1_000_000)
            nextListenBrainzRequest = System.nanoTime() + 1_000_000_000
        }
        return suspendCancellableCoroutine { continuation ->
        val identified = request.newBuilder().header("User-Agent", "StillShelf/${BuildConfig.VERSION_NAME} (https://github.com/kalzEOS/stillshelf/issues)").build()
        val call = client.newCall(identified)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(listeningConnectionFailure(request.url.host, e))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        if (!response.isSuccessful) throw ListeningApiException(
                            response.code, response.code == 429 || response.code >= 500,
                            when (response.code) {
                                401, 403 -> "Connection authorization failed. Edit your service credentials."
                                404 -> "This server does not provide the requested listening API."
                                429 -> "The listening service is busy. Please try again later."
                                else -> "Listening service returned HTTP ${response.code}."
                            }
                        )
                        val body = response.body ?: throw ListeningConnectionException("The listening service returned an empty response. Try again later.")
                        val source = body.source()
                        source.request(2_000_001)
                        if (source.buffer.size > 2_000_000) throw ListeningConnectionException("The listening response was too large.")
                        body.string() to response.header("x-total-count")
                    }
                    if (continuation.isActive) result.fold(continuation::resume) { error ->
                        continuation.resumeWithException(if (error is IOException && error !is ListeningApiException && error !is ListeningConnectionException)
                            listeningConnectionFailure(request.url.host, error) else error)
                    }
                }
            }
        })
        }
    }

    suspend fun json(request: Request): JSONObject {
        val body = text(request).first
        return try { JSONObject(body) } catch (_: Exception) {
            throw ListeningConnectionException("The service returned no usable data. Try again later; your saved connection has not been removed.")
        }
    }
}

internal fun listeningConnectionFailure(host: String, error: IOException): ListeningConnectionException = ListeningConnectionException(
    when (error) {
        is java.net.UnknownHostException -> "Could not find $host. Check DNS and your network connection."
        is java.io.InterruptedIOException -> "$host did not respond in time. The service may be unavailable; try again later."
        is javax.net.ssl.SSLException -> "Could not establish a secure connection to $host. Check the HTTPS certificate and device clock."
        else -> "Could not reach $host. Check your connection or try again later."
    }
)

internal fun listeningUrl(base: String, path: String, query: Map<String, String> = emptyMap()): HttpUrl {
    val parsed = base.trim().trimEnd('/').toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Enter a valid http:// or https:// server URL.")
    require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) {
        "Use the server URL without embedded credentials, query, or fragment."
    }
    return parsed.newBuilder().addPathSegments(path.trim('/')).apply {
        query.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build()
}
