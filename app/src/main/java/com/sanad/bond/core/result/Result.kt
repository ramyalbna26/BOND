package com.sanad.bond.core.result

/**
 * Sealed class representing the result of an operation.
 * Can be Success, Error, or Loading.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: SanadError) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun errorOrNull(): SanadError? = when (this) {
        is Error -> error
        else -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw error.toException()
        is Loading -> throw IllegalStateException("Result is still loading")
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(error)
        is Loading -> Loading
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> Error(error)
        is Loading -> Loading
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (SanadError) -> Unit): Result<T> {
        if (this is Error) action(error)
        return this
    }

    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(error: SanadError): Result<Nothing> = Error(error)
        fun loading(): Result<Nothing> = Loading
    }
}

/**
 * Extension function to convert a try-catch block to a Result
 */
inline fun <T> runCatchingResult(block: () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(SanadError.fromException(e))
    }
}

/**
 * Extension function to convert a suspend function to a Result
 */
suspend inline fun <T> suspendRunCatchingResult(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(SanadError.fromException(e))
    }
}