package com.sanad.bond.core.result

import java.io.IOException

sealed class SanadError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    data class NetworkError(
        override val message: String = "Network error occurred",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class NoInternetConnection(
        override val message: String = "No internet connection",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class TimeoutError(
        override val message: String = "Request timed out",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class DatabaseError(
        override val message: String = "Database error occurred",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class DataNotFound(
        override val message: String = "Data not found",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class FileNotFoundError(
        val path: String,
        override val message: String = "File not found: $path",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class FileReadError(
        val path: String,
        override val message: String = "Failed to read file: $path",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class FileWriteError(
        val path: String,
        override val message: String = "Failed to write file: $path",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class FilePermissionError(
        val path: String,
        override val message: String = "Permission denied for file: $path",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class DocumentParseError(
        val format: String,
        override val message: String = "Failed to parse $format document",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class DocumentExportError(
        val format: String,
        override val message: String = "Failed to export to $format",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class UnsupportedFormatError(
        val format: String,
        override val message: String = "Unsupported format: $format",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class AuthenticationError(
        override val message: String = "Authentication failed",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class BiometricError(
        override val message: String = "Biometric authentication failed",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class ValidationError(
        val field: String,
        override val message: String = "Validation error on field: $field",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class EmptyFieldError(
        val field: String,
        override val message: String = "Field cannot be empty: $field",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class UnknownError(
        override val message: String = "An unknown error occurred",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class CancellationError(
        override val message: String = "Operation was cancelled",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    data class IllegalStateError(
        override val message: String = "Illegal state",
        override val cause: Throwable? = null
    ) : SanadError(message, cause)

    fun toException(): Exception = this

    fun getUserMessage(): String = when (this) {
        is NetworkError -> "تعذر الاتصال بالخادم"
        is NoInternetConnection -> "لا يوجد اتصال بالإنترنت"
        is TimeoutError -> "انتهت مهلة الاتصال"
        is DatabaseError -> "حدث خطأ في قاعدة البيانات"
        is DataNotFound -> "البيانات غير موجودة"
        is FileNotFoundError -> "الملف غير موجود"
        is FileReadError -> "تعذرت قراءة الملف"
        is FileWriteError -> "تعذرت كتابة الملف"
        is FilePermissionError -> "لا توجد صلاحيات للوصول للملف"
        is DocumentParseError -> "تعذر قراءة المستند"
        is DocumentExportError -> "تعذر تصدير المستند"
        is UnsupportedFormatError -> "صيغة غير مدعومة"
        is AuthenticationError -> "فشل المصادقة"
        is BiometricError -> "فشل التحقق البيومتري"
        is ValidationError -> "بيانات غير صحيحة"
        is EmptyFieldError -> "الحقل لا يمكن أن يكون فارغاً"
        is UnknownError -> "حدث خطأ غير متوقع"
        is CancellationError -> "تم إلغاء العملية"
        is IllegalStateError -> "حالة غير صحيحة"
    }

    companion object {
        fun fromException(throwable: Throwable): SanadError {
            return when (throwable) {
                is SanadError -> throwable
                is IOException -> {
                    if (throwable.message?.contains("timeout", ignoreCase = true) == true) {
                        TimeoutError(cause = throwable)
                    } else {
                        NetworkError(cause = throwable)
                    }
                }
                is kotlinx.coroutines.CancellationException -> CancellationError(cause = throwable)
                is IllegalStateException -> IllegalStateError(cause = throwable)
                is IllegalArgumentException -> ValidationError(field = "unknown", cause = throwable)
                else -> UnknownError(message = throwable.message ?: "Unknown error", cause = throwable)
            }
        }
    }
}