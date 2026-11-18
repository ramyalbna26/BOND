package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Tools settings: OCR language, TTS speed, STT language, background
 * removal, regex search, case sensitivity.
 *
 * All preferences persist and are read by services in Level 5.
 */
class ToolsSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_tools, rootKey)

        bindList(SettingsKeys.TOOLS_OCR_LANGUAGE, default = "auto")
        bindList(SettingsKeys.TOOLS_TTS_SPEED, default = "1.0")
        bindList(SettingsKeys.TOOLS_STT_LANGUAGE, default = "system")

        bindSwitch(SettingsKeys.TOOLS_OCR_AUTO_DETECT, default = true)
        bindSwitch(SettingsKeys.TOOLS_BACKGROUND_REMOVAL, default = false)
        bindSwitch(SettingsKeys.TOOLS_REGEX_SEARCH, default = false)
        bindSwitch(SettingsKeys.TOOLS_CASE_SENSITIVE_SEARCH, default = false)
    }

    private fun bindSwitch(key: String, default: Boolean) {
        find<SwitchPreferenceCompat>(key)?.apply {
            isChecked = SettingsRepository.getBoolean(key, default)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(key, newValue as Boolean)
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