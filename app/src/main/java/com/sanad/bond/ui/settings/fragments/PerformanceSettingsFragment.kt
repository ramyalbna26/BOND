package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository

/**
 * Performance settings: rendering acceleration, image downscaling,
 * parallel processing, streaming thresholds, background workers.
 *
 * All values are read by the service layer at runtime.
 */
class PerformanceSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_performance, rootKey)

        bindSwitch(SettingsKeys.PERF_HARDWARE_ACCEL, default = true)
        bindSwitch(SettingsKeys.PERF_IMAGE_DOWNSCALE, default = true)

        bindList(SettingsKeys.PERF_MAX_PARALLEL_TASKS, default = "4")
        bindList(SettingsKeys.PERF_STREAMING_THRESHOLD_MB, default = "50")
        bindList(SettingsKeys.PERF_BACKGROUND_WORKERS, default = "2")
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