package com.sanad.bond.ui.settings.fragments

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.core.logging.LogEntry
import com.sanad.bond.core.logging.LogLevel
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.ui.log.LogViewerActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Professional logging hub — replaces the ad-hoc logging screen from MainActivity.
 *
 *  - Live status (entries count, file size, availability, path)
 *  - Live preview of the last 3 entries (throttled, updates every ~500ms)
 *  - All settings (enable, file, auto-scroll, min level)
 *  - Actions (open viewer, refresh, share file, clear)
 *
 * Implements LogManager.LogListener to keep the status card in sync with the
 * logger without polling.
 */
class LoggingSettingsFragment : BasePreferenceFragment(), LogManager.LogListener {

    private lateinit var statusPref: Preference
    private lateinit var previewPref: Preference

    // Throttle: don't rebuild the summary on every single log line
    private var lastUiUpdate = 0L
    private val uiUpdateIntervalMs = 500L

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_logging, rootKey)

        statusPref  = requireNotNull(find("pref_log_status"))
        previewPref = requireNotNull(find("pref_log_preview"))

        bindLoggingToggle()
        bindFileToggle()
        bindMinLevel()
        bindAutoScroll()
        bindActions()

        refreshStatus()
    }

    // ============================================================
    // Lifecycle — start/stop live listener
    // ============================================================

    override fun onResume() {
        super.onResume()
        LogManager.addListener(this)
        refreshStatus()
    }

    override fun onPause() {
        LogManager.removeListener(this)
        super.onPause()
    }

    // ============================================================
    // LogListener — throttled UI refresh
    // ============================================================

    override fun onLog(entry: LogEntry) {
        val now = System.currentTimeMillis()
        if (now - lastUiUpdate < uiUpdateIntervalMs) return
        lastUiUpdate = now

        activity?.runOnUiThread {
            if (isAdded && view != null) refreshStatus()
        }
    }

    // ============================================================
    // Bindings
    // ============================================================

    private fun bindLoggingToggle() {
        find<SwitchPreferenceCompat>(SettingsKeys.LOGGING_ENABLED)?.apply {
            isChecked = LogManager.isLoggingEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                LogManager.setLoggingEnabled(enabled)
                SettingsRepository.setLoggingEnabled(enabled)
                LogManager.i(logTag, "Logging enabled=$enabled")
                refreshStatus()
                true
            }
        }
    }

    private fun bindFileToggle() {
        find<SwitchPreferenceCompat>(SettingsKeys.LOGGING_FILE_ENABLED)?.apply {
            isChecked = LogManager.isFileLoggingEnabled()
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                LogManager.setFileLoggingEnabled(enabled)
                SettingsRepository.setFileLoggingEnabled(enabled)
                LogManager.i(logTag, "File logging enabled=$enabled")
                refreshStatus()
                true
            }
        }
    }

    private fun bindMinLevel() {
        find<ListPreference>(SettingsKeys.LOGGING_MIN_LEVEL)?.apply {
            value = LogManager.getMinLevel().name
            setOnPreferenceChangeListener { _, newValue ->
                val name = newValue as String
                val level = runCatching { LogLevel.valueOf(name) }.getOrNull() ?: return@setOnPreferenceChangeListener false
                LogManager.setMinLevel(level)
                SettingsRepository.setLoggingMinLevel(name)
                LogManager.i(logTag, "Min level=$name")
                refreshStatus()
                true
            }
        }
    }

    private fun bindAutoScroll() {
        find<SwitchPreferenceCompat>(SettingsKeys.LOGGING_AUTO_SCROLL)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.LOGGING_AUTO_SCROLL, true)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.LOGGING_AUTO_SCROLL, newValue as Boolean)
                true
            }
        }
    }

    private fun bindActions() {
        // 1. Open full viewer
        find<Preference>("pref_log_open_viewer")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), LogViewerActivity::class.java))
            true
        }

        // 2. Refresh stats
        find<Preference>("pref_log_refresh")?.setOnPreferenceClickListener {
            refreshStatus()
            showMessage(R.string.prefs_logging_refreshed)
            true
        }

        // 3. Share the log file
        find<Preference>("pref_log_share")?.setOnPreferenceClickListener {
            shareLogFile()
            true
        }

        // 4. Clear (with confirmation)
        find<Preference>("pref_log_clear")?.setOnPreferenceClickListener {
            showConfirm(
                titleRes = R.string.prefs_logging_clear_title,
                messageRes = R.string.clear_logs_message
            ) {
                LogManager.clear()
                refreshStatus()
                showMessage(R.string.prefs_logging_cleared)
            }
            true
        }
    }

    // ============================================================
    // Status rendering
    // ============================================================

    private fun refreshStatus() {
        renderStatusCard()
        renderPreviewCard()
    }

    private fun renderStatusCard() {
        val enabled   = LogManager.isLoggingEnabled()
        val fileOn    = LogManager.isFileLoggingEnabled()
        val available = LogManager.isLogFileAvailable()
        val entries   = LogManager.sessionEntryCount()
        val sizeBytes = LogManager.logFileSizeBytes()
        val path      = LogManager.logFilePath()

        val indicator = if (enabled) "🟢" else "🔴"
        val entriesText = getString(R.string.prefs_logging_entries_format, entries)
        val sizeText    = Formatter.formatShortFileSize(requireContext(), sizeBytes)
        val fileState   = getString(if (fileOn) R.string.value_on else R.string.value_off)
        val availState  = getString(if (available) R.string.value_yes else R.string.value_no)

        statusPref.summary = buildString {
            append(indicator).append(' ')
            append(getString(if (enabled) R.string.prefs_logging_status_on
                             else R.string.prefs_logging_status_off))
            append("  ·  ").append(entriesText)
            append("  ·  ").append(sizeText)
            append('\n')
            append(getString(R.string.prefs_logging_status_file_line, fileState, availState))
            append('\n')
            append(path)
        }
    }

    private fun renderPreviewCard() {
        val recent = LogManager.snapshot().takeLast(3)
        if (recent.isEmpty()) {
            previewPref.summary = getString(R.string.prefs_logging_preview_empty)
            return
        }

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        previewPref.summary = recent.joinToString("\n") { entry ->
            val time = formatter.format(Date(entry.timestamp))
            val msg  = entry.message.replace('\n', ' ').take(80)
            "${entry.level.letter}/$time ${entry.tag}: $msg"
        }
    }

    // ============================================================
    // Share
    // ============================================================

    private fun shareLogFile() {
        val uri = LogManager.shareableLogUri()
        if (uri == null) {
            showMessage(R.string.prefs_logging_share_unavailable)
            return
        }

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sanad logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(share, getString(R.string.prefs_logging_share_title)))
        } catch (_: Exception) {
            showMessage(R.string.no_app_to_open)
        }
    }
}