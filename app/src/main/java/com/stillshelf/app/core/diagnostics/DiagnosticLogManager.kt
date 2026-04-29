package com.stillshelf.app.core.diagnostics

import android.content.Context
import android.os.Build
import com.stillshelf.app.BuildConfig
import com.stillshelf.app.core.datastore.SessionPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DiagnosticLogState(
    val enabled: Boolean = false,
    val hasLogs: Boolean = false,
    val sessionId: String? = null,
    val latestLogName: String? = null,
    val activeBackend: String? = null
)

@Singleton
class DiagnosticLogManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionPreferences: SessionPreferences
) {
    companion object {
        private const val LOG_DIRECTORY_NAME = "diagnostic_logs"
        private const val LOG_FILE_PREFIX = "stillshelf-diagnostic"
        private const val MAX_LOG_FILE_BYTES = 1_000_000L
        private const val MAX_LOG_FILE_COUNT = 4
        private const val UTC_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow(DiagnosticLogState())
    val state: StateFlow<DiagnosticLogState> = stateFlow.asStateFlow()
    private val previousExceptionHandler = AtomicReference<Thread.UncaughtExceptionHandler?>(null)
    @Volatile private var initialized = false
    @Volatile private var enabled = false
    @Volatile private var sessionId: String? = null
    @Volatile private var currentLogFile: File? = null
    @Volatile private var activeBackend: String? = null
    @Volatile private var logDirectoryReady = false

    init {
        scope.launch {
            sessionPreferences.state
                .map { it.diagnosticLoggingEnabled }
                .distinctUntilChanged()
                .collect { isEnabled ->
                    if (isEnabled) {
                        enableLogging()
                    } else {
                        disableLogging()
                    }
                }
        }
        scope.launch {
            sessionPreferences.state
                .map { it.selectedBackend?.storageValue }
                .distinctUntilChanged()
                .collect { backend ->
                    mutex.withLock {
                        if (activeBackend == backend) return@withLock
                        activeBackend = backend
                        if (enabled) {
                            currentLogFile = resumableLogFileLocked()
                        }
                        updateStateLocked()
                    }
                }
        }
        scope.launch {
            cleanupHeaderOnlyLogs()
        }
        scope.launch {
            refreshFileState()
        }
    }

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            previousExceptionHandler.compareAndSet(
                null,
                Thread.getDefaultUncaughtExceptionHandler()
            )
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                recordCrash(thread, throwable)
                previousExceptionHandler.get()?.uncaughtException(thread, throwable)
            }
            initialized = true
        }
    }

    fun logInfo(tag: String, message: String) {
        logEntry("INFO", tag, message, null)
    }

    fun logDebug(tag: String, message: String) {
        logEntry("DEBUG", tag, message, null)
    }

    fun logWarning(tag: String, message: String, throwable: Throwable? = null) {
        logEntry("WARN", tag, message, throwable)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        logEntry("ERROR", tag, message, throwable)
    }

    fun logPlaybackState(tag: String, state: String, detail: String? = null) {
        val message = buildString {
            append("playback_state=")
            append(state)
            detail?.takeIf { it.isNotBlank() }?.let {
                append(" detail=")
                append(it)
            }
        }
        logEntry("INFO", tag, message, null)
    }

    fun logPlaybackError(tag: String, errorType: String, throwable: Throwable? = null) {
        val message = buildString {
            append("playback_error_type=")
            append(errorType)
        }
        logEntry("ERROR", tag, message, throwable)
    }

    fun logNetworkError(tag: String, errorType: String, method: String, httpStatusCode: Int? = null, throwable: Throwable? = null) {
        val message = buildString {
            append("network_error_type=")
            append(errorType)
            append(" method=")
            append(method)
            httpStatusCode?.let {
                append(" status=")
                append(it)
            }
        }
        logEntry("WARN", tag, message, throwable)
    }

    fun logLifecycle(tag: String, message: String) {
        logEntry("INFO", tag, message, null)
    }

    fun logDiagnosticEvent(tag: String, message: String) {
        logEntry("INFO", tag, message, null)
    }

    suspend fun deleteLogs() {
        mutex.withLock {
            enabled = false
            logDirectory().listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }
            currentLogFile = null
            stateFlow.value = DiagnosticLogState(
            enabled = false,
            hasLogs = false,
            sessionId = null,
            latestLogName = null,
            activeBackend = null
        )
        }
    }

    suspend fun deleteLogFile(file: File) {
        mutex.withLock {
            val targetFile = file.absoluteFile
            val activeFile = currentLogFile?.absoluteFile
            runCatching { targetFile.delete() }
            if (enabled && activeFile == targetFile) {
                currentLogFile = null
            }
            updateStateLocked()
        }
    }

    suspend fun latestLogFile(): File? = mutex.withLock {
        logDirectory()
            .listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            ?.firstOrNull()
    }

    suspend fun logFiles(): List<File> = mutex.withLock {
        logDirectory()
            .listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .orEmpty()
    }

    private fun logEntry(level: String, tag: String, message: String, throwable: Throwable?) {
        if (!enabled) return
        appendLogEntry(level, tag, message, throwable)
    }

    private fun recordCrash(thread: Thread, throwable: Throwable) {
        if (!enabled) return
        runCatching {
            appendLogEntry(
                level = "FATAL",
                tag = thread.name.ifBlank { "uncaught" },
                message = "uncaught_exception thread=${thread.name}",
                throwable = throwable,
                synchronous = true
            )
        }
    }

    private suspend fun enableLogging() {
        val currentBackend = sessionPreferences.state.first().selectedBackend?.storageValue
        mutex.withLock {
            enabled = true
            activeBackend = currentBackend
            sessionId = UUID.randomUUID().toString().replace("-", "").take(12)
            cleanupHeaderOnlyLogsLocked()
            currentLogFile = resumableLogFileLocked()
            updateStateLocked()
        }
        logLifecycle("Settings", "diagnostic_logging_enabled")
    }

    private fun resumableLogFileLocked(): File? {
        val backendSuffix = activeBackend?.trim()?.takeIf { it.isNotBlank() } ?: "pre-backend"
        val prefix = "$LOG_FILE_PREFIX-$backendSuffix-"
        return logDirectory()
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.length() > 0L && it.length() < MAX_LOG_FILE_BYTES }
            ?.maxByOrNull { it.lastModified() }
    }

    private suspend fun disableLogging() {
        mutex.withLock {
            enabled = false
            currentLogFile = null
            updateStateLocked()
        }
    }

    private fun logDirectory(): File {
        val directory = appContext.filesDir.resolve(LOG_DIRECTORY_NAME)
        if (!logDirectoryReady) {
            synchronized(this) {
                if (!logDirectoryReady) {
                    directory.mkdirs()
                    logDirectoryReady = true
                }
            }
        }
        return directory
    }

    private fun appendLogEntry(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?,
        synchronous: Boolean = false
    ) {
        if (!enabled) return
        val safeTag = tag.trim().ifBlank { "StillShelf" }
        val safeMessage = DiagnosticLogSanitizer.sanitize(message)
        val throwableText = throwable?.let(DiagnosticLogSanitizer::sanitizeThrowable)
        val entry = buildLogEntry(level, safeTag, safeMessage, throwableText)
        val encoded = entry.toByteArray(Charsets.UTF_8)
        if (synchronous) {
            writeLogEntry(encoded, entry)
            return
        }
        scope.launch {
            mutex.withLock {
                writeLogEntry(encoded, entry)
            }
        }
    }

    private fun buildLogEntry(
        level: String,
        tag: String,
        message: String,
        throwableText: String?
    ): String {
        val timestamp = utcFormatter().format(Date())
        return buildString {
            append(timestamp)
            append(" [")
            append(level)
            append("] ")
            append(tag)
            append(": ")
            append(message)
            append(" | app=")
            append(BuildConfig.VERSION_NAME)
            append("(")
            append(BuildConfig.VERSION_CODE)
            append(")")
            activeBackend?.let {
                append(" | backend=")
                append(it)
            }
            append(" | android=")
            append(Build.VERSION.RELEASE)
            append(" | model=")
            append(Build.MODEL)
            sessionId?.let {
                append(" | session=")
                append(it)
            }
            throwableText?.let {
                append('\n')
                append(it)
            }
            append('\n')
        }
    }

    private fun writeLogEntry(encoded: ByteArray, entry: String) {
        synchronized(this) {
            val file = prepareWritableFileLocked(encoded.size) ?: return
            FileOutputStream(file, true).use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write(entry)
                    writer.flush()
                }
            }
            currentLogFile = file
            pruneOldLogsLocked()
            updateStateLocked()
        }
    }

    private fun prepareWritableFileLocked(nextEntrySizeBytes: Int): File? {
        if (!enabled) return null
        val existing = currentLogFile
        if (existing != null && existing.exists() && existing.length() + nextEntrySizeBytes <= MAX_LOG_FILE_BYTES) {
            return existing
        }
        return createFreshLogFileLocked()
    }

    private fun createFreshLogFileLocked(): File {
        val directory = logDirectory()
        val logFile = File(directory, buildLogFileName())
        val header = buildFileHeader()
        FileOutputStream(logFile, false).use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.write(header)
                writer.flush()
            }
        }
        currentLogFile = logFile
        return logFile
    }

    private fun buildFileHeader(): String {
        val timestamp = utcFormatter().format(Date())
        return buildString {
            append("# StillShelf diagnostic log\n")
            append("# started_at=")
            append(timestamp)
            append('\n')
            append("# app_version=")
            append(BuildConfig.VERSION_NAME)
            append(" (")
            append(BuildConfig.VERSION_CODE)
            append(")\n")
            append("# android_version=")
            append(Build.VERSION.RELEASE)
            append('\n')
            append("# device_model=")
            append(Build.MODEL)
            append('\n')
            activeBackend?.let {
                append("# backend=")
                append(it)
                append('\n')
            }
            sessionId?.let {
                append("# session_id=")
                append(it)
                append('\n')
            }
            append('\n')
        }
    }

    private fun buildLogFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val safeSessionId = sessionId ?: UUID.randomUUID().toString().replace("-", "").take(12)
        val backendSuffix = activeBackend?.trim()?.takeIf { it.isNotBlank() } ?: "pre-backend"
        return "$LOG_FILE_PREFIX-$backendSuffix-$timestamp-$safeSessionId.log"
    }

    private fun pruneOldLogsLocked() {
        val files = logDirectory()
            .listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        files.drop(MAX_LOG_FILE_COUNT).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private suspend fun cleanupHeaderOnlyLogs() {
        mutex.withLock {
            cleanupHeaderOnlyLogsLocked()
            updateStateLocked()
        }
    }

    private fun cleanupHeaderOnlyLogsLocked() {
        val activeFile = currentLogFile?.absoluteFile
        logDirectory().listFiles()
            ?.filter { it.isFile }
            ?.filter { file -> file.absoluteFile != activeFile }
            ?.forEach { file ->
                if (file.length() <= 0L) return@forEach
                val hasRealLogContent = runCatching {
                    file.useLines { lines ->
                        lines.any { line -> line.isNotBlank() && !line.trimStart().startsWith("#") }
                    }
                }.getOrDefault(false)
                if (!hasRealLogContent) {
                    runCatching { file.delete() }
                }
            }
    }

    private fun updateStateLocked() {
        val latest = currentLogFile?.takeIf { it.exists() && it.length() > 0L }
        stateFlow.value = DiagnosticLogState(
            enabled = enabled,
            hasLogs = latest != null || (logDirectory().listFiles()?.any { it.isFile && it.length() > 0L } == true),
            sessionId = sessionId,
            latestLogName = latest?.name,
            activeBackend = activeBackend
        )
    }

    private suspend fun refreshFileState() {
        mutex.withLock {
            updateStateLocked()
        }
    }

    private fun utcFormatter(): SimpleDateFormat {
        return SimpleDateFormat(UTC_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
