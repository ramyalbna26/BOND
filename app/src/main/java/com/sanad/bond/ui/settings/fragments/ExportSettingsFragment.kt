package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Export settings: default format, preset, location behavior, metadata.
 */
class ExportSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_export, rootKey)

        bindDefaultFormat()
        bindPreset()
        bindAskLocation()
        bindOverwrite()
        bindIncludeMetadata()

        renderLocation()
    }

    private fun bindDefaultFormat() {
        find<ListPreference>(SettingsKeys.EXPORT_DEFAULT_FORMAT)?.apply {
            value = SettingsRepository.getString(SettingsKeys.EXPORT_DEFAULT_FORMAT, "pdf")
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(SettingsKeys.EXPORT_DEFAULT_FORMAT, newValue as String)
                true
            }
        }
    }

    private fun bindPreset() {
        find<ListPreference>(SettingsKeys.EXPORT_PRESET)?.apply {
            value = SettingsRepository.getString(SettingsKeys.EXPORT_PRESET, "default")
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(SettingsKeys.EXPORT_PRESET, newValue as String)
                true
            }
        }
    }

    private fun bindAskLocation() {
        find<SwitchPreferenceCompat>(SettingsKeys.EXPORT_ASK_LOCATION)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.EXPORT_ASK_LOCATION, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.EXPORT_ASK_LOCATION, newValue as Boolean)
                true
            }
        }
    }

    private fun bindOverwrite() {
        find<SwitchPreferenceCompat>(SettingsKeys.EXPORT_OVERWRITE_EXISTING)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.EXPORT_OVERWRITE_EXISTING, false)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.EXPORT_OVERWRITE_EXISTING, newValue as Boolean)
                true
            }
        }
    }

    private fun bindIncludeMetadata() {
        find<SwitchPreferenceCompat>(SettingsKeys.EXPORT_INCLUDE_METADATA)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.EXPORT_INCLUDE_METADATA, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.EXPORT_INCLUDE_METADATA, newValue as Boolean)
                true
            }
        }
    }

    private fun renderLocation() {
        val ctx = requireContext()
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        find<Preference>("pref_export_location")?.summary = dir.absolutePath
    }
}