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

        inputStream.use { stream ->
            val document = PDDocument.load(stream)
            val stripper = PDFTextStripper()
            
            // استخراج النص صفحة بصفحة
            val htmlBuilder = StringBuilder()
            val plainTextBuilder = StringBuilder()
            val numPages = document.numberOfPages

            for (page in 1..numPages) {
                stripper.startPage = page
                stripper.endPage = page
                val pageText = stripper.getText(document)
                
                htmlBuilder.append("<h3>صفحة $page</h3>")
                htmlBuilder.append("<p>${pageText.replace("\n", "<br/>")}</p>")
                plainTextBuilder.append(pageText).append("\n\n---\n\n")
            }

            document.close()
            return DocContent(
                html = htmlBuilder.toString().ifBlank { "<p></p>" },
                plainText = plainTextBuilder.toString().trim()
            )
        }
    }
}