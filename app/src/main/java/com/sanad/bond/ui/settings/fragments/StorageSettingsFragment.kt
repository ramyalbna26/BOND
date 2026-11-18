package com.sanad.bond.ui.settings.fragments

import android.os.Bundle
import android.text.format.Formatter
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.sanad.bond.R
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.services.backup.BackupScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Storage settings: shows real paths and cache usage, lets the user cap
 * cache size, clear it, and configure automatic backups.
 */
class StorageSettingsFragment : BasePreferenceFragment() {

    private lateinit var docsPathPref: Preference
    private lateinit var tempPathPref: Preference
    private lateinit var cacheSizePref: Preference
    private var backupLastPref: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_storage, rootKey)

        docsPathPref  = requireNotNull(find("pref_docs_path"))
        tempPathPref  = requireNotNull(find("pref_temp_path"))
        cacheSizePref = requireNotNull(find("pref_cache_size"))
        backupLastPref = find("pref_backup_last")

        bindCacheLimit()
        bindClearOnExit()
        bindAutoBackup()
        bindBackupInterval()
        bindClearCacheAction()
        bindBackupNowAction()

        refreshStorageInfo()
        refreshBackupInfo()
    }

    override fun onResume() {
        super.onResume()
        refreshStorageInfo()
        refreshBackupInfo()
    }

    // ---- ListPreference: cache size limit ----
    private fun bindCacheLimit() {
        find<ListPreference>(SettingsKeys.STORAGE_CACHE_SIZE_MB)?.apply {
            value = SettingsRepository.getString(SettingsKeys.STORAGE_CACHE_SIZE_MB, "200")
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(SettingsKeys.STORAGE_CACHE_SIZE_MB, newValue as String)
                refreshStorageInfo()
                true
            }
        }
    }

    // ---- SwitchPreferenceCompat: clear cache on exit ----
    private fun bindClearOnExit() {
        find<SwitchPreferenceCompat>(SettingsKeys.STORAGE_CLEAR_CACHE_ON_EXIT)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.STORAGE_CLEAR_CACHE_ON_EXIT, false)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.STORAGE_CLEAR_CACHE_ON_EXIT, newValue as Boolean)
                true
            }
        }
    }

    // ---- SwitchPreferenceCompat: auto backup ----
    private fun bindAutoBackup() {
        find<SwitchPreferenceCompat>(SettingsKeys.STORAGE_AUTO_BACKUP)?.apply {
            isChecked = SettingsRepository.getBoolean(SettingsKeys.STORAGE_AUTO_BACKUP, false)
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putBoolean(SettingsKeys.STORAGE_AUTO_BACKUP, newValue as Boolean)
                // Schedule or cancel the WorkManager job immediately so the
                // next boot (or the next preference change) picks it up.
                BackupScheduler.syncFromSettings(requireContext())
                true
            }
        }
    }

    // ---- ListPreference: backup interval ----
    private fun bindBackupInterval() {
        find<ListPreference>(SettingsKeys.STORAGE_BACKUP_INTERVAL_HRS)?.apply {
            value = SettingsRepository.getString(SettingsKeys.STORAGE_BACKUP_INTERVAL_HRS, "24")
            setOnPreferenceChangeListener { _, newValue ->
                SettingsRepository.putString(SettingsKeys.STORAGE_BACKUP_INTERVAL_HRS, newValue as String)
                // Re-register the worker with the new interval. UPDATE policy
                // replaces the previous request without duplicating it.
                BackupScheduler.syncFromSettings(requireContext())
                true
            }
        }
    }

    // ---- Action: clear cache now ----
    private fun bindClearCacheAction() {
        find<Preference>("pref_clear_cache")?.setOnPreferenceClickListener {
            showConfirm(
                titleRes = R.string.prefs_storage_clear_cache_title,
                messageRes = R.string.prefs_storage_clear_cache_message
            ) {
                clearCache()
                refreshStorageInfo()
                showMessage(R.string.prefs_storage_cleared)
            }
            true
        }
    }

    // ---- Action: run backup immediately ----
    private fun bindBackupNowAction() {
        find<Preference>("pref_backup_now")?.setOnPreferenceClickListener {
            BackupScheduler.runNow(requireContext())
            showMessage(R.string.prefs_storage_backup_started)
            // Give the worker a moment to finish before refreshing — the
            // next onResume will pick up the final state.
            refreshBackupInfo()
            true
        }
    }

    // ---- Storage info rendering ----

    private fun refreshStorageInfo() {
        val ctx = requireContext()

        // Documents directory: app-external, visible via file managers
        val docsDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        docsPathPref.summary = docsDir.absolutePath

        // Temp directory
        val tempDir = File(ctx.cacheDir, "temp").apply { mkdirs() }
        tempPathPref.summary = tempDir.absolutePath

        // Cache size vs limit
        val used = directorySize(ctx.cacheDir)
        val limitMb = SettingsRepository
            .getString(SettingsKeys.STORAGE_CACHE_SIZE_MB, "200")
            .toIntOrNull() ?: 200

        val usedStr  = Formatter.formatShortFileSize(ctx, used)
        val limitStr = if (limitMb >= 1024) "${limitMb / 1024} GB" else "$limitMb MB"
        cacheSizePref.summary = getString(
            R.string.prefs_storage_cache_usage_format,
            usedStr, limitStr
        )
    }

    private fun refreshBackupInfo() {
        val pref = backupLastPref ?: return
        val ctx = requireContext()
        val latest = BackupScheduler.latestBackupDir(ctx)
        if (latest == null) {
            pref.summary = getString(R.string.prefs_storage_backup_last_never)
            return
        }

        // Parse the timestamp suffix out of "backup_yyyyMMdd_HHmmss"
        val raw = latest.name.removePrefix("backup_")
        val prettyDate = try {
            val parser = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            formatter.format(parser.parse(raw) ?: Date(latest.lastModified()))
        } catch (_: Throwable) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(latest.lastModified()))
        }
        val size = directorySize(latest)
        val sizeStr = Formatter.formatShortFileSize(ctx, size)
        pref.summary = getString(R.string.prefs_storage_backup_last_format, prettyDate, sizeStr)
    }

    private fun clearCache() {
        val ctx = requireContext()
        deleteDirectoryContents(ctx.cacheDir)
        // Recreate the app's temp directory so services don't crash
        File(ctx.cacheDir, "temp").mkdirs()
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    private fun deleteDirectoryContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            try {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            } catch (_: Exception) {
                // Best effort
            }
        }
    }
}