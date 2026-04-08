package com.stillshelf.app.data.api

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromeApiTextDecodingTest {

    @Test
    fun decodeNavidromeResponseBody_prefersValidUtf8OverBadDeclaredCharset() {
        val payload = """{"subsonic-response":{"status":"ok","album":{"name":"مع الحب"}}}"""
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)

        val decoded = decodeNavidromeResponseBody(
            bytes = bytes,
            declaredCharset = Charset.forName("windows-1252")
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun decodeNavidromeResponseBody_fallsBackWhenUtf8IsInvalid() {
        val payload = """{"subsonic-response":{"status":"ok","album":{"name":"Hell’s Hits"}}}"""
        val bytes = payload.toByteArray(Charset.forName("windows-1252"))

        val decoded = decodeNavidromeResponseBody(
            bytes = bytes,
            declaredCharset = Charset.forName("windows-1252")
        )

        assertEquals(payload, decoded)
    }
}
