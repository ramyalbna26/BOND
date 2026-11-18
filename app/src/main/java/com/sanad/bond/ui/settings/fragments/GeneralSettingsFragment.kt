package com.sanad.bond.ui.settings.fragments

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.core.logging.LogManager

/**
 * General settings: language, theme, dynamic color, behavior toggles, data reset.
 *
 * Language & theme changes are applied IMMEDIATELY via AppCompatDelegate,
 * which will recreate the activity stack (expected behavior).
 */
class GeneralSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_general, rootKey)

        bindLanguage()
        bindTheme()
        bindDynamicColor()
        bindSimpleSwitch(SettingsKeys.GENERAL_ANIMATIONS, default = true)
        bindSimpleSwitch(SettingsKeys.GENERAL_HAPTICS, default = true)
        bindSimpleSwitch(SettingsKeys.GENERAL_SHOW_ONBOARDING, default = true)
        bindResetFirstLaunch()
    }

    // ---- Language ----
    private fun bindLanguage() {
        find<ListPreference>(SettingsKeys.GENERAL_LANGUAGE)?.apply {
            value = SettingsRepository.getString(SettingsKeys.GENERAL_LANGUAGE, "system")
            setOnPreferenceChangeListener { _, newValue ->
                val lang = newValue as String
                SettingsRepository.putString(SettingsKeys.GENERAL_LANGUAGE, lang)
                applyLanguage(lang)
                true
            }
        }
    }

    private fun applyLanguage(lang: String) {
        val locales = when (lang) {
            "ar" -> LocaleListCompat.forLanguageTags("ar")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        LogManager.i(logTag, "Applying language=$lang")
        AppCompatDelegate.setApplicationLocales(locales)
        // Activity will be recreated automatically by AppCompat
    }

    // ---- Theme ----
    private fun bindTheme() {
        find<ListPreference>(SettingsKeys.GENERAL_THEME)?.apply {
            value = SettingsRepository.getString(SettingsKeys.GENERAL_THEME, "system")
            setOnPreferenceChangeListener { _, newValue ->
                val mode = newValue as String
                SettingsRepository.putString(SettingsKeys.GENERAL_THEME, mode)
                applyTheme(mode)
                true
            }
        }
    }

    private fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        LogManager.i(logTag, "Applying theme=$mode (nightMode=$nightMode)")
        AppCompatDelegate.setDefaultNightMode(nightMode)
        // Activity will be recreated automatically
    }

    // ---- Dynamic color (API 31+) ----
    private fun bindDynamicColor() {
        val pref = find<SwitchPreferenceCompat>(SettingsKeys.GENERAL_DYNAMIC_COLOR) ?: return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            pref.isVisible = false
            return
        }

        pref.isChecked = SettingsRepository.getBoolean(SettingsKeys.GENERAL_DYNAMIC_COLOR, true)
        pref.setOnPreferenceChangeListener { _, newValue ->
            SettingsRepository.putBoolean(SettingsKeys.GENERAL_DYNAMIC_COLOR, newValue as Boolean)
            showMessage(R.string.settings_restart_required)
            true
        }
    }

    // ---- Generic boolean switch ----
    private fun bindSimpleSwitch(key: String, default: Boolean) {
        find<SwitchPreferenceCompat>(key)?.apply {
            isChecked = SettingsRepository.getBoolean(key, default)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(key, newValue as Boolean)
                true
            }
        }
    }

    // ---- Reset first-launch flag ----
    private fun bindResetFirstLaunch() {
        find<Preference>("pref_general_reset_first_launch")?.setOnPreferenceClickListener {
            showConfirm(
                titleRes = R.string.prefs_general_reset_first_launch_title,
                messageRes = R.string.prefs_general_reset_first_launch_summary
            ) {
                SettingsRepository.putBoolean(SettingsKeys.GENERAL_FIRST_LAUNCH, true)
                showMessage(R.string.prefs_general_reset_done)
            }
            true
        }
    }
}