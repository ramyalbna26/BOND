package com.sanad.bond.data.model

/**
 * UI model for a single file shown in the library list.
 *
 * Decoupled from Room entities and disk File objects so the adapter and
 * fragments never touch the data layer directly.
 */
data class FileItem(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isFavorite: Boolean = false
)