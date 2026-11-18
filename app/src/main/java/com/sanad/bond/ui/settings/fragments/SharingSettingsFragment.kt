package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.R

/**
 * Sharing preferences: master toggle, default action (share/export/save), watermark.
 */
class SharingSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_sharing, rootKey)

        find<SwitchPreferenceCompat>(SettingsKeys.SHARING_ENABLED)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.SHARING_ENABLED, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.SHARING_ENABLED, newValue as Boolean)
                true
            }
        }

        find<ListPreference>(SettingsKeys.SHARING_DEFAULT_ACTION)?.apply {
            value = SettingsRepository.getString(SettingsKeys.SHARING_DEFAULT_ACTION, "share")
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(SettingsKeys.SHARING_DEFAULT_ACTION, newValue as String)
                true
            }
        }

        find<SwitchPreferenceCompat>(SettingsKeys.SHARING_INCLUDE_WATERMARK)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.SHARING_INCLUDE_WATERMARK, false)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.SHARING_INCLUDE_WATERMARK, newValue as Boolean)
                true
            }
        }
    }
}