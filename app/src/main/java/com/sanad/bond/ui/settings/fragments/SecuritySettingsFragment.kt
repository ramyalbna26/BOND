package com.sanad.bond.ui.settings.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.core.logging.LogManager

/**
 * Security settings: app lock, biometric, PIN, auto-lock, encryption.
 *
 * The toggles persist the user's preference. Actual enforcement
 * (lock screen, biometric prompt, PIN entry) ships in Level 5 services.
 * This fragment hides biometric on hardware that doesn't support it.
 */
class SecuritySettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_security, rootKey)

        bindAppLock()
        bindBiometric()
        bindPin()
        bindAutoLock()
        bindEncryptDocuments()
        bindHideRecent()
    }

    private fun bindAppLock() {
        find<SwitchPreferenceCompat>(SettingsKeys.SECURITY_APP_LOCK)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.SECURITY_APP_LOCK, false)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.SECURITY_APP_LOCK, newValue as Boolean)
                true
            }
        }
    }

    private fun bindBiometric() {
        val pref = find<SwitchPreferenceCompat>(SettingsKeys.SECURITY_BIOMETRIC) ?: return

        // Use androidx.biometric.BiometricManager instead of the deprecated
        // PackageManager.FEATURE_FINGERPRINT (API 28+, and it misses face /
        // iris sensors). BIOMETRIC_WEAK matches the authenticators
        // BiometricPrompt accepts by default.
        val canAuth = try {
            androidx.biometric.BiometricManager.from(requireContext())
                .canAuthenticate(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
        } catch (_: Exception) {
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        }

        val biometricAvailable = canAuth == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

        if (!biometricAvailable) {
            // Hide the toggle on devices with no biometric hardware OR when
            // it's temporarily unavailable (enrollment locked, etc.).
            pref.isVisible = false
            return
        }

        pref.isChecked = SettingsRepository.getBoolean(SettingsKeys.SECURITY_BIOMETRIC, false)
        pref.setOnPreferenceChangeListener { _, newValue ->
            SettingsRepository.putBoolean(SettingsKeys.SECURITY_BIOMETRIC, newValue as Boolean)
            true
        }
    }

    private fun bindPin() {
        find<Preference>("pref_set_pin")?.setOnPreferenceClickListener {
            // PIN entry screen ships in Level 5
            LogManager.i(logTag, "Set PIN tapped — coming in Level 5")
            showMessage(R.string.prefs_security_pin_coming)
            true
        }
    }

    private fun bindAutoLock() {
        find<ListPreference>(SettingsKeys.SECURITY_AUTO_LOCK_MIN)?.apply {
            value = SettingsRepository.getString(SettingsKeys.SECURITY_AUTO_LOCK_MIN, "5")
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(SettingsKeys.SECURITY_AUTO_LOCK_MIN, newValue as String)
                true
            }
        }
    }

    private fun bindEncryptDocuments() {
        find<SwitchPreferenceCompat>(SettingsKeys.SECURITY_ENCRYPT_DOCUMENTS)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.SECURITY_ENCRYPT_DOCUMENTS, false)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.SECURITY_ENCRYPT_DOCUMENTS, newValue as Boolean)
                true
            }
        }
    }

    private fun bindHideRecent() {
        find<SwitchPreferenceCompat>(SettingsKeys.SECURITY_HIDE_RECENT)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.SECURITY_HIDE_RECENT, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.SECURITY_HIDE_RECENT, newValue as Boolean)
                true
            }
        }
    }
}