package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Controls settings: default image processing (quality, max dimension,
 * format, compression, alt-text enforcement) and default text styling
 * (font, size, spacing, alignment) + word count display.
 *
 * Values feed into the services layer for image and text tools.
 */
class ControlsSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_controls, rootKey)

        // Image defaults
        bindList(SettingsKeys.CONTROLS_IMAGE_QUALITY, default = "medium")
        bindList(SettingsKeys.CONTROLS_IMAGE_MAX_DIMENSION, default = "2048")
        bindList(SettingsKeys.CONTROLS_IMAGE_FORMAT, default = "webp")

        bindSwitch(SettingsKeys.CONTROLS_IMAGE_AUTO_COMPRESS, default = true)
        bindSwitch(SettingsKeys.CONTROLS_IMAGE_ALT_TEXT_REQ, default = false)

        // Text defaults
        bindList(SettingsKeys.CONTROLS_TEXT_DEFAULT_FONT, default = "cairo")
        bindList(SettingsKeys.CONTROLS_TEXT_DEFAULT_SIZE, default = "16")
        bindList(SettingsKeys.CONTROLS_TEXT_DEFAULT_SPACING, default = "1.15")
        bindList(SettingsKeys.CONTROLS_TEXT_DEFAULT_ALIGN, default = "right")

        bindSwitch(SettingsKeys.CONTROLS_TEXT_SHOW_WORD_COUNT, default = true)
        bindSwitch(SettingsKeys.CONTROLS_TEXT_APPLY_DEFAULT_STYLE, default = true)
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