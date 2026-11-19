package com.sanad.bond.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.data.db.SanadDatabase
import com.sanad.bond.data.model.DocContent
import com.sanad.bond.data.model.DocMeta
import com.sanad.bond.data.model.DocType
import com.sanad.bond.data.model.DocumentModel
import com.sanad.bond.services.document.DocxExporter
import com.sanad.bond.services.document.DocxImporter
import com.sanad.bond.services.document.PdfExporter
import com.sanad.bond.services.document.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DocumentRepository {

    private const val TAG = "DocumentRepository"

    private fun getDb(context: Context): SanadDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            SanadDatabase::class.java,
            "sanad.db"
        )
            .fallbackToDestructiveMigration()
            .build()

    suspend fun load(context: Context, uri: Uri, fileName: String): DocumentModel =
        withContext(Dispatchers.IO) {
            val db = getDb(context)
            val docType = getDocTypeFromFileName(fileName)
            val uriString = uri.toString()
            LogManager.i(TAG, "Loading $fileName as ${docType.name}")

            // 1. HTML snapshot from DB via raw query (avoids DAO API mismatch)
            val storedHtml: String? = try {
                db.query(
                    "SELECT html FROM document_html WHERE uri = ? LIMIT 1",
                    arrayOf<Any>(uriString)
                ).use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (t: Throwable) {
                LogManager.e(TAG, "Read HTML from DB failed", t)
                null
            }

            if (!storedHtml.isNullOrEmpty()) {
                LogManager.d(TAG, "Loaded HTML from DB (${storedHtml.length} chars)")
                return@withContext DocumentModel(
                    meta = DocMeta(fileName, uriString, docType),
                    content = DocContent(storedHtml, stripHtml(storedHtml))
                )
            }

            // 2. Fallback to native import
            val content = try {
                when (docType) {
                    DocType.WORD -> DocxImporter.import(context, uri)
                    DocType.PDF -> PdfImporter.import(context, uri)
                    DocType.MARKDOWN, DocType.TEXT -> {
                        val text = readText(context, uri)
                        DocContent.fromPlainText(text)
                    }
                }
            } catch (t: Throwable) {
                LogManager.e(TAG, "Native import failed", t)
                DocContent(html = "<p></p>", plainText = "")
            }

            DocumentModel(
                meta = DocMeta(fileName, uriString, docType),
                content = content
            )
        }

    suspend fun save(context: Context, doc: DocumentModel): DocumentModel =
        withContext(Dispatchers.IO) {
            val uriString = doc.meta.fileUri ?: error("Document has no URI")
            val uri = Uri.parse(uriString)
            val db = getDb(context)
            LogManager.i(TAG, "Saving ${doc.meta.fileName} as ${doc.meta.docType.name}")

            // 1. Binary
            when (doc.meta.docType) {
                DocType.WORD -> DocxExporter.export(context, uri, doc.content)
                DocType.PDF -> PdfExporter.export(context, uri, doc.content)
                DocType.TEXT, DocType.MARKDOWN ->
                    writeText(context, uri, doc.content.plainText)
            }

            // 2. HTML to DB via raw query (INSERT OR REPLACE)
            try {
                db.query(
                    "INSERT OR REPLACE INTO document_html (uri, html, updatedAt) VALUES (?, ?, ?)",
                    arrayOf<Any>(uriString, doc.content.html, System.currentTimeMillis())
                )
                LogManager.d(TAG, "HTML saved to DB (${doc.content.html.length} chars)")
            } catch (t: Throwable) {
                LogManager.e(TAG, "Save HTML to DB failed", t)
            }

            doc.copy(meta = doc.meta.copy(modifiedAt = System.currentTimeMillis()))
        }

    private fun getDocTypeFromFileName(name: String): DocType = when {
        name.endsWith(".docx", true) -> DocType.WORD
        name.endsWith(".pdf", true) -> DocType.PDF
        name.endsWith(".md", true) -> DocType.MARKDOWN
        else -> DocType.TEXT
    }

    private fun readText(context: Context, uri: Uri): String =
        if (uri.scheme == "file") {
            java.io.File(requireNotNull(uri.path)).readText()
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: ""
        }

    private fun writeText(context: Context, uri: Uri, text: String) {
        if (uri.scheme == "file") {
            java.io.File(requireNotNull(uri.path)).writeText(text)
        } else {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun stripHtml(html: String): String = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
}