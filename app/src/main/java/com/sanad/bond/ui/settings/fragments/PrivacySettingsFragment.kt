package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Privacy: telemetry toggles, log anonymization, policy links.
 *
 * Policy/terms links are stubbed with a "coming soon" dialog until real
 * URLs are hosted. Better than pointing users at example.com 404 pages.
 */
class PrivacySettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_privacy, rootKey)

        bindSwitch(SettingsKeys.PRIVACY_SEND_CRASH_REPORTS, default = true)
        bindSwitch(SettingsKeys.PRIVACY_SEND_USAGE_STATS, default = false)
        bindSwitch(SettingsKeys.PRIVACY_ANONYMIZE_LOGS, default = true)
        bindSwitch(SettingsKeys.PRIVACY_CLEAR_ON_LOGOUT, default = false)

        find<Preference>("pref_privacy_policy")?.setOnPreferenceClickListener {
            showComingSoonDialog(R.string.prefs_privacy_policy_title)
            true
        }
        find<Preference>("pref_privacy_terms")?.setOnPreferenceClickListener {
            showComingSoonDialog(R.string.prefs_privacy_terms_title)
            true
        }
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

    private fun showComingSoonDialog(titleRes: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(R.string.settings_placeholder_message)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }
}