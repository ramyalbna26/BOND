package com.sanad.bond.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A file that the user recently opened. Upserted on every open so the
 * "Recent" tab reflects actual usage, not just presence on disk.
 */
@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastOpenedAt: Long
)