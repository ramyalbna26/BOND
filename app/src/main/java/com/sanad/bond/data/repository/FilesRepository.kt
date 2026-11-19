package com.sanad.bond.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.data.db.SanadDatabase
import com.sanad.bond.data.db.entity.FavoriteFile
import com.sanad.bond.data.db.entity.RecentFile
import com.sanad.bond.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

object FilesRepository {

    private const val TAG = "FilesRepository"
    private lateinit var db: SanadDatabase

    fun init(context: Context) {
        db = Room.databaseBuilder(
            context.applicationContext,
            SanadDatabase::class.java,
            "sanad.db"
        )
            .fallbackToDestructiveMigration()
            .build()
        LogManager.i(TAG, "FilesRepository initialized")
    }

    fun observeRecent(): Flow<List<FileItem>> = combine(
        db.recentFileDao().getAllRecentFiles(),
        db.favoriteFileDao().getAllFavoriteFiles()
    ) { recent, favorites ->
        val favUris = favorites.mapTo(HashSet()) { it.uri }
        recent.map { r ->
            FileItem(
                uri = r.uri,
                displayName = r.displayName,
                mimeType = r.mimeType,
                sizeBytes = r.sizeBytes,
                lastModified = r.lastOpenedAt,
                isFavorite = r.uri in favUris
            )
        }
    }

    fun observeFavorites(): Flow<List<FileItem>> = combine(
        db.favoriteFileDao().getAllFavoriteFiles(),
        db.recentFileDao().getAllRecentFiles()
    ) { favorites, recent ->
        val recentByUri = recent.associateBy { it.uri }
        favorites.map { f ->
            FileItem(
                uri = f.uri,
                displayName = f.displayName,
                mimeType = f.mimeType,
                sizeBytes = f.sizeBytes,
                lastModified = recentByUri[f.uri]?.lastOpenedAt ?: f.favoritedAt,
                isFavorite = true
            )
        }
    }

    suspend fun recordRecent(uri: String, displayName: String, mimeType: String, sizeBytes: Long) {
        db.recentFileDao().insertRecentFile(
            RecentFile(
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleFavorite(item: FileItem): Boolean {
        val isFav = db.favoriteFileDao().isFavorite(item.uri)
        return if (isFav) {
            db.favoriteFileDao().deleteFavoriteFile(item.uri)
            false
        } else {
            db.favoriteFileDao().insertFavoriteFile(
                FavoriteFile(
                    uri = item.uri,
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes,
                    favoritedAt = System.currentTimeMillis()
                )
            )
            true
        }
    }

    suspend fun removeRecent(uri: String) {
        db.recentFileDao().deleteRecentFile(uri)
    }

    suspend fun scanAppFiles(context: Context): List<FileItem> = withContext(Dispatchers.IO) {
        val favUris: Set<String> = try {
            db.favoriteFileDao().getAllFavoriteFiles()
                .first()
                .mapTo(HashSet()) { it.uri }
        } catch (_: Throwable) { emptySet() }

        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.filter { it.extension.lowercase() in setOf("docx", "pdf", "txt", "md") }
            ?.map { file ->
                val uri = Uri.fromFile(file).toString()
                FileItem(
                    uri = uri,
                    displayName = file.name,
                    mimeType = guessMime(file.name),
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    isFavorite = uri in favUris
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?.toList()
            ?: emptyList()
    }

    private fun guessMime(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }
}