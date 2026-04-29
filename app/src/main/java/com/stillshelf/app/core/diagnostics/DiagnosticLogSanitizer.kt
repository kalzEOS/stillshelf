package com.stillshelf.app.core.diagnostics

import java.security.MessageDigest
import java.util.Locale

object DiagnosticLogSanitizer {
    private val emailRegex = Regex("(?i)(?<![\\w@.+-])[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+")
    private val urlRegex = Regex("(?i)\\bhttps?://[^\\s<>()\"']+")
    private val ipAddressRegex = Regex("(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)")
    private val ipv6AddressRegex = Regex(
        "(?i)(?<![\\w:])(?:\\[(?:[0-9a-f]{0,4}(?::[0-9a-f]{0,4}){2,}(?:%[\\w.~-]+)?)\\]|(?:[0-9a-f]{0,4}(?::[0-9a-f]{0,4}){2,}(?:%[\\w.~-]+)?))(?![\\w:])"
    )
    private val absolutePathRegex = Regex("(?<!\\w)(?:/[\\w.-]+){2,}")
    private val windowsPathRegex = Regex("(?i)\\b[A-Z]:\\\\(?:[^\\\\\\r\\n]+\\\\)*[^\\\\\\r\\n]+")
    private val unknownHostRegex = Regex(
        "(?i)(Unable to resolve host(?:name)?\\s+|UnknownHostException:\\s+)(\"?)([^\"\\s:]+)(\"?)"
    )
    private val tokenValueRegex = Regex(
        "(?i)\\b(?:authorization|cookie|set-cookie|api[_-]?key|access[_-]?token|refresh[_-]?token|id[_-]?token|token|password|passwd|secret|session|bearer)\\b\\s*[:=]\\s*([^\\s,;]+)"
    )
    private val titleValueRegex = Regex(
        "(?i)\\b(?:title|book[_-]?title|song[_-]?title|album[_-]?title|track[_-]?name|narrator[_-]?name|series[_-]?name|genre[_-]?name|author[_-]?name|media[_-]?name|library[_-]?name)\\b\\s*[:=]\\s*([^\\r\\n,;]+)"
    )
    private val userIdValueRegex = Regex(
        "(?i)\\b(?:user[_-]?id|account[_-]?id|profile[_-]?id|username|user[_-]?name|user[_-]?login|display[_-]?name|full[_-]?name)\\b\\s*[:=]\\s*([^\\s,;]+)"
    )
    private val urlKeyRegex = Regex(
        "(?i)\\b((?:server[_-]?url|base[_-]?url|endpoint|server[_-]?address|server[_-]?host|host|url))\\b\\s*[:=]\\s*([^\\s,;]+)"
    )
    private val headerValueRegex = Regex(
        "(?im)^\\s*(authorization|cookie|set-cookie|x-api-key|x-auth-token|x-session-token)\\s*:\\s*([^\\r\\n]+)$"
    )

    fun sanitize(value: String): String {
        var sanitized = value
        sanitized = headerValueRegex.replace(sanitized) { match ->
            val headerName = match.groupValues[1]
            val marker = when (headerName.lowercase(Locale.US)) {
                "authorization", "cookie", "set-cookie" -> "[REDACTED_TOKEN]"
                else -> "[REDACTED_HEADER]"
            }
            "$headerName: $marker"
        }
        sanitized = tokenValueRegex.replace(sanitized) { match ->
            "${match.groupValues[0].substringBefore(':').substringBefore('=').trim()}=[REDACTED_TOKEN]"
        }
        sanitized = urlKeyRegex.replace(sanitized) { match ->
            val key = match.groupValues[1]
            val redaction = if (key.equals("url", ignoreCase = true)) {
                "[REDACTED_URL]"
            } else {
                "[REDACTED_SERVER]"
            }
            "$key=$redaction"
        }
        sanitized = titleValueRegex.replace(sanitized) { match ->
            "${match.groupValues[0].substringBefore(':').substringBefore('=').trim()}=[REDACTED_TITLE]"
        }
        sanitized = userIdValueRegex.replace(sanitized) { match ->
            val rawValue = match.groupValues[1]
            val hashedValue = hashForLogging(rawValue)
            "${match.groupValues[0].substringBefore(':').substringBefore('=').trim()}=[HASH:$hashedValue]"
        }
        sanitized = urlRegex.replace(sanitized, "[REDACTED_URL]")
        sanitized = emailRegex.replace(sanitized, "[REDACTED_EMAIL]")
        sanitized = ipAddressRegex.replace(sanitized, "[REDACTED_IP]")
        sanitized = ipv6AddressRegex.replace(sanitized, "[REDACTED_IP]")
        sanitized = unknownHostRegex.replace(sanitized) {
            "${it.groupValues[1]}${it.groupValues[2]}[REDACTED_HOST]${it.groupValues[4]}"
        }
        sanitized = windowsPathRegex.replace(sanitized, "[REDACTED_PATH]")
        sanitized = absolutePathRegex.replace(sanitized, "[REDACTED_PATH]")
        return sanitized
    }

    fun sanitizeThrowable(throwable: Throwable): String {
        val rawStackTrace = buildString {
            append(throwable::class.java.name)
            throwable.message?.takeIf { it.isNotBlank() }?.let { message ->
                append(": ")
                append(message)
            }
            append('\n')
            append(throwable.stackTraceToString())
        }
        return sanitize(rawStackTrace)
    }

    private fun hashForLogging(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.trim().toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
