package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Editor settings: zoom, line numbers, ruler, status bar, word wrap,
 * spell check, and autosave behavior.
 *
 * All preferences persist and are read by the editor service at runtime.
 */
class EditorSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_editor, rootKey)

        bindList(SettingsKeys.EDITOR_DEFAULT_ZOOM, default = "100")
        bindList(SettingsKeys.EDITOR_AUTOSAVE_INTERVAL_SEC, default = "30")

        bindSwitch(SettingsKeys.EDITOR_SHOW_LINE_NUMBERS, default = true)
        bindSwitch(SettingsKeys.EDITOR_SHOW_RULER, default = false)
        bindSwitch(SettingsKeys.EDITOR_SHOW_STATUS_BAR, default = true)
        bindSwitch(SettingsKeys.EDITOR_WORD_WRAP, default = true)
        bindSwitch(SettingsKeys.EDITOR_SPELL_CHECK, default = true)
        bindSwitch(SettingsKeys.EDITOR_AUTOSAVE_ENABLED, default = true)
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