package com.sanad.bond.core.di

import android.content.Context
import androidx.room.Room
import com.sanad.bond.data.db.SanadDatabase
import com.sanad.bond.data.db.dao.DocumentHtmlDao
import com.sanad.bond.data.db.dao.FavoriteFileDao
import com.sanad.bond.data.db.dao.RecentFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): SanadDatabase {
        return Room.databaseBuilder(
            context,
            SanadDatabase::class.java,
            "sanad_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRecentFileDao(database: SanadDatabase): RecentFileDao {
        return database.recentFileDao()
    }

    @Provides
    fun provideFavoriteFileDao(database: SanadDatabase): FavoriteFileDao {
        return database.favoriteFileDao()
    }

    @Provides
    fun provideDocumentHtmlDao(database: SanadDatabase): DocumentHtmlDao {
        return database.documentHtmlDao()
    }
}