package com.sanad.bond.ui.create

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sanad.bond.R

/**
 * Supported document types for the "create new" flow.
 *
 * Each entry knows its extension, MIME type, and the string resources
 * that describe it in the UI.
 */
enum class DocumentType(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val iconRes: Int,
    val extension: String,
    val mimeType: String
) {
    WORD(
        id = "word",
        titleRes = R.string.create_type_word_title,
        subtitleRes = R.string.create_type_word_subtitle,
        iconRes = R.drawable.ic_doc_generic,
        extension = ".docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ),
    PDF(
        id = "pdf",
        titleRes = R.string.create_type_pdf_title,
        subtitleRes = R.string.create_type_pdf_subtitle,
        iconRes = R.drawable.ic_doc_generic,
        extension = ".pdf",
        mimeType = "application/pdf"
    ),
    TEXT(
        id = "text",
        titleRes = R.string.create_type_text_title,
        subtitleRes = R.string.create_type_text_subtitle,
        iconRes = R.drawable.ic_doc_generic,
        extension = ".txt",
        mimeType = "text/plain"
    ),
    MARKDOWN(
        id = "markdown",
        titleRes = R.string.create_type_markdown_title,
        subtitleRes = R.string.create_type_markdown_subtitle,
        iconRes = R.drawable.ic_doc_generic,
        extension = ".md",
        mimeType = "text/markdown"
    )
}