package com.sanad.bond.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the HTML produced by the editor for a given document URI.
 *
 * The HTML is the "source of truth" for the editor — it preserves the
 * exact formatting the user saw. This is independent of the native file
 * format (DOCX/PDF), which is written on every save as well, and is what
 * other apps open.
 *
 * The primary key is the file URI (either file:// or content://).
 */
@Entity(tableName = "document_html")
data class DocumentHtml(
    @PrimaryKey val uri: String,
    val html: String,
    val updatedAt: Long
)