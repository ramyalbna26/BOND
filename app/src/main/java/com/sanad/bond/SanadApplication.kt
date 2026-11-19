package com.sanad.bond

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.color.DynamicColors
import com.sanad.bond.core.logging.LogLevel
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.data.repository.FilesRepository
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.services.backup.BackupScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SanadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LogManager.bootLog("===== SanadApplication.onCreate() START =====")

        // 1. Logging
        try {
            LogManager.init(this)
            LogManager.bootLog("[1/7] LogManager.init() OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[1/7] LogManager.init() FAILED: ${t.message}")
        }

        // 2. Settings
        try {
            SettingsRepository.init(this)
            val enabled = SettingsRepository.isLoggingEnabled()
            val fileEnabled = SettingsRepository.isFileLoggingEnabled()
            val levelName = SettingsRepository.getLoggingMinLevel()
            LogManager.setLoggingEnabled(enabled)
            LogManager.setFileLoggingEnabled(fileEnabled)
            LogManager.setMinLevel(
                runCatching { LogLevel.valueOf(levelName) }.getOrDefault(LogLevel.VERBOSE)
            )
            LogManager.bootLog("[2/7] SettingsRepository.init() OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[2/7] SettingsRepository.init() FAILED: ${t.message}")
        }

        // 3. FilesRepository (init DB)
        try {
            FilesRepository.init(this)
            LogManager.bootLog("[3/7] FilesRepository.init() OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[3/7] FilesRepository.init() FAILED: ${t.message}")
        }

        // 4. PDFBox
        try {
            PDFBoxResourceLoader.init(applicationContext)
            LogManager.bootLog("[4/7] PDFBox OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[4/7] PDFBox FAILED: ${t.message}")
        }

        // 5. Dynamic Colors
        try {
            DynamicColors.applyToActivitiesIfAvailable(this)
            LogManager.bootLog("[5/7] DynamicColors OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[5/7] DynamicColors FAILED: ${t.message}")
        }

        // 6. Theme
        try {
            val prefs = getSharedPreferences("sanad_prefs", MODE_PRIVATE)
            val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            AppCompatDelegate.setDefaultNightMode(themeMode)
            LogManager.bootLog("[6/7] Theme applied: $themeMode")
        } catch (t: Throwable) {
            LogManager.bootLog("[6/7] Theme FAILED: ${t.message}")
        }

        // 7. Locale + Backup
        try {
            val prefs = getSharedPreferences("sanad_prefs", MODE_PRIVATE)
            val langCode = prefs.getString("language_code", "ar") ?: "ar"
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(langCode)
            )
            BackupScheduler.schedule(this)
            LogManager.bootLog("[7/7] Locale + Backup OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[7/7] FAILED: ${t.message}")
        }

        LogManager.bootLog("===== SanadApplication.onCreate() END =====")
    }
}