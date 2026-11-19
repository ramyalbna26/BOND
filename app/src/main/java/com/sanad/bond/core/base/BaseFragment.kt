package com.sanad.bond.core.base

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
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
 * Base Fragment class that provides common functionality
 * for all fragments in the application.
 */
abstract class BaseFragment : Fragment() {

    @get:JvmName("logTag")
protected val tag: String get() = this::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.log(LogLevel.DEBUG, tag, "onCreate")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogManager.log(LogLevel.DEBUG, tag, "onViewCreated")
        setupObservers()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        LogManager.log(LogLevel.DEBUG, tag, "onResume")
    }

    override fun onPause() {
        super.onPause()
        LogManager.log(LogLevel.DEBUG, tag, "onPause")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LogManager.log(LogLevel.DEBUG, tag, "onDestroyView")
    }

    /**
     * Override this to set up observers for LiveData/Flow
     */
    protected open fun setupObservers() {}

    /**
     * Override this to set up click listeners and other UI interactions
     */
    protected open fun setupListeners() {}

    /**
     * Show a toast message
     */
    protected fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(requireContext(), message, duration).show()
    }

    /**
     * Show an error toast from a SanadError
     */
    protected fun showErrorToast(error: SanadError) {
        showToast(error.getUserMessage(), Toast.LENGTH_LONG)
    }

    /**
     * Collect a Flow safely within the fragment's view lifecycle
     */
    protected fun <T> Flow<T>.collectSafe(
        state: Lifecycle.State = Lifecycle.State.STARTED,
        action: suspend (T) -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(state) {
                collectLatest(action)
            }
        }
    }

    /**
     * Log an error with the fragment's tag
     */
    protected fun logError(message: String, throwable: Throwable? = null) {
        LogManager.log(LogLevel.ERROR, tag, message, throwable)
    }

    /**
     * Log a debug message with the fragment's tag
     */
    protected fun logDebug(message: String) {
        LogManager.log(LogLevel.DEBUG, tag, message)
    }
}