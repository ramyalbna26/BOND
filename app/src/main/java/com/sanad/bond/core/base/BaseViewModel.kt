package com.sanad.bond.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanad.bond.core.logging.LogLevel
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.core.result.Result
import com.sanad.bond.core.result.SanadError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel class that provides common functionality
 * for all ViewModels in the application.
 */
abstract class BaseViewModel : ViewModel() {

    protected val tag: String get() = this::class.java.simpleName

    // Common UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // One-time events
    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /**
     * Execute a suspend function with loading state and error handling
     */
    protected fun <T> executeWithResult(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        showLoading: Boolean = true,
        block: suspend () -> Result<T>,
        onSuccess: (T) -> Unit = {},
        onError: (SanadError) -> Unit = {}
    ) {
        viewModelScope.launch(dispatcher) {
            if (showLoading) _isLoading.value = true

            val result = try {
                block()
            } catch (e: Exception) {
                Result.Error(SanadError.fromException(e))
            }

            if (showLoading) _isLoading.value = false

            when (result) {
                is Result.Success -> {
                    onSuccess(result.data)
                }
                is Result.Error -> {
                    logError("Operation failed: ${result.error.message}", result.error)
                    _errorMessage.value = result.error.getUserMessage()
                    onError(result.error)
                }
                is Result.Loading -> {
                    // Should not happen here
                }
            }
        }
    }

    /**
     * Execute a suspend function with simple error handling
     */
    protected fun execute(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        showLoading: Boolean = false,
        block: suspend () -> Unit,
        onError: (SanadError) -> Unit = {}
    ) {
        viewModelScope.launch(dispatcher) {
            if (showLoading) _isLoading.value = true

            try {
                block()
            } catch (e: Exception) {
                val error = SanadError.fromException(e)
                logError("Operation failed: ${error.message}", error)
                _errorMessage.value = error.getUserMessage()
                onError(error)
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }
    }

    /**
     * Send a one-time UI event
     */
    protected fun sendEvent(event: UiEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    /**
     * Clear the error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Log an error with the ViewModel's tag
     */
    protected fun logError(message: String, throwable: Throwable? = null) {
        LogManager.log(LogLevel.ERROR, tag, message, throwable)
    }

    /**
     * Log a debug message with the ViewModel's tag
     */
    protected fun logDebug(message: String) {
        LogManager.log(LogLevel.DEBUG, tag, message)
    }
}

/**
 * Sealed class for one-time UI events
 */
sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class ShowError(val error: SanadError) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    data object NavigateBack : UiEvent()
    data class NavigateWithData(val route: String, val data: Any?) : UiEvent()
}