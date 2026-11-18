package com.sanad.bond.data.repository

import com.sanad.bond.core.base.BaseRepository
import com.sanad.bond.core.result.Result
import com.sanad.bond.data.db.dao.FavoriteFileDao
import com.sanad.bond.data.db.dao.RecentFileDao
import com.sanad.bond.data.model.FileItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface FilesRepository {
    fun getRecentFiles(): Flow<List<FileItem>>
    fun getFavoriteFiles(): Flow<List<FileItem>>
    suspend fun addRecentFile(file: FileItem): Result<Unit>
    suspend fun removeRecentFile(fileId: String): Result<Unit>
    suspend fun toggleFavorite(file: FileItem): Result<Boolean>
    suspend fun isFavorite(fileId: String): Result<Boolean>
    suspend fun clearRecentFiles(): Result<Unit>
}

@Singleton
class FilesRepositoryImpl @Inject constructor(
    private val recentFileDao: RecentFileDao,
    private val favoriteFileDao: FavoriteFileDao
) : BaseRepository(), FilesRepository {

    override fun getRecentFiles(): Flow<List<FileItem>> {
        return recentFileDao.getAllRecentFiles()
    }

    override fun getFavoriteFiles(): Flow<List<FileItem>> {
        return favoriteFileDao.getAllFavoriteFiles()
    }

    override suspend fun addRecentFile(file: FileItem): Result<Unit> {
        return safeDbCall("addRecentFile") {
            recentFileDao.insertRecentFile(file.toEntity())
        }
    }

    override suspend fun removeRecentFile(fileId: String): Result<Unit> {
        return safeDbCall("removeRecentFile") {
            recentFileDao.deleteRecentFile(fileId)
        }
    }

    override suspend fun toggleFavorite(file: FileItem): Result<Boolean> {
        return safeDbCall("toggleFavorite") {
            val isFavorite = favoriteFileDao.isFavorite(file.id)
            if (isFavorite) {
                favoriteFileDao.deleteFavoriteFile(file.id)
                false
            } else {
                favoriteFileDao.insertFavoriteFile(file.toFavoriteEntity())
                true
            }
        }
    }

    override suspend fun isFavorite(fileId: String): Result<Boolean> {
        return safeDbCall("isFavorite") {
            favoriteFileDao.isFavorite(fileId)
        }
    }

    override suspend fun clearRecentFiles(): Result<Unit> {
        return safeDbCall("clearRecentFiles") {
            recentFileDao.clearAllRecentFiles()
        }
    }
}