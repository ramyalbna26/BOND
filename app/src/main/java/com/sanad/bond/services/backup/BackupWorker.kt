package com.sanad.bond.services.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sanad.bond.core.logging.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Periodic worker that snapshots the app's persistent state:
 *
 *   - SharedPreferences  (data/data/<pkg>/shared_prefs/sanad_settings.xml)
 *   - Room database      (data/data/<pkg>/databases/sanad.db + -wal + -shm)
 *
 * Each run creates a new timestamped folder under:
 *   getExternalFilesDir(null)/backups/backup_<yyyyMMdd_HHmmss>/
 *
 * Retention: only the newest [MAX_BACKUPS] folders are kept; older ones
 * are deleted automatically. No content:// or permissions needed — the
 * backups live inside the app's external files directory.
 *
 * Restore is out of scope for the auto-backup phase; the folders are
 * meant to be copied off-device manually (or restored by a future
 * RestoreWorker).
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        LogManager.i(TAG, "BackupWorker started (attempt=${runAttemptCount + 1})")

        return try {
            val backupDir = performBackup(applicationContext)
            if (backupDir != null) {
                LogManager.i(TAG, "Backup created: ${backupDir.absolutePath}")
                Result.success()
            } else {
                LogManager.w(TAG, "Backup produced no output")
                Result.failure()
            }
        } catch (t: Throwable) {
            LogManager.e(TAG, "Backup failed", t)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val MAX_BACKUPS = 5
        private const val MAX_RETRIES = 3

        // The prefs file name must match SettingsRepository.PREFS_NAME
        private const val PREFS_FILE = "sanad_settings.xml"
        private const val DB_PREFIX = "sanad.db"

        /**
         * Creates one timestamped backup folder. Returns the folder on
         * success, null if any critical step failed.
         */
        private suspend fun performBackup(context: Context): File? =
            withContext(Dispatchers.IO) {
                try {
                    val root = File(
                        context.getExternalFilesDir(null) ?: context.filesDir,
                        "backups"
                    )
                    if (!root.exists() && !root.mkdirs()) {
                        LogManager.e(TAG, "Cannot create backups dir: ${root.absolutePath}")
                        return@withContext null
                    }

                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(Date())
                    val target = File(root, "backup_$stamp")
                    if (!target.mkdirs()) {
                        LogManager.e(TAG, "Cannot create backup folder: ${target.absolutePath}")
                        return@withContext null
                    }

                    // 1. SharedPreferences
                    val prefsSource = File(context.dataDir, "shared_prefs/$PREFS_FILE")
                    if (prefsSource.exists()) {
                        prefsSource.copyTo(
                            File(target, "sanad_settings.xml"),
                            overwrite = true
                        )
                        LogManager.d(TAG, "Copied prefs (${prefsSource.length()} bytes)")
                    } else {
                        LogManager.w(TAG, "No prefs file at ${prefsSource.absolutePath}")
                    }

                    // 2. Room database (+ WAL + SHM companions)
                    val dbDir = File(context.dataDir, "databases")
                    if (dbDir.exists()) {
                        dbDir.listFiles()?.forEach { f ->
                            if (f.isFile && f.name.startsWith(DB_PREFIX)) {
                                f.copyTo(File(target, f.name), overwrite = true)
                                LogManager.d(TAG, "Copied db file ${f.name} (${f.length()} bytes)")
                            }
                        }
                    } else {
                        LogManager.w(TAG, "No databases dir at ${dbDir.absolutePath}")
                    }

                    // 3. Metadata (handy for the user to know what's inside)
                    val meta = buildString {
                        append("{\n")
                        append("  \"timestamp\": ").append(System.currentTimeMillis()).append(",\n")
                        append("  \"humanTime\": \"").append(stamp).append("\",\n")
                        append("  \"appVersion\": \"").append(appVersionName(context)).append("\",\n")
                        append("  \"contents\": [\"shared_prefs\", \"room_db\"]\n")
                        append("}\n")
                    }
                    File(target, "metadata.json").writeText(meta)

                    // 4. Retention — delete oldest folders beyond MAX_BACKUPS
                    pruneOldBackups(root)

                    target
                } catch (t: Throwable) {
                    LogManager.e(TAG, "performBackup threw", t)
                    null
                }
            }

        private fun pruneOldBackups(root: File) {
            val dirs = root.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("backup_") }
                ?.sortedByDescending { it.name }
                ?: return

            if (dirs.size <= MAX_BACKUPS) return

            dirs.drop(MAX_BACKUPS).forEach { old ->
                try {
                    val ok = old.deleteRecursively()
                    LogManager.d(TAG, "Pruned ${old.name}: $ok")
                } catch (t: Throwable) {
                    LogManager.w(TAG, "Failed to prune ${old.name}", t)
                }
            }
        }

        @Suppress("DEPRECATION")
        private fun appVersionName(context: Context): String = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }
    }
}