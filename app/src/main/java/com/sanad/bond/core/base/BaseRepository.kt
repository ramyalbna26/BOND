package com.sanad.bond.core.base

import com.sanad.bond.core.logging.LogLevel
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.core.result.Result
import com.sanad.bond.core.result.SanadError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Base Repository class that provides common functionality
 * for all repositories in the application.
 */
abstract class BaseRepository {

    protected val tag: String get() = this::class.java.simpleName

    /**
     * Execute a suspend function and wrap the result in a Result object
     */
    protected suspend fun <T> safeCall(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> T
    ): Result<T> {
        return withContext(dispatcher) {
            try {
                Result.Success(block())
            } catch (e: Exception) {
                logError("Repository call failed: ${e.message}", e)
                Result.Error(SanadError.fromException(e))
            }
        }
    }

    /**
     * Execute a database operation safely
     */
    protected suspend fun <T> safeDbCall(
        operation: String,
        block: suspend () -> T
    ): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                logDebug("Starting DB operation: $operation")
                val result = block()
                logDebug("DB operation completed: $operation")
                Result.Success(result)
            } catch (e: Exception) {
                logError("DB operation failed: $operation - ${e.message}", e)
                Result.Error(SanadError.DatabaseError(
                    message = "Database operation failed: $operation",
                    cause = e
                ))
            }
        }
    }

    /**
     * Execute a file operation safely
     */
    protected suspend fun <T> safeFileCall(
        operation: String,
        block: suspend () -> T
    ): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                logDebug("Starting file operation: $operation")
                val result = block()
                logDebug("File operation completed: $operation")
                Result.Success(result)
            } catch (e: Exception) {
                logError("File operation failed: $operation - ${e.message}", e)
                Result.Error(SanadError.fromException(e))
            }
        }
    }

    /**
     * Log an error with the repository's tag
     */
    protected fun logError(message: String, throwable: Throwable? = null) {
        LogManager.log(LogLevel.ERROR, tag, message, throwable)
    }

    /**
     * Log a debug message with the repository's tag
     */
    protected fun logDebug(message: String) {
        LogManager.log(LogLevel.DEBUG, tag, message)
    }

    /**
     * Log an info message with the repository's tag
     */
    protected fun logInfo(message: String) {
        LogManager.log(LogLevel.INFO, tag, message)
    }
}