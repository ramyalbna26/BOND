package com.sanad.bond.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanad.bond.core.utils.Constants
import com.sanad.bond.data.db.dao.DocumentHtmlDao
import com.sanad.bond.data.db.dao.FavoriteFileDao
import com.sanad.bond.data.db.dao.RecentFileDao
import com.sanad.bond.data.db.entity.DocumentHtml
import com.sanad.bond.data.db.entity.FavoriteFile
import com.sanad.bond.data.db.entity.RecentFile

/**
 * Single source of truth for the app's SQLite database.
 *
 * Foundational rules enforced here (previously broken — see analysis report):
 *  - `version` MUST equal [Constants.DATABASE_VERSION] and match the highest
 *    committed schema JSON under `app/schemas/`. v1 had only recent/favorite
 *    tables; v2 adds `document_html`. Code was pinned to v1 while v2.json was
 *    committed → `IllegalStateException` on every install that had v2 data.
 *  - `exportSchema = true` so KSP regenerates the JSON under the
 *    `room.schemaLocation` arg declared in app/build.gradle.kts.
 *  - Exactly ONE builder in the whole app. The Hilt [DatabaseModule] calls
 *    [getInstance]; [com.sanad.bond.data.repository.FilesRepository] and
 *    [com.sanad.bond.data.repository.DocumentRepository] MUST NOT open their
 *    own `Room.databaseBuilder` (old code did → leaked FDs and raced on
 *    migration policy).
 *  - Migration policy: explicit [MIGRATION_1_2], NO destructive fallback.
 *    Destructive fallback was silently wiping user data on schema mismatch.
 */
@Database(
    entities = [DocumentHtml::class, RecentFile::class, FavoriteFile::class],
    version = Constants.DATABASE_VERSION,
    exportSchema = true
)
abstract class SanadDatabase : RoomDatabase() {
    abstract fun documentHtmlDao(): DocumentHtmlDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun favoriteFileDao(): FavoriteFileDao

    companion object {
        @Volatile
        private var INSTANCE: SanadDatabase? = null

        /**
         * v1 → v2: add the `document_html` table that stores the editor HTML
         * per file URI. Mirrors the entity at [DocumentHtml].
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `document_html` (
                        `uri` TEXT NOT NULL,
                        `html` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`uri`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): SanadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SanadDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
