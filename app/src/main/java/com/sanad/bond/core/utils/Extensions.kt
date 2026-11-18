package com.sanad.bond.core.utils

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.sanad.bond.core.result.Result
import com.sanad.bond.core.result.SanadError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extension functions used throughout the application
 */

// Context Extensions
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.showError(error: SanadError) {
    showToast(error.getUserMessage(), Toast.LENGTH_LONG)
}

// Fragment Extensions
fun Fragment.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    requireContext().showToast(message, duration)
}

fun Fragment.showError(error: SanadError) {
    requireContext().showError(error)
}

// String Extensions
fun String.toFormattedDate(pattern: String = "yyyy-MM-dd HH:mm"): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        val outputFormat = SimpleDateFormat(pattern, Locale.getDefault())
        val date = inputFormat.parse(this)
        date?.let { outputFormat.format(it) } ?: this
    } catch (e: Exception) {
        this
    }
}

fun String.toFileSize(): String {
    return try {
        val size = this.toLong()
        when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    } catch (e: Exception) {
        this
    }
}

fun String.getFileExtension(): String {
    val lastDot = lastIndexOf('.')
    return if (lastDot >= 0) substring(lastDot) else ""
}

fun String.removeFileExtension(): String {
    val lastDot = lastIndexOf('.')
    return if (lastDot >= 0) substring(0, lastDot) else this
}

// Date Extensions
fun Date.toFormattedString(pattern: String = "yyyy-MM-dd HH:mm"): String {
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(this)
}

fun Date.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - time

    return when {
        diff < 60_000 -> "الآن"
        diff < 3600_000 -> "${diff / 60_000} دقيقة"
        diff < 86400_000 -> "${diff / 3600_000} ساعة"
        diff < 604800_000 -> "${diff / 86400_000} يوم"
        diff < 2592000_000 -> "${diff / 604800_000} أسبوع"
        else -> toFormattedString("yyyy-MM-dd")
    }
}

// Flow Extensions
fun <T> Flow<T>.asResult(): Flow<Result<T>> {
    return this
        .map<T, Result<T>> { Result.Success(it) }
        .onStart { emit(Result.Loading) }
        .catch { emit(Result.Error(SanadError.fromException(it))) }
}

// Collection Extensions
fun <T> List<T>.safeGet(index: Int): T? {
    return if (index in indices) get(index) else null
}

// Boolean Extensions
fun Boolean.toInt(): Int = if (this) 1 else 0
fun Int.toBoolean(): Boolean = this != 0