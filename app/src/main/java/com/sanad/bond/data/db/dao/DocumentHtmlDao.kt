package com.sanad.bond.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanad.bond.data.db.entity.DocumentHtml
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentHtmlDao {
    @Query("SELECT * FROM document_html ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentHtml>>

    @Query("SELECT * FROM document_html WHERE uri = :uri LIMIT 1")
    suspend fun getDocumentById(uri: String): DocumentHtml?

    @Query("SELECT html FROM document_html WHERE uri = :uri LIMIT 1")
    suspend fun get(uri: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(entity: DocumentHtml)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateDocument(entity: DocumentHtml)

    @Query("DELETE FROM document_html WHERE uri = :uri")
    suspend fun deleteDocument(uri: String)

    @Query("SELECT * FROM document_html WHERE html LIKE :query ORDER BY updatedAt DESC")
    suspend fun searchDocuments(query: String): List<DocumentHtml>
}