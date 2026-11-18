package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Default fonts for Arabic / Latin text + default size + advanced options.
 * Fonts are loaded from assets/fonts/ at render time (Level 5 service layer).
 */
class FontsSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_fonts, rootKey)

        bindList(SettingsKeys.FONTS_DEFAULT_ARABIC, "cairo")
        bindList(SettingsKeys.FONTS_DEFAULT_LATIN, "roboto")
        bindList(SettingsKeys.FONTS_DEFAULT_SIZE_SP, "16")
        bindList(SettingsKeys.FONTS_FALLBACK, "roboto")

        find<SwitchPreferenceCompat>(SettingsKeys.FONTS_LOAD_SYSTEM)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.FONTS_LOAD_SYSTEM, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.FONTS_LOAD_SYSTEM, newValue as Boolean)
                true
            }
        }
    }

    private fun bindList(key: String, default: String) {
        find<ListPreference>(key)?.apply {
            value = SettingsRepository.getString(key, default)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(key, newValue as String)
                true
            }
        }
    }
}