package com.sanad.bond.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanad.bond.data.db.dao.DocumentHtmlDao
import com.sanad.bond.data.db.dao.FavoriteFileDao
import com.sanad.bond.data.db.dao.RecentFileDao
import com.sanad.bond.data.db.entity.DocumentHtml
import com.sanad.bond.data.db.entity.FavoriteFile
import com.sanad.bond.data.db.entity.RecentFile

/**
 * Central Room database for Sanad.
 *
 * Version 1: recent files + favorites.
 * Version 2: adds document_html (editor HTML snapshots keyed by file URI).
 */
@Database(
    entities = [
        RecentFile::class,
        FavoriteFile::class,
        DocumentHtml::class
    ],
    version = 2,
    exportSchema = true
)
abstract class SanadDatabase : RoomDatabase() {

    abstract fun recentFileDao(): RecentFileDao
    abstract fun favoriteFileDao(): FavoriteFileDao
    abstract fun documentHtmlDao(): DocumentHtmlDao

    companion object {
        private const val DB_NAME = "sanad.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `document_html` (
                        `uri` TEXT NOT NULL PRIMARY KEY,
                        `html` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: SanadDatabase? = null

        fun get(context: Context): SanadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SanadDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}