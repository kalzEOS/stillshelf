package com.stillshelf.app.scrobbling

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningHttpTest {
    @Test fun transportIdentifiesApplicationAndKeepsCredentialsOutOfUrl() = runBlocking {
        var sent: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            sent = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        ListeningHttp(client).json(Request.Builder().url("https://example.invalid/1/validate-token")
            .header("Authorization", "Token private-token").build())
        assertTrue(sent!!.header("User-Agent")!!.startsWith("StillShelf/"))
        assertTrue(sent!!.header("User-Agent")!!.contains("https://github.com/kalzEOS/stillshelf/issues"))
        assertFalse(sent!!.url.toString().contains("private-token"))
    }

    @Test fun transportTimeoutIsReportedAsTimeoutWithoutLeakingExceptionContents() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { throw SocketTimeoutException("private-token") }.build()
        try {
            ListeningHttp(client).json(Request.Builder().url("https://example.invalid/1/validate-token").build())
            fail("Expected timeout")
        } catch (e: ListeningConnectionException) {
            assertTrue(e.message!!.contains("did not respond in time"))
            assertFalse(e.message!!.contains("private-token"))
        }
    }
}
