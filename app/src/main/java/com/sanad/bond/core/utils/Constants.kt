package com.sanad.bond.core.utils

/**
 * Application-wide constants
 */
object Constants {

    // Database
    const val DATABASE_NAME = "sanad_database"
    const val DATABASE_VERSION = 2

    // Preferences
    const val PREFERENCES_NAME = "sanad_prefs"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_LANGUAGE_CODE = "language_code"
    const val KEY_AUTO_BACKUP = "auto_backup"
    const val KEY_BACKUP_INTERVAL = "backup_interval"

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