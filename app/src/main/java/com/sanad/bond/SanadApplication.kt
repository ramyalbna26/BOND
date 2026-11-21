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

        // 6. Theme — read from the SAME file PreferenceFragmentCompat writes
        // to (sanad_settings via SettingsRepository). The value is one of
        // "system" / "light" / "dark" (ListPreference in prefs_general.xml)
        // mapped to AppCompatDelegate night-mode constants.
        try {
            val themeMode = when (SettingsRepository.getString(
                com.sanad.bond.data.model.SettingsKeys.GENERAL_THEME,
                "system"
            )) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(themeMode)
            LogManager.bootLog("[6/7] Theme applied: $themeMode")
        } catch (t: Throwable) {
            LogManager.bootLog("[6/7] Theme FAILED: ${t.message}")
        }

        // 7. Locale + Backup
        try {
            // Language tag from sanad_settings ("ar" / "en" / "system").
            val langCode = SettingsRepository.getString(
                com.sanad.bond.data.model.SettingsKeys.GENERAL_LANGUAGE,
                "ar"
            ).ifBlank { "ar" }
            val tags = if (langCode == "system") {
                androidx.core.os.LocaleListCompat.getEmptyLocaleList()
            } else {
                androidx.core.os.LocaleListCompat.forLanguageTags(langCode)
            }
            AppCompatDelegate.setApplicationLocales(tags)
            BackupScheduler.schedule(this)
            LogManager.bootLog("[7/7] Locale + Backup OK")
        } catch (t: Throwable) {
            LogManager.bootLog("[7/7] FAILED: ${t.message}")
        }

        LogManager.bootLog("===== SanadApplication.onCreate() END =====")
    }
}