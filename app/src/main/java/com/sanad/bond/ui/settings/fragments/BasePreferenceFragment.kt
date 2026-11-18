package com.sanad.bond.ui.settings.fragments

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sanad.bond.R
import com.sanad.bond.core.logging.LogManager

/**
 * Base class for every settings fragment.
 *
 * Provides:
 *  - Lifecycle logging (helps when debugging navigation)
 *  - Safe `find<T>(key)` helper (returns null instead of crashing)
 *  - Confirmation dialog helper
 *  - Toast helper
 *
 * Subclasses override [onCreatePreferences] and call
 * `setPreferencesFromResource(R.xml.prefs_xxx, rootKey)`.
 */
abstract class BasePreferenceFragment : PreferenceFragmentCompat() {

    protected val logTag: String
        get() = this::class.simpleName ?: "SettingsFragment"

    override fun onResume() {
        super.onResume()
        LogManager.d(logTag, "onResume")
    }

    override fun onPause() {
        LogManager.d(logTag, "onPause")
        super.onPause()
    }

    /** Type-safe lookup — null instead of exception if the key is missing. */
    protected inline fun <reified T : Preference> find(key: String): T? =
        try {
            findPreference<T>(key)
        } catch (_: Exception) {
            null
        }

    protected fun showMessage(@StringRes messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    protected fun showMessage(text: CharSequence) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    protected fun showConfirm(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        onConfirm: () -> Unit
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> onConfirm() }
            .show()
    }
}