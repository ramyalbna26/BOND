package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * PDF settings: page size, orientation, image DPI, compression level,
 * font embedding, password protection, viewer scroll mode, night mode.
 *
 * Password UI ships in Level 5; the toggle only persists the preference.
 */
class PdfSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_pdf, rootKey)

        bindList(SettingsKeys.PDF_DEFAULT_PAGE_SIZE, default = "A4")
        bindList(SettingsKeys.PDF_DEFAULT_ORIENTATION, default = "portrait")
        bindList(SettingsKeys.PDF_IMAGE_DPI, default = "150")
        bindList(SettingsKeys.PDF_COMPRESSION_LEVEL, default = "5")
        bindList(SettingsKeys.PDF_VIEWER_SCROLL, default = "vertical")

        bindSwitch(SettingsKeys.PDF_EMBED_FONTS, default = true)
        bindSwitch(SettingsKeys.PDF_PASSWORD_PROTECTION, default = false)
        bindSwitch(SettingsKeys.PDF_VIEWER_NIGHT_MODE, default = false)
    }

    private fun bindSwitch(key: String, default: Boolean) {
        find<SwitchPreferenceCompat>(key)?.apply {
            isChecked = SettingsRepository.getBoolean(key, default)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(key, newValue as Boolean)
                if (key == SettingsKeys.PDF_PASSWORD_PROTECTION && (newValue as Boolean)) {
                    showMessage(R.string.prefs_pdf_password_coming)
                }
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