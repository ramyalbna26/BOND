package com.sanad.bond.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanad.bond.data.db.entity.DocumentHtml

@Dao
interface DocumentHtmlDao {

    @Query("SELECT html FROM document_html WHERE uri = :uri LIMIT 1")
    suspend fun get(uri: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: DocumentHtml)

    @Query("DELETE FROM document_html WHERE uri = :uri")
    suspend fun delete(uri: String)
}