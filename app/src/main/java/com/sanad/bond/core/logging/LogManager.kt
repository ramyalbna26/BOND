package com.sanad.bond.core.logging

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.CopyOnWriteArrayList

object LogManager {
    private const val TAG = "LogManager"
    private const val LOG_FILE_NAME = "sanad_log.txt"
    private const val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024

    private var context: Context? = null
    private var logFile: File? = null
    private var fileWriter: BufferedWriter? = null
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = CopyOnWriteArrayList<LogListener>()
    private val entries = CopyOnWriteArrayList<LogEntry>()

    @Volatile private var loggingEnabled = true
    @Volatile private var fileLoggingEnabled = true
    @Volatile private var minLevel = LogLevel.VERBOSE

    interface LogListener {
        fun onLog(entry: LogEntry)
    }

    fun init(context: Context) {
        this.context = context.applicationContext
        try {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            if (fileLoggingEnabled && logFile?.exists() == true) {
                fileWriter = BufferedWriter(FileWriter(logFile, true))
            }
            bootLog("LogManager initialized")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to init LogManager", t)
        }
    }

    fun bootLog(message: String) { android.util.Log.i(TAG, message) }

    fun d(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.DEBUG, tag, message, throwable)
    fun i(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.INFO, tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)
    fun v(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.VERBOSE, tag, message, throwable)

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (!loggingEnabled || level.priority < minLevel.priority) return
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, throwable)
        entries.add(entry)
        listeners.forEach { l -> try { l.onLog(entry) } catch (_: Throwable) {} }
        if (fileLoggingEnabled && fileWriter != null) {
            scope.launch {
                mutex.withLock {
                    try {
                        fileWriter?.write(entry.format()); fileWriter?.newLine(); fileWriter?.flush()
                        if ((logFile?.length() ?: 0) > MAX_LOG_SIZE_BYTES) rotateLog()
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun rotateLog() {
        try {
            fileWriter?.close(); logFile?.delete(); logFile?.createNewFile()
            fileWriter = BufferedWriter(FileWriter(logFile, true))
        } catch (_: Throwable) {}
    }

    fun addListener(listener: LogListener) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: LogListener) { listeners.remove(listener) }
    fun snapshot(): List<LogEntry> = entries.toList()
    fun clear() {
        entries.clear()
        try {
            fileWriter?.close(); logFile?.delete(); logFile?.createNewFile()
            fileWriter = BufferedWriter(FileWriter(logFile, true))
        } catch (_: Throwable) {}
    }

    fun isLoggingEnabled(): Boolean = loggingEnabled
    fun setLoggingEnabled(enabled: Boolean) { loggingEnabled = enabled }
    fun isFileLoggingEnabled(): Boolean = fileLoggingEnabled
    fun setFileLoggingEnabled(enabled: Boolean) {
        fileLoggingEnabled = enabled
        if (enabled && fileWriter == null && logFile != null) {
            try { fileWriter = BufferedWriter(FileWriter(logFile, true)) } catch (_: Throwable) {}
        } else if (!enabled) {
            try { fileWriter?.close(); fileWriter = null } catch (_: Throwable) {}
        }
    }

    fun getMinLevel(): LogLevel = minLevel
    fun setMinLevel(level: LogLevel) { minLevel = level }
    fun isLogFileAvailable(): Boolean = logFile?.exists() == true
    fun sessionEntryCount(): Int = entries.size
    fun logFileSizeBytes(): Long = logFile?.length() ?: 0L
    fun logFilePath(): String = logFile?.absolutePath ?: "N/A"

    fun shareableLogUri(): Uri? {
        return try {
            val ctx = context ?: return null
            val file = logFile ?: return null
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        } catch (_: Throwable) { null }
    }
}