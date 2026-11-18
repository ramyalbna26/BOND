package com.sanad.bond.core.logging

import android.util.Log

// دوال مساعدة للسماح باستخدام i(), d(), w(), e() مباشرة من أي مكان
fun i(tag: String, message: String) {
    Log.i(tag, message)
}

fun d(tag: String, message: String) {
    Log.d(tag, message)
}

fun w(tag: String, message: String) {
    Log.w(tag, message)
}

fun e(tag: String, message: String, throwable: Throwable? = null) {
    Log.e(tag, message, throwable)
}

// دوال Extension لاستخدامها مباشرة على أي كائن (مثل this.i("message"))
fun Any.i(message: String) {
    Log.i(this::class.java.simpleName, message)
}

fun Any.d(message: String) {
    Log.d(this::class.java.simpleName, message)
}

fun Any.w(message: String) {
    Log.w(this::class.java.simpleName, message)
}

fun Any.e(message: String, throwable: Throwable? = null) {
    Log.e(this::class.java.simpleName, message, throwable)
}