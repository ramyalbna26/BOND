package com.sanad.bond.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanad.bond.data.db.entity.FavoriteFile
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteFileDao {
    @Query("SELECT * FROM favorite_files ORDER BY favoritedAt DESC")
    fun getAllFavoriteFiles(): Flow<List<FavoriteFile>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_files WHERE uri = :uri)")
    suspend fun isFavorite(uri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteFile(file: FavoriteFile)

    @Query("DELETE FROM favorite_files WHERE uri = :uri")
    suspend fun deleteFavoriteFile(uri: String)
}