package com.sanad.bond.services.document

import android.content.Context
import android.net.Uri
import com.sanad.bond.data.model.DocContent
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.core.logging.LogManager
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports DocContent to a DOCX with basic HTML → WordprocessingML mapping.
 *
 * Supported inline: <b>, <strong>, <i>, <em>, <u>, <s>, <br>.
 * Block: <p>, <h1>-<h4>, <li>. Everything else is stripped but text kept.
 *
 * Page size, orientation, and margins are read from Settings → Word
 * and applied to the document's <w:sectPr> section properties.
 *
 * Images / tables are out of scope for the offline fallback.
 */
object DocxExporter {

    private const val TAG = "DocxExporter"

    /** Page dimensions in twips (1 twip = 1/1440 inch). Width × Height. */
    private val PAGE_SIZES_TWIPS: Map<String, Pair<Int, Int>> = mapOf(
        "A4" to (11906 to 16838),
        "A5" to (8391 to 11906),
        "A3" to (16838 to 23811),
        "Letter" to (12240 to 15840),
        "Legal" to (12240 to 20160)
    )

    /** Uniform margins in twips. */
    private val MARGIN_TWIPS: Map<String, Int> = mapOf(
        "narrow" to 720,     // 0.5 inch
        "normal" to 1440,    // 1.0 inch
        "wide" to 2880       // 2.0 inch
    )

    fun export(context: Context, uri: Uri, content: DocContent) {
        val outputStream = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).outputStream()
        } else {
            context.contentResolver.openOutputStream(uri, "wt")
                ?: throw java.io.IOException("Cannot write DOCX: $uri")
        }

        // Read the user's Word settings — the toggles in Settings → Word
        // now have a real effect on the output file.
        val pageSizeName = SettingsRepository.getString(
            SettingsKeys.WORD_DEFAULT_PAGE_SIZE, "A4"
        )
        val orientationName = SettingsRepository.getString(
            SettingsKeys.WORD_DEFAULT_ORIENTATION, "portrait"
        )
        val marginsName = SettingsRepository.getString(
            SettingsKeys.WORD_DEFAULT_MARGINS, "normal"
        )

        val (baseWidth, baseHeight) = PAGE_SIZES_TWIPS[pageSizeName]
            ?: PAGE_SIZES_TWIPS.getValue("A4")

        val (finalWidth, finalHeight) = if (orientationName == "landscape") {
            baseHeight to baseWidth
        } else {
            baseWidth to baseHeight
        }

        val marginTwips = MARGIN_TWIPS[marginsName] ?: 1440

        LogManager.d(
            TAG,
            "DOCX page: $pageSizeName $orientationName " +
                "(${finalWidth}x${finalHeight} twips), margins=$marginTwips"
        )

        val documentXml = buildDocumentXml(
            html = content.html,
            pageWidth = finalWidth,
            pageHeight = finalHeight,
            margin = marginTwips
        )
        LogManager.d(TAG, "Generated document.xml: ${documentXml.length} chars")

        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(DOCX_CONTENT_TYPES.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(DOCX_RELS.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun buildDocumentXml(
        html: String,
        pageWidth: Int,
        pageHeight: Int,
        margin: Int
    ): String {
        val sb = StringBuilder()
        val normalized = html
            .replace("\r\n", "\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

        val chunks = normalized.split(
            Regex("(?i)</?(p|h1|h2|h3|h4|li|ul|ol|div|blockquote|pre)[^>]*>")
        )

        for (raw in chunks) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val runs = parseRuns(trimmed)
            if (runs.isEmpty()) continue
            sb.append("<w:p>")
            for (run in runs) {
                if (run.text.isEmpty()) continue
                sb.append("<w:r>")
                val rPr = buildString {
                    if (run.bold) append("<w:b/>")
                    if (run.italic) append("<w:i/>")
                    if (run.underline) append("<w:u w:val=\"single\"/>")
                    if (run.strike) append("<w:strike/>")
                }
                if (rPr.isNotEmpty()) {
                    sb.append("<w:rPr>").append(rPr).append("</w:rPr>")
                }
                sb.append("<w:t xml:space=\"preserve\">")
                sb.append(escapeXml(run.text))
                sb.append("</w:t></w:r>")
            }
            sb.append("</w:p>")
        }

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    $sb
                    <w:sectPr>
                        <w:pgSz w:w="$pageWidth" w:h="$pageHeight"/>
                        <w:pgMar w:top="$margin" w:right="$margin" w:bottom="$margin" w:left="$margin"/>
                    </w:sectPr>
                </w:body>
            </w:document>
        """.trimIndent()
    }

    private data class TextRun(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false
    )

    private fun parseRuns(html: String): List<TextRun> {
        val runs = mutableListOf<TextRun>()
        val buffer = StringBuilder()
        var bold = 0; var italic = 0; var underline = 0; var strike = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                runs.add(
                    TextRun(
                        text = decodeEntities(buffer.toString()),
                        bold = bold > 0, italic = italic > 0,
                        underline = underline > 0, strike = strike > 0
                    )
                )
                buffer.setLength(0)
            }
        }

        var i = 0
        while (i < html.length) {
            val c = html[i]
            if (c == '<') {
                val end = html.indexOf('>', i)
                if (end == -1) { buffer.append(c); i++; continue }
                val tag = html.substring(i + 1, end).trim().lowercase()
                val isClosing = tag.startsWith("/")
                val name = tag.removePrefix("/").substringBefore(' ').substringBefore('/')
                when (name) {
                    "b", "strong" -> { flush(); bold = if (isClosing) (bold - 1).coerceAtLeast(0) else bold + 1 }
                    "i", "em" -> { flush(); italic = if (isClosing) (italic - 1).coerceAtLeast(0) else italic + 1 }
                    "u" -> { flush(); underline = if (isClosing) (underline - 1).coerceAtLeast(0) else underline + 1 }
                    "s", "strike", "del" -> { flush(); strike = if (isClosing) (strike - 1).coerceAtLeast(0) else strike + 1 }
                    "br" -> buffer.append('\n')
                    "span", "font", "a", "code" -> flush()
                    else -> { /* skip */ }
                }
                i = end + 1
            } else { buffer.append(c); i++ }
        }
        flush()
        return runs
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

    private val DOCX_CONTENT_TYPES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
    """.trimIndent()

    private val DOCX_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()
}