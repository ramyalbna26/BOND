package com.sanad.bond.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A file the user starred for quick access.
 */
@Entity(tableName = "favorite_files")
data class FavoriteFile(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val favoritedAt: Long
)