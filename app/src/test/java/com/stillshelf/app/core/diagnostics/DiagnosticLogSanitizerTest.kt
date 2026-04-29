package com.stillshelf.app.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogSanitizerTest {
    @Test
    fun sanitizesSensitiveValuesInLogText() {
        val raw = """
            url=https://example.com/api/v1/books
            server_url=http://10.0.0.5:8080
            Authorization: Bearer abc123
            Cookie: session=xyz789
            email=user@example.com
            ip=192.168.0.8
            ipv6=2001:db8::1
            resolver=Unable to resolve host invidious.lan: No address associated with hostname
            resolver2=Unable to resolve host nas: No address associated with hostname
            UnknownHostException: example.org
            path=/data/user/0/com.stillshelf.app/files/cache.log
            user_id=user-123
            media title=The Hobbit
        """.trimIndent()

        val sanitized = DiagnosticLogSanitizer.sanitize(raw)

        assertFalse(sanitized.contains("https://example.com"))
        assertFalse(sanitized.contains("10.0.0.5"))
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("xyz789"))
        assertFalse(sanitized.contains("user@example.com"))
        assertFalse(sanitized.contains("192.168.0.8"))
        assertFalse(sanitized.contains("2001:db8::1"))
        assertFalse(sanitized.contains("invidious.lan"))
        assertFalse(sanitized.contains("nas"))
        assertFalse(sanitized.contains("example.org"))
        assertFalse(sanitized.contains("/data/user/0/com.stillshelf.app/files/cache.log"))
        assertFalse(sanitized.contains("user-123"))
        assertFalse(sanitized.contains("The Hobbit"))
        assertTrue(sanitized.contains("[REDACTED_URL]"))
        assertTrue(sanitized.contains("[REDACTED_SERVER]"))
        assertTrue(sanitized.contains("[REDACTED_TOKEN]"))
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"))
        assertTrue(sanitized.contains("[REDACTED_IP]"))
        assertTrue(sanitized.contains("[REDACTED_PATH]"))
        assertTrue(sanitized.contains("[REDACTED_HOST]"))
        assertTrue(sanitized.contains("[HASH:"))
        assertTrue(sanitized.contains("[REDACTED_TITLE]"))
    }

    @Test
    fun sanitizesThrowableMessagesAndStackTraces() {
        val throwable = IllegalStateException("Request failed for https://example.com/api?token=abc123")

        val sanitized = DiagnosticLogSanitizer.sanitizeThrowable(throwable)

        assertFalse(sanitized.contains("https://example.com"))
        assertFalse(sanitized.contains("abc123"))
        assertTrue(sanitized.contains("[REDACTED_URL]"))
    }
}
