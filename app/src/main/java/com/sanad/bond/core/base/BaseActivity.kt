package com.sanad.bond.core.base

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sanad.bond.core.logging.LogLevel
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.core.result.SanadError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Base Activity class that provides common functionality
 * for all activities in the application.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected val tag: String get() = this::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.log(LogLevel.DEBUG, tag, "onCreate")
    }

    override fun onStart() {
        super.onStart()
        LogManager.log(LogLevel.DEBUG, tag, "onStart")
    }

    override fun onResume() {
        super.onResume()
        LogManager.log(LogLevel.DEBUG, tag, "onResume")
    }

    override fun onPause() {
        super.onPause()
        LogManager.log(LogLevel.DEBUG, tag, "onPause")
    }

    override fun onStop() {
        super.onStop()
        LogManager.log(LogLevel.DEBUG, tag, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        LogManager.log(LogLevel.DEBUG, tag, "onDestroy")
    }

    /**
     * Show a toast message
     */
    protected fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    /**
     * Show an error toast from a SanadError
     */
    protected fun showErrorToast(error: SanadError) {
        showToast(error.getUserMessage(), Toast.LENGTH_LONG)
    }

    /**
     * Collect a Flow safely within the activity's lifecycle
     */
    protected fun <T> Flow<T>.collectSafe(
        state: Lifecycle.State = Lifecycle.State.STARTED,
        action: suspend (T) -> Unit
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(state) {
                collectLatest(action)
            }
        }
    }

    /**
     * Log an error with the activity's tag
     */
    protected fun logError(message: String, throwable: Throwable? = null) {
        LogManager.log(LogLevel.ERROR, tag, message, throwable)
    }

    /**
     * Log a debug message with the activity's tag
     */
    protected fun logDebug(message: String) {
        LogManager.log(LogLevel.DEBUG, tag, message)
    }
}