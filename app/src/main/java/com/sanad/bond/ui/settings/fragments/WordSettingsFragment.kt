package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Word (DOCX) settings: page size, orientation, margins, image compression,
 * font embedding, compatibility mode.
 */
class WordSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_word, rootKey)

        bindList(SettingsKeys.WORD_DEFAULT_PAGE_SIZE, default = "A4")
        bindList(SettingsKeys.WORD_DEFAULT_ORIENTATION, default = "portrait")
        bindList(SettingsKeys.WORD_DEFAULT_MARGINS, default = "normal")

        bindSwitch(SettingsKeys.WORD_COMPRESS_IMAGES, default = true)
        bindSwitch(SettingsKeys.WORD_EMBED_FONTS, default = false)
        bindSwitch(SettingsKeys.WORD_KEEP_COMPATIBILITY, default = true)
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