package com.sanad.bond.data.repository

import com.sanad.bond.core.base.BaseRepository
import com.sanad.bond.core.result.Result
import com.sanad.bond.data.db.dao.DocumentHtmlDao
import com.sanad.bond.data.model.DocumentModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface DocumentRepository {
    fun getAllDocuments(): Flow<List<DocumentModel>>
    suspend fun getDocument(id: String): Result<DocumentModel?>
    suspend fun saveDocument(document: DocumentModel): Result<Unit>
    suspend fun updateDocument(document: DocumentModel): Result<Unit>
    suspend fun deleteDocument(id: String): Result<Unit>
    suspend fun searchDocuments(query: String): Result<List<DocumentModel>>
}

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val documentHtmlDao: DocumentHtmlDao
) : BaseRepository(), DocumentRepository {

    override fun getAllDocuments(): Flow<List<DocumentModel>> {
        return documentHtmlDao.getAllDocuments()
    }

    override suspend fun getDocument(id: String): Result<DocumentModel?> {
        return safeDbCall("getDocument") {
            documentHtmlDao.getDocumentById(id)?.toModel()
        }
    }

    override suspend fun saveDocument(document: DocumentModel): Result<Unit> {
        return safeDbCall("saveDocument") {
            documentHtmlDao.insertDocument(document.toEntity())
        }
    }

    override suspend fun updateDocument(document: DocumentModel): Result<Unit> {
        return safeDbCall("updateDocument") {
            documentHtmlDao.updateDocument(document.toEntity())
        }
    }

    override suspend fun deleteDocument(id: String): Result<Unit> {
        return safeDbCall("deleteDocument") {
            documentHtmlDao.deleteDocument(id)
        }
    }

    override suspend fun searchDocuments(query: String): Result<List<DocumentModel>> {
        return safeDbCall("searchDocuments") {
            documentHtmlDao.searchDocuments("%$query%").map { it.toModel() }
        }
    }
}