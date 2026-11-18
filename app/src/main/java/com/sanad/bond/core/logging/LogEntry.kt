package com.sanad.bond.core.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    fun format(): String {
        val time = FORMATTER.get()!!.format(Date(timestamp))
        val base = "$time ${level.letter}/$tag: $message"
        return if (throwable != null) "$base\n${Log.getStackTraceString(throwable)}" else base
    }

    companion object {
        private val FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }
    }
}