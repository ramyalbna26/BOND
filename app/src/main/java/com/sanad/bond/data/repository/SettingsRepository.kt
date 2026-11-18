package com.sanad.bond.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sanad.bond.core.logging.LogManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Singleton wrapper over SharedPreferences.
 *
 * - Typed getters/setters (synchronous — used in UI setup)
 * - Flow-based observation (reactive — used with collect)
 * - Feature gates for LIB_* (isFeatureEnabledFor)
 */
object SettingsRepository {

    private const val TAG = "SettingsRepository"
    private const val PREFS_NAME = "sanad_settings"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        LogManager.i(TAG, "SettingsRepository initialized. prefs=$PREFS_NAME")
    }

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("SettingsRepository.init(context) must be called first")

    // ---- Typed getters ----
    fun getBoolean(key: String, default: Boolean = false): Boolean =
        requirePrefs().getBoolean(key, default)

    fun getString(key: String, default: String = ""): String =
        requirePrefs().getString(key, default) ?: default

    fun getInt(key: String, default: Int = 0): Int =
        requirePrefs().getInt(key, default)

    fun getLong(key: String, default: Long = 0L): Long =
        requirePrefs().getLong(key, default)

    fun getFloat(key: String, default: Float = 0f): Float =
        requirePrefs().getFloat(key, default)

    // ---- Typed setters ----
    fun putBoolean(key: String, value: Boolean) {
        requirePrefs().edit().putBoolean(key, value).apply()
    }

    fun putString(key: String, value: String) {
        requirePrefs().edit().putString(key, value).apply()
    }

    fun putInt(key: String, value: Int) {
        requirePrefs().edit().putInt(key, value).apply()
    }

    fun putLong(key: String, value: Long) {
        requirePrefs().edit().putLong(key, value).apply()
    }

    fun putFloat(key: String, value: Float) {
        requirePrefs().edit().putFloat(key, value).apply()
    }

    fun remove(key: String) {
        requirePrefs().edit().remove(key).apply()
    }

    fun clearAll() {
        requirePrefs().edit().clear().apply()
        LogManager.i(TAG, "All settings cleared")
    }

    fun getAllKeys(): Set<String> = requirePrefs().all.keys.toSet()

    // ---- Reactive observation ----
    fun observeBoolean(key: String, default: Boolean = false): Flow<Boolean> =
        observe(key) { getBoolean(key, default) }

    fun observeString(key: String, default: String = ""): Flow<String> =
        observe(key) { getString(key, default) }

    fun observeInt(key: String, default: Int = 0): Flow<Int> =
        observe(key) { getInt(key, default) }

    fun observeLong(key: String, default: Long = 0L): Flow<Long> =
        observe(key) { getLong(key, default) }

    private fun <T> observe(key: String, reader: () -> T): Flow<T> = callbackFlow {
        trySend(reader())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key || changedKey == null) {
                trySend(reader())
            }
        }
        requirePrefs().registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            requirePrefs().unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    // ---- Logging shortcuts ----
    fun isLoggingEnabled(): Boolean =
        getBoolean(com.sanad.bond.data.model.SettingsKeys.LOGGING_ENABLED, true)

    fun setLoggingEnabled(value: Boolean) {
        putBoolean(com.sanad.bond.data.model.SettingsKeys.LOGGING_ENABLED, value)
    }

    fun isFileLoggingEnabled(): Boolean =
        getBoolean(com.sanad.bond.data.model.SettingsKeys.LOGGING_FILE_ENABLED, true)

    fun setFileLoggingEnabled(value: Boolean) {
        putBoolean(com.sanad.bond.data.model.SettingsKeys.LOGGING_FILE_ENABLED, value)
    }

    fun getLoggingMinLevel(): String =
        getString(com.sanad.bond.data.model.SettingsKeys.LOGGING_MIN_LEVEL, "VERBOSE")

    fun setLoggingMinLevel(level: String) {
        putString(com.sanad.bond.data.model.SettingsKeys.LOGGING_MIN_LEVEL, level)
    }

    // ---- Feature gates ----
    fun isFeatureEnabledFor(mimeType: String): Boolean {
        val key = when {
            mimeType.contains("wordprocessingml", ignoreCase = true)
                || mimeType == "application/msword"
                -> com.sanad.bond.data.model.SettingsKeys.LIB_WORD_ENABLED
            mimeType == "application/pdf"
                -> com.sanad.bond.data.model.SettingsKeys.LIB_PDF_ENABLED
            mimeType.startsWith("image/")
                -> com.sanad.bond.data.model.SettingsKeys.LIB_IMAGE_ENABLED
            mimeType.startsWith("text/")
                -> com.sanad.bond.data.model.SettingsKeys.LIB_TEXT_ENABLED
            else -> return true
        }
        return getBoolean(key, true)
    }
}