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

object DocxExporter {
    private const val TAG = "DocxExporter"

    private val PAGE_SIZES_TWIPS: Map<String, Pair<Int, Int>> = mapOf(
        "A4" to (11906 to 16838),
        "A5" to (8391 to 11906),
        "A3" to (16838 to 23811),
        "Letter" to (12240 to 15840),
        "Legal" to (12240 to 20160)
    )

    private val MARGIN_TWIPS: Map<String, Int> = mapOf(
        "narrow" to 720,
        "normal" to 1440,
        "wide" to 2880
    )

    fun export(context: Context, uri: Uri, content: DocContent) {
        val outputStream = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).outputStream()
        } else {
            context.contentResolver.openOutputStream(uri, "wt")
                ?: throw java.io.IOException("Cannot write DOCX: $uri")
        }

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
            "DOCX page: $pageSizeName $orientationName (${finalWidth}x${finalHeight} twips), margins=$marginTwips"
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
            // word/_rels/document.xml.rels — required by the OOXML spec;
            // some Word versions prompt to "repair" the file when it is
            // missing. Previously omitted.
            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zip.write(DOCX_DOCUMENT_RELS.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            // word/styles.xml — defines the Normal style. Without it Word
            // falls back to its default style table, which on some locales
            // renders Arabic in a non-Arabic font.
            zip.putNextEntry(ZipEntry("word/styles.xml"))
            zip.write(DOCX_STYLES.toByteArray(Charsets.UTF_8))
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

        // Arabic font from Settings → Fonts. "system"/empty means let Word
        // choose; otherwise we emit <w:rFonts w:cs="…"/> so Word uses a font
        // that actually has Arabic glyphs (the default Calibri may not).
        val arabicFont = SettingsRepository.getString(
            SettingsKeys.FONTS_DEFAULT_ARABIC, ""
        ).trim()
        val rFontsXml = if (arabicFont.isNotEmpty() && arabicFont != "system") {
            """<w:rFonts w:ascii="$arabicFont" w:hAnsi="$arabicFont" w:cs="$arabicFont"/>"""
        } else ""

        val chunks = normalized.split(
            Regex("(?i)</?(p|h1|h2|h3|h4|li|ul|ol|div|blockquote|pre)[^>]*>")
        )
        for (raw in chunks) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val runs = parseRuns(trimmed)
            if (runs.isEmpty()) continue

            // RTL detection: if any run in this paragraph contains Arabic /
            // Hebrew, mark the paragraph and every run as RTL so Word
            // shapes and reorders correctly.
            val paragraphRtl = runs.any { isRtl(it.text) }

            sb.append("<w:p>")
            if (paragraphRtl) {
                // <w:bidi/> must come inside <w:pPr>; without it Word draws
                // the paragraph LTR even if the runs are marked RTL.
                sb.append("<w:pPr><w:bidi/></w:pPr>")
            }
            for (run in runs) {
                if (run.text.isEmpty()) continue
                sb.append("<w:r>")
                val rPr = buildString {
                    if (rFontsXml.isNotEmpty()) append(rFontsXml)
                    if (run.bold) append("<w:b/>")
                    if (run.italic) append("<w:i/>")
                    if (run.underline) append("<w:u w:val=\"single\"/>")
                    if (run.strike) append("<w:strike/>")
                    if (paragraphRtl) append("<w:rtl w:val=\"1\"/>")
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
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /**
     * True if the paragraph contains any Arabic / Hebrew character — used
     * to decide whether to emit `<w:bidi/>` (RTL paragraph) and `<w:rtl/>`
     * (RTL run). Without these Word renders Arabic LTR and reordering is
     * unreliable, which is fatal for an Arabic-first app.
     */
    private fun isRtl(text: String): Boolean {
        for (ch in text) {
            val c = ch.code
            // Arabic block (0x0600–0x06FF) + Supplement (0x0750–0x077F) +
            // Hebrew (0x0590–0x05FF)
            if (c in 0x0600..0x06FF || c in 0x0750..0x077F || c in 0x0590..0x05FF) {
                return true
            }
        }
        return false
    }

    private fun decodeEntities(text: String): String = text
        .replace(" ", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    private val DOCX_CONTENT_TYPES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
        <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
        <Default Extension="xml" ContentType="application/xml"/>
        <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
        </Types>
    """.trimIndent()

    private val DOCX_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()

    private val DOCX_DOCUMENT_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private val DOCX_STYLES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
        <w:docDefaults>
            <w:rPrDefault>
            <w:rPr>
                <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Arial"/>
                <w:sz w:val="22"/>
                <w:szCs w:val="22"/>
                <w:lang w:val="ar-SA" w:bidi="ar-SA"/>
            </w:rPr>
            </w:rPrDefault>
            <w:pPrDefault>
            <w:pPr>
                <w:spacing w:after="160" w:line="259" w:lineRule="auto"/>
            </w:pPr>
            </w:pPrDefault>
        </w:docDefaults>
        <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
            <w:name w:val="Normal"/>
            <w:qFormat/>
        </w:style>
        </w:styles>
    """.trimIndent()
}