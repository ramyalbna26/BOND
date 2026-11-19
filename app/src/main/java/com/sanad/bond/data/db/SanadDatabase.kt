package com.sanad.bond.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sanad.bond.data.db.dao.DocumentHtmlDao
import com.sanad.bond.data.db.dao.FavoriteFileDao
import com.sanad.bond.data.db.dao.RecentFileDao
import com.sanad.bond.data.db.entity.DocumentHtml
import com.sanad.bond.data.db.entity.FavoriteFile
import com.sanad.bond.data.db.entity.RecentFile

@Database(
    entities = [DocumentHtml::class, RecentFile::class, FavoriteFile::class],
    version = 1,
    exportSchema = false
)
abstract class SanadDatabase : RoomDatabase() {
    abstract fun documentHtmlDao(): DocumentHtmlDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun favoriteFileDao(): FavoriteFileDao

    companion object {
        @Volatile
        private var INSTANCE: SanadDatabase? = null

        fun getInstance(context: Context): SanadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SanadDatabase::class.java,
                    "sanad.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}