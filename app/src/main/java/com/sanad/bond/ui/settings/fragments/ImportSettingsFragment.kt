package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Import settings: encodings, size limits, keep-original.
 * Actual file import happens in services/import (Level 5).
 */
class ImportSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_import, rootKey)

        // Static info — formats supported by the current build
        find<Preference>("pref_import_supported_formats")?.summary =
            getString(R.string.prefs_import_supported_value)

        bindAutoEncoding()
        bindDefaultEncoding()
        bindMaxSize()
        bindKeepOriginal()
    }

    private fun bindAutoEncoding() {
        find<SwitchPreferenceCompat>(SettingsKeys.IMPORT_AUTO_DETECT_ENCODING)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.IMPORT_AUTO_DETECT_ENCODING, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.IMPORT_AUTO_DETECT_ENCODING, newValue as Boolean)
                true
            }
        }
    }

    private fun bindDefaultEncoding() {
        find<ListPreference>(SettingsKeys.IMPORT_DEFAULT_ENCODING)?.apply {
            value = SettingsRepository.getString(SettingsKeys.IMPORT_DEFAULT_ENCODING, "UTF-8")
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(SettingsKeys.IMPORT_DEFAULT_ENCODING, newValue as String)
                true
            }
        }
    }

    private fun bindMaxSize() {
        find<ListPreference>(SettingsKeys.IMPORT_MAX_FILE_SIZE_MB)?.apply {
            value = SettingsRepository.getString(SettingsKeys.IMPORT_MAX_FILE_SIZE_MB, "50")
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(SettingsKeys.IMPORT_MAX_FILE_SIZE_MB, newValue as String)
                true
            }
        }
    }

    private fun bindKeepOriginal() {
        find<SwitchPreferenceCompat>(SettingsKeys.IMPORT_KEEP_ORIGINAL)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.IMPORT_KEEP_ORIGINAL, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.IMPORT_KEEP_ORIGINAL, newValue as Boolean)
                true
            }
        }
    }
}