package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Feature toggles for every bundled library.
 * These flags gate the visibility of the corresponding actions across the app.
 *
 * All flags default to TRUE (feature available).
 * Turning one off is safe — it only hides the entry point.
 */
class LibrariesSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_libraries, rootKey)

        bind(SettingsKeys.LIB_WORD_ENABLED)
        bind(SettingsKeys.LIB_PDF_ENABLED)
        bind(SettingsKeys.LIB_OCR_ENABLED)
        bind(SettingsKeys.LIB_TTS_ENABLED)
        bind(SettingsKeys.LIB_STT_ENABLED)
        bind(SettingsKeys.LIB_IMAGE_ENABLED)
        bind(SettingsKeys.LIB_SIGNATURE_ENABLED)
        bind(SettingsKeys.LIB_QR_ENABLED)
        bind(SettingsKeys.LIB_TEXT_ENABLED)
        bind(SettingsKeys.LIB_CHARTS_ENABLED)
        bind(SettingsKeys.LIB_LATEX_ENABLED)
    }

    private fun bind(key: String) {
        find<SwitchPreferenceCompat>(key)?.apply {
            isChecked = SettingsRepository.getBoolean(key, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(key, newValue as Boolean)
                true
            }
        }
    }
}