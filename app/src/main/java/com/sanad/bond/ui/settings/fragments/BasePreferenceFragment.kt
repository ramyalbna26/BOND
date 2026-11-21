package com.sanad.bond.ui.settings.fragments

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sanad.bond.R
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.core.utils.Constants

/**
 * Base class for every settings fragment.
 *
 * Provides:
 *  - Lifecycle logging (helps when debugging navigation)
 *  - Safe `find<T>(key)` helper (returns null instead of crashing)
 *  - Confirmation dialog helper
 *  - Toast helper
 *
 * FOUNDATION FIX: every settings screen MUST write to the same SharedPreferences
 * file that [com.sanad.bond.data.repository.SettingsRepository] reads from
 * (`sanad_settings`, see [Constants.PREFERENCES_NAME]). Previously each
 * PreferenceFragmentCompat wrote to the default `${packageName}_preferences`
 * file, so none of the user's settings (theme, language, autosave, backup
 * interval, …) ever took effect — SettingsRepository always returned defaults.
 *
 * Subclasses override [onCreatePreferences] and call
 * `setPreferencesFromResource(R.xml.prefs_xxx, rootKey)`. We intercept
 * `setPreferencesFromResource` (which runs after `preferenceManager` is set
 * up in `onCreate`) and pin the prefs name once for all fragments.
 */
abstract class BasePreferenceFragment : PreferenceFragmentCompat() {

    protected val logTag: String
        get() = this::class.simpleName ?: "SettingsFragment"

    override fun setPreferencesFromResource(preferencesResId: Int, key: String?) {
        // Pin the SharedPreferences file BEFORE the XML is inflated, so
        // every Preference reads/writes the same file SettingsRepository
        // reads from. This is the single fix that makes all 18 prefs_*.xml
        // screens functional.
        preferenceManager.sharedPreferencesName = Constants.PREFERENCES_NAME
        super.setPreferencesFromResource(preferencesResId, key)
    }

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