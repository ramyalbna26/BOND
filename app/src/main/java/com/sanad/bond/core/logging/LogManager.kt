package com.sanad.bond.core.logging

import android.content.Context
import android.util.Log

object LogManager {
    private const val TAG_PREFIX = "Sanad"

    fun init(context: Context) {
        Log.i(TAG_PREFIX, "LogManager initialized")
    }

    fun bootLog(message: String) {
        Log.i("$TAG_PREFIX-Boot", message)
    }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val fullTag = "$TAG_PREFIX-$tag"
        when (level) {
            LogLevel.VERBOSE -> Log.v(fullTag, message, throwable)
            LogLevel.DEBUG -> Log.d(fullTag, message, throwable)
            LogLevel.INFO -> Log.i(fullTag, message, throwable)
            LogLevel.WARN -> Log.w(fullTag, message, throwable)
            LogLevel.ERROR -> Log.e(fullTag, message, throwable)
            LogLevel.ASSERT -> Log.wtf(fullTag, message, throwable)
        }
    }
}