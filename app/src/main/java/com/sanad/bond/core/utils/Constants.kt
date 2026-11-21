package com.sanad.bond.core.utils

/**
 * Application-wide constants
 */
object Constants {

    // Database — MUST match the name passed to Room.databaseBuilder
    // (SanadDatabase.kt, BackupWorker.DB_PREFIX) and the schemas dir.
    const val DATABASE_NAME = "sanad.db"
    const val DATABASE_VERSION = 2

    // Preferences — MUST match SettingsRepository.PREFS_NAME and the name
    // assigned in BasePreferenceFragment so PreferenceFragmentCompat writes
    // to the same file SettingsRepository reads from.
    const val PREFERENCES_NAME = "sanad_settings"

    // Theme / language keys — MUST match SettingsKeys.GENERAL_THEME /
    // GENERAL_LANGUAGE. Old short keys (theme_mode / language_code) were
    // read from the wrong prefs file by SanadApplication and never matched
    // what PreferenceFragmentCompat wrote.
    const val KEY_THEME_MODE = "sanad.general.theme"
    const val KEY_LANGUAGE_CODE = "sanad.general.language"
    const val KEY_AUTO_BACKUP = "sanad.storage.auto_backup"
    const val KEY_BACKUP_INTERVAL = "sanad.storage.backup_interval_hours"

    // File Extensions
    const val EXT_PDF = ".pdf"
    const val EXT_DOCX = ".docx"
    const val EXT_MD = ".md"
    const val EXT_TXT = ".txt"
    const val EXT_HTML = ".html"

    // MIME Types
    const val MIME_PDF = "application/pdf"
    const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MIME_MD = "text/markdown"
    const val MIME_TXT = "text/plain"
    const val MIME_HTML = "text/html"

    // Network
    const val CONNECT_TIMEOUT = 30_000L // 30 seconds
    const val READ_TIMEOUT = 30_000L
    const val WRITE_TIMEOUT = 30_000L

    // Backup
    const val BACKUP_INTERVAL_HOURS = 24L
    const val MAX_BACKUP_SIZE_MB = 50

    // UI
    const val MAX_FILE_NAME_LENGTH = 100
    const val MAX_RECENT_FILES = 50
    const val MAX_FAVORITE_FILES = 100

    // Logging
    const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5 MB
    const val MAX_LOG_FILES = 5
}