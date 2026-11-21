package com.sanad.bond.services.document

import android.content.Context
import android.net.Uri
import com.sanad.bond.data.model.DocContent
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfImporter {

    fun import(context: Context, uri: Uri): DocContent {
        val inputStream = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).inputStream()
        } else {
            context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF")
        }

        // FOUNDATION FIXES:
        //  1. PDDocument is now closed in a finally — previously only on the
        //     happy path, so an encrypted / corrupt PDF that made
        //     PDFTextStripper.getText throw leaked the PDDocument.
        //  2. Extracted text is HTML-escaped before being wrapped in <p>…</p>.
        //     PDFs routinely contain <, >, & (form fields, code listings);
        //     concatenating them verbatim produced broken HTML that Tiptap
        //     mis-parsed.
        inputStream.use { stream ->
            val document = PDDocument.load(stream)
            try {
                val stripper = PDFTextStripper()

                val htmlBuilder = StringBuilder()
                val plainTextBuilder = StringBuilder()
                val numPages = document.numberOfPages

                for (page in 1..numPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val pageText = stripper.getText(document)

                    htmlBuilder.append("<h3>")
                        .append(escapeHtml("صفحة $page"))
                        .append("</h3>")
                    // \n → <br/>, and escape everything else.
                    val escaped = escapeHtml(pageText)
                    htmlBuilder.append("<p>")
                        .append(escaped.replace("\n", "<br/>"))
                        .append("</p>")
                    plainTextBuilder.append(pageText).append("\n\n---\n\n")
                }

                return DocContent(
                    html = htmlBuilder.toString().ifBlank { "<p></p>" },
                    plainText = plainTextBuilder.toString().trim()
                )
            } finally {
                document.close()
            }
        }
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
