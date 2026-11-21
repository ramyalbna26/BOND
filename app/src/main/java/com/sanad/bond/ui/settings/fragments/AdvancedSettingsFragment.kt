package com.sanad.bond.ui.settings.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sanad.bond.MainActivity
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.core.logging.LogManager

/**
 * Advanced settings: debug mode, experimental features, custom JSON config,
 * and a factory reset that clears every preference and restarts the app.
 */
class AdvancedSettingsFragment : BasePreferenceFragment() {

    private var customConfigPref: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_advanced, rootKey)

        bindSwitch(SettingsKeys.ADV_DEBUG_MODE, default = false) { enabled ->
            LogManager.setMinLevel(
                if (enabled) com.sanad.bond.core.logging.LogLevel.VERBOSE
                else com.sanad.bond.core.logging.LogLevel.INFO
            )
        }
        bindSwitch(SettingsKeys.ADV_EXPERIMENTAL_FEATURES, default = false) { }
        bindSwitch(SettingsKeys.ADV_OVERRIDE_DEFAULTS, default = false) { }

        bindCustomConfig()
        bindViewKeys()
        bindReset()
    }

    private fun bindSwitch(
        key: String,
        default: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        find<SwitchPreferenceCompat>(key)?.apply {
            isChecked = SettingsRepository.getBoolean(key, default)
            setOnPreferenceChangeListener { _, newValue ->
                val v = newValue as Boolean
                SettingsRepository.putBoolean(key, v)
                onChanged(v)
                true
            }
        }
    }

    // ---- Custom JSON config ----
    private fun bindCustomConfig() {
        customConfigPref = find<Preference>("pref_custom_config")
        customConfigPref?.apply {
            updateConfigSummary()
            setOnPreferenceClickListener {
                showConfigEditor(SettingsRepository.getString(
                    SettingsKeys.ADV_CUSTOM_CONFIG_JSON, ""
                ))
                true
            }
        }
    }

    private fun updateConfigSummary() {
        val current = SettingsRepository.getString(SettingsKeys.ADV_CUSTOM_CONFIG_JSON, "")
        customConfigPref?.summary = if (current.isEmpty()) {
            getString(R.string.prefs_adv_custom_config_empty)
        } else {
            getString(R.string.prefs_adv_custom_config_set, current.length)
        }
    }

    private fun showConfigEditor(current: String) {
        val input = EditText(requireContext()).apply {
            setText(current)
            hint = "{ \"key\": \"value\" }"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.prefs_adv_custom_config_title)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.prefs_adv_custom_config_clear) { _, _ ->
                SettingsRepository.remove(SettingsKeys.ADV_CUSTOM_CONFIG_JSON)
                updateConfigSummary()
                showMessage(R.string.prefs_adv_custom_config_cleared)
            }
            .setPositiveButton(R.string.confirm) { _, _ ->
                val json = input.text?.toString()?.trim().orEmpty()
                if (json.isEmpty()) {
                    SettingsRepository.remove(SettingsKeys.ADV_CUSTOM_CONFIG_JSON)
                } else {
                    SettingsRepository.putString(SettingsKeys.ADV_CUSTOM_CONFIG_JSON, json)
                }
                updateConfigSummary()
                showMessage(R.string.prefs_adv_custom_config_saved)
            }
            .show()
    }

    // ---- View all keys (debug helper) ----
    private fun bindViewKeys() {
        find<Preference>("pref_view_settings_keys")?.setOnPreferenceClickListener {
            val keys = SettingsRepository.getAllKeys().sorted()
            if (keys.isEmpty()) {
                showMessage(R.string.prefs_adv_no_keys)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.prefs_adv_view_keys_title)
                    .setMessage(keys.joinToString("\n"))
                    .setPositiveButton(R.string.confirm, null)
                    .show()
            }
            true
        }
    }

    // ---- Reset all settings ----
    private fun bindReset() {
        find<Preference>("pref_reset_settings")?.setOnPreferenceClickListener {
            showConfirm(
                titleRes = R.string.prefs_adv_reset_title,
                messageRes = R.string.prefs_adv_reset_message
            ) {
                performReset()
            }
            true
        }
    }

    private fun performReset() {
        LogManager.w(logTag, "Factory reset initiated")
        SettingsRepository.clearAll()

        // Re-apply theme + locale defaults immediately so the user sees the
        // change without a process kill. Previously this called
        // Runtime.getRuntime().exit(0) 300ms after starting a new task,
        // which raced the task launch and showed a crash dialog on some
        // OEMs. We now re-apply the AppCompat night mode + locale list
        // (which recreate the activity stack for us) and let the process
        // die naturally.
        try {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.getEmptyLocaleList()
            )
        } catch (t: Throwable) {
            LogManager.w(logTag, "Re-apply theme/locale failed: ${t.message}")
        }

        // Start a fresh MainActivity task and tear down the current stack.
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        requireActivity().finishAffinity()
    }
}