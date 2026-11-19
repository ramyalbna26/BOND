package com.sanad.bond.services.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sanad.bond.core.logging.LogManager
import java.io.File
import java.util.concurrent.TimeUnit

object BackupScheduler {

    private const val TAG = "BackupScheduler"
    private const val UNIQUE_WORK_NAME = "sanad_auto_backup"
    private const val DEFAULT_INTERVAL_HOURS = 24L

    fun schedule(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                DEFAULT_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            LogManager.i(TAG, "Auto-backup scheduled every ${DEFAULT_INTERVAL_HOURS}h")
        } catch (t: Throwable) {
            LogManager.e(TAG, "Failed to schedule backup", t)
        }
    }

    fun syncFromSettings(context: Context) {
        schedule(context)
    }

    fun runNow(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
            LogManager.i(TAG, "One-off backup enqueued")
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
}