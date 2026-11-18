package com.sanad.bond.services.document

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.sanad.bond.data.model.DocContent
import com.sanad.bond.core.logging.LogManager
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.zip.ZipInputStream

object DocxImporter {

    private const val TAG = "DocxImporter"
    private const val NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    fun import(context: Context, uri: Uri): DocContent {
        val inputStream = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
                ?: error("Cannot open DOCX: $uri")
        }

        var documentXml: String? = null
        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        documentXml = zis.readBytes().toString(Charsets.UTF_8)
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (t: Throwable) {
            LogManager.e(TAG, "Failed to read DOCX zip", t)
            return DocContent(html = "<p></p>", plainText = "")
        }

        val xml = documentXml ?: run {
            LogManager.w(TAG, "No word/document.xml found in DOCX")
            return DocContent(html = "<p></p>", plainText = "")
        }

        return try {
            parseDocumentXml(xml)
        } catch (t: Throwable) {
            LogManager.e(TAG, "Failed to parse document.xml", t)
            DocContent(html = "<p></p>", plainText = "")
        }
    }

    private fun parseDocumentXml(xml: String): DocContent {
        val html = StringBuilder()
        val plain = StringBuilder()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var currentParaStyle = ""
        var currentParaAlign = ""
        var currentParagraphHtml = StringBuilder()
        var currentParagraphPlain = StringBuilder()
        var inRun = false
        var runBold = false
        var runItalic = false
        var runUnderline = false
        var runStrike = false
        var runColor = ""
        var runSize = ""
        var runFont = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val ns = parser.namespace
            val localName = parser.name

            if (ns == NS_W) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (localName) {
                            "p" -> {
                                currentParagraphHtml = StringBuilder()
                                currentParagraphPlain = StringBuilder()
                                currentParaStyle = ""
                                currentParaAlign = ""
                            }
                            "pStyle" -> currentParaStyle = parser.getAttr("val")
                            "jc" -> currentParaAlign = parser.getAttr("val")
                            "r" -> {
                                inRun = true
                                runBold = false; runItalic = false; runUnderline = false
                                runStrike = false; runColor = ""; runSize = ""; runFont = ""
                            }
                            "b" -> runBold = true
                            "i" -> runItalic = true
                            "u" -> runUnderline = true
                            "strike" -> runStrike = true
                            "color" -> runColor = parser.getAttr("val")
                            "sz" -> runSize = parser.getAttr("val")
                            "rFonts" -> runFont = parser.getAttr("ascii")
                            "t" -> {
                                // SAFE: nextText can throw on empty <w:t/> tags.
                                // Empty text tags are common in Word docs.
                                val text = try {
                                    parser.nextText()
                                } catch (_: Throwable) {
                                    ""
                                }
                                if (inRun && text.isNotEmpty()) {
                                    currentParagraphHtml.append(
                                        applyRunStyle(
                                            text, runBold, runItalic, runUnderline,
                                            runStrike, runColor, runSize, runFont
                                        )
                                    )
                                    currentParagraphPlain.append(text)
                                }
                            }
                            "br" -> {
                                if (inRun) {
                                    currentParagraphHtml.append("<br/>")
                                    currentParagraphPlain.append("\n")
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (localName) {
                            "p" -> {
                                val tagName = when {
                                    currentParaStyle.startsWith("Heading1") -> "h1"
                                    currentParaStyle.startsWith("Heading2") -> "h2"
                                    currentParaStyle.startsWith("Heading3") -> "h3"
                                    else -> "p"
                                }
                                val align = when (currentParaAlign) {
                                    "center" -> "center"
                                    "right" -> "right"
                                    "left" -> "left"
                                    "both" -> "justify"
                                    else -> ""
                                }
                                val styleAttr = if (align.isNotEmpty()) {
                                    " style=\"text-align: $align;\""
                                } else ""
                                html.append("<$tagName$styleAttr>")
                                    .append(currentParagraphHtml)
                                    .append("</$tagName>")
                                plain.append(currentParagraphPlain).append("\n")
                            }
                            "r" -> inRun = false
                        }
                    }
                }
            }

            eventType = try {
                parser.next()
            } catch (t: Throwable) {
                LogManager.w(TAG, "Malformed XML — stopping parse", t)
                break
            }
        }

        val finalHtml = html.toString().ifBlank { "<p></p>" }
        val finalPlain = plain.toString().trim()
        return DocContent(finalHtml, finalPlain)
    }

    private fun XmlPullParser.getAttr(name: String): String {
        return getAttributeValue(NS_W, name)
            ?: getAttributeValue(null, "w:$name")
            ?: getAttributeValue(null, name)
            ?: ""
    }

    private fun applyRunStyle(
        text: String, bold: Boolean, italic: Boolean, underline: Boolean, strike: Boolean,
        color: String, size: String, font: String
    ): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val styles = mutableListOf<String>()
        if (bold) styles.add("font-weight: bold;")
        if (italic) styles.add("font-style: italic;")
        if (underline || strike) {
            val decos = mutableListOf<String>()
            if (underline) decos.add("underline")
            if (strike) decos.add("line-through")
            styles.add("text-decoration: ${decos.joinToString(" ")};")
        }
        if (color.isNotEmpty() && color != "auto") styles.add("color: #$color;")
        if (size.isNotEmpty()) {
            val pt = (size.toIntOrNull() ?: 0) / 2
            if (pt > 0) styles.add("font-size: ${pt}pt;")
        }
        if (font.isNotEmpty()) styles.add("font-family: $font;")

        return if (styles.isEmpty()) escaped
        else "<span style=\"${styles.joinToString(" ")}\">$escaped</span>"
    }
}