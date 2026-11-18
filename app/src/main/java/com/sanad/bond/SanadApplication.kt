package com.sanad.bond

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.color.DynamicColors
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.core.logging.LogLevel
import com.sanad.bond.services.backup.BackupScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SanadApplication : Application() {

    @Inject
    lateinit var backupScheduler: BackupScheduler

    override fun onCreate() {
        super.onCreate()

        LogManager.bootLog("===== SanadApplication.onCreate() START =====")

        // 1. Logging
        LogManager.bootLog("[1/7] LogManager.init()")
        try {
            LogManager.init(this)
            LogManager.bootLog("[1/7] LogManager.init() OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[1/7] LogManager.init() FAILED: ${t.message}")
        }

        // 2. PDFBox
        LogManager.bootLog("[2/7] PDFBoxResourceLoader.init()")
        try {
            PDFBoxResourceLoader.init(applicationContext)
            LogManager.bootLog("[2/7] PDFBoxResourceLoader.init() OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[2/7] PDFBoxResourceLoader.init() FAILED: ${t.message}")
        }

        // 3. Dynamic Colors
        LogManager.bootLog("[3/7] DynamicColors.applyToActivitiesIfAvailable()")
        try {
            DynamicColors.applyToActivitiesIfAvailable(this)
            LogManager.bootLog("[3/7] DynamicColors OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[3/7] DynamicColors FAILED: ${t.message}")
        }

        // 4. Theme
        LogManager.bootLog("[4/7] Applying theme")
        try {
            val prefs = getSharedPreferences("sanad_prefs", MODE_PRIVATE)
            val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            AppCompatDelegate.setDefaultNightMode(themeMode)
            LogManager.bootLog("[4/7] Theme applied: $themeMode")
        } catch (t: Throwable) {
            LogManager.bootLog("[4/7] Theme FAILED: ${t.message}")
        }

        // 5. Locale
        LogManager.bootLog("[5/7] Applying locale")
        try {
            val prefs = getSharedPreferences("sanad_prefs", MODE_PRIVATE)
            val langCode = prefs.getString("language_code", "ar") ?: "ar"
            val locales = LocaleListCompat.forLanguageTags(langCode)
            AppCompatDelegate.setApplicationLocales(locales)
            LogManager.bootLog("[5/7] Locale applied: $langCode")
        } catch (t: Throwable) {
            LogManager.bootLog("[5/7] Locale FAILED: ${t.message}")
        }

        // 6. Backup Scheduler
        LogManager.bootLog("[6/7] BackupScheduler.schedule()")
        try {
            backupScheduler.schedule()
            LogManager.bootLog("[6/7] BackupScheduler OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[6/7] BackupScheduler FAILED: ${t.message}")
        }

        // 7. Global Exception Handler
        LogManager.bootLog("[7/7] GlobalExceptionHandler")
        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    LogManager.log(
                        LogLevel.ERROR,
                        "GlobalExceptionHandler",
                        "Uncaught exception in ${thread.name}: ${throwable.message}",
                        throwable
                    )
                } catch (_: Throwable) {
                    // Ignore
                }
                defaultHandler?.uncaughtException(thread, throwable)
            }
            LogManager.bootLog("[7/7] GlobalExceptionHandler OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[7/7] GlobalExceptionHandler FAILED: ${t.message}")
        }

        LogManager.bootLog("===== SanadApplication.onCreate() END =====")
    }
}