package com.sanad.bond.services.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import java.io.File
import java.util.concurrent.TimeUnit

object BackupScheduler {

    private const val TAG = "BackupScheduler"
    private const val PERIODIC_WORK_NAME = "sanad_auto_backup"
    private const val ONE_OFF_WORK_NAME = "sanad_backup_now"
    private const val DEFAULT_INTERVAL_HOURS = 24L

    /**
     * Enqueues (or cancels) the periodic auto-backup based on the user's
     * settings. Previously this ALWAYS enqueued a 24h periodic worker,
     * ignoring both the `sanad.storage.auto_backup` toggle and the
     * `sanad.storage.backup_interval_hours` choice — so turning auto-backup
     * OFF in Settings did nothing, and picking "1 hour" still ran every 24h.
     *
     * Called once from SanadApplication.onCreate and again from
     * StorageSettingsFragment whenever the toggle/interval changes.
     */
    fun schedule(context: Context) {
        try {
            val enabled = SettingsRepository.getBoolean(
                SettingsKeys.STORAGE_AUTO_BACKUP,
                default = false // opt-in — fresh install does NOT auto-backup
            )
            if (!enabled) {
                WorkManager.getInstance(context)
                    .cancelUniqueWork(PERIODIC_WORK_NAME)
                LogManager.i(TAG, "Auto-backup DISABLED — periodic work cancelled")
                return
            }

            val hours = parseIntervalHours(
                SettingsRepository.getString(
                    SettingsKeys.STORAGE_BACKUP_INTERVAL_HRS,
                    DEFAULT_INTERVAL_HOURS.toString()
                )
            )

            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                hours, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // keep existing enqueued time, update constraints/interval
                request
            )
            LogManager.i(TAG, "Auto-backup scheduled every ${hours}h")
        } catch (t: Throwable) {
            LogManager.e(TAG, "Failed to schedule backup", t)
        }
    }

    /** Alias kept for callers that re-sync after a settings change. */
    fun syncFromSettings(context: Context) = schedule(context)

    /**
     * Manual "Backup now". Previously enqueued a NON-unique OneTimeWorkRequest,
     * so repeated taps stacked concurrent workers writing to the same
     * timestamped folder. Now unique with REPLACE.
     */
    fun runNow(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_OFF_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            LogManager.i(TAG, "One-off backup enqueued (unique, REPLACE)")
        } catch (t: Throwable) {
            LogManager.e(TAG, "Failed to enqueue backup", t)
        }
    }

    fun latestBackupDir(context: Context): File? {
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
        if (!root.exists()) return null
        return root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("backup_") }
            ?.maxByOrNull { it.name }
    }

    /**
     * Parses the ListPreference value (a string of hours: "1","6","12","24","168").
     * Falls back to [DEFAULT_INTERVAL_HOURS] on anything unparseable or below
     * WorkManager's 15-minute minimum (defensive — all current values are >= 1h).
     */
    private fun parseIntervalHours(raw: String): Long {
        val parsed = raw.toLongOrNull()
            ?: DEFAULT_INTERVAL_HOURS
        // WorkManager minimum periodic interval is 15 minutes; below that it
        // silently bumps to 15 min. We coerce to a sane 1h minimum here.
        return parsed.coerceAtLeast(1L)
    }
}
