package com.sanad.bond.services.document

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Html
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.sanad.bond.data.model.DocContent
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.core.logging.LogManager
import java.io.File

/**
 * Exports DocContent to a real PDF using Android's built-in PdfDocument.
 *
 * Page size and orientation are read from Settings → PDF and applied to
 * every exported file. RTL is auto-detected from the first non-whitespace
 * character so Arabic content renders correctly.
 *
 * Images / tables / custom colors are not rendered — those are handled by
 * html2pdf.js in the WebView when it's available. This exporter is the
 * guaranteed-offline fallback.
 *
 * Note on compression: Android's PdfDocument does not expose a compression
 * level API — content streams are compressed at the framework level with
 * a fixed algorithm. The PDF_COMPRESSION_LEVEL setting is therefore a no-op
 * for this exporter; it will apply to the html2pdf.js path when that is
 * enabled at runtime.
 */
object PdfExporter {

    private const val TAG = "PdfExporter"
    private const val MARGIN = 40
    private const val TEXT_SIZE = 12f
    private const val LINE_SPACING_MULT = 1.3f
    private const val MAX_PAGES = 200

    /** Page dimensions in points (1 pt = 1/72 inch). Width × Height. */
    private val PAGE_SIZES_PT: Map<String, Pair<Int, Int>> = mapOf(
        "A4" to (595 to 842),
        "A5" to (420 to 595),
        "A3" to (842 to 1191),
        "Letter" to (612 to 792),
        "Legal" to (612 to 1008)
    )

    fun export(context: Context, uri: Uri, content: DocContent) {
        // NOTE: openOutputStream may throw SecurityException if the SAF
        // grant has expired. We let it propagate so DocumentRepository
        // can catch it and fall back to app-specific storage.
        val outputStream = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).outputStream()
        } else {
            context.contentResolver.openOutputStream(uri, "wt")
                ?: throw java.io.IOException("Cannot write PDF: $uri")
        }

        // Read the user's PDF settings — the toggles in Settings → PDF
        // now have a real effect on the output file.
        val pageSizeName = SettingsRepository.getString(
            SettingsKeys.PDF_DEFAULT_PAGE_SIZE, "A4"
        )
        val orientationName = SettingsRepository.getString(
            SettingsKeys.PDF_DEFAULT_ORIENTATION, "portrait"
        )

        val (baseWidth, baseHeight) = PAGE_SIZES_PT[pageSizeName]
            ?: PAGE_SIZES_PT.getValue("A4")

        val (pageWidth, pageHeight) = if (orientationName == "landscape") {
            baseHeight to baseWidth
        } else {
            baseWidth to baseHeight
        }

        LogManager.d(
            TAG,
            "PDF page: $pageSizeName $orientationName (${pageWidth}x${pageHeight} pt)"
        )

        val pdfDocument = PdfDocument()
        try {
            val spanned: Spanned = Html.fromHtml(
                content.html,
                Html.FROM_HTML_MODE_LEGACY
            ) as Spanned

            LogManager.d(
                TAG,
                "Parsed PDF text (${spanned.length} chars): \"${spanned.toString().take(60)}\""
            )

            val paint = TextPaint().apply {
                isAntiAlias = true
                textSize = TEXT_SIZE
                color = Color.BLACK
            }

            val contentWidth = pageWidth - 2 * MARGIN
            val contentHeight = pageHeight - 2 * MARGIN
            val totalLen = spanned.length

            if (totalLen == 0) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                pdfDocument.finishPage(page)
                pdfDocument.writeTo(outputStream)
                LogManager.i(TAG, "Wrote empty PDF (1 page, ${pageWidth}x${pageHeight})")
                return
            }

            val direction = detectTextDirection(spanned)
            LogManager.d(TAG, "Text direction: $direction")

            var pageNumber = 1
            var start = 0

            while (start < totalLen) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    pageWidth, pageHeight, pageNumber
                ).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                val layout = StaticLayout.Builder
                    .obtain(spanned, start, totalLen, paint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(direction)
                    .setLineSpacing(0f, LINE_SPACING_MULT)
                    .setIncludePad(false)
                    .build()

                val lastVisibleLine = if (layout.height <= contentHeight) {
                    layout.lineCount - 1
                } else {
                    var lo = 0
                    var hi = layout.lineCount - 1
                    var found = 0
                    while (lo <= hi) {
                        val mid = (lo + hi) / 2
                        if (layout.getLineBottom(mid) <= contentHeight) {
                            found = mid
                            lo = mid + 1
                        } else hi = mid - 1
                    }
                    found
                }

                canvas.save()
                canvas.translate(MARGIN.toFloat(), MARGIN.toFloat())
                canvas.clipRect(0, 0, contentWidth, contentHeight)
                layout.draw(canvas)
                canvas.restore()

                pdfDocument.finishPage(page)

                val nextStart = if (lastVisibleLine < layout.lineCount - 1) {
                    layout.getLineStart(lastVisibleLine + 1)
                } else {
                    totalLen
                }
                if (nextStart <= start) break
                start = nextStart
                pageNumber++
                if (pageNumber > MAX_PAGES) {
                    LogManager.w(
                        TAG,
                        "Reached MAX_PAGES ($MAX_PAGES) — truncating output"
                    )
                    break
                }
            }

            pdfDocument.writeTo(outputStream)
            LogManager.i(
                TAG,
                "PDF written: ${pageNumber - 1} page(s), " +
                    "$pageSizeName $orientationName, " +
                    "$totalLen chars"
            )
        } finally {
            try { pdfDocument.close() } catch (_: Throwable) {}
            try { outputStream.close() } catch (_: Throwable) {}
        }
    }

    /**
     * يكتشف إذا كان النص عربي/RTL أم لا، بناءً على أول حرف غير مسافة.
     *
     * Return type is TextDirectionHeuristic (interface) — that's what
     * StaticLayout.setTextDirection expects. TextDirectionHeuristics (with s)
     * is just the holder class for the static LTR/RTL instances.
     */
    private fun detectTextDirection(spanned: Spanned): TextDirectionHeuristic {
        val firstChar = spanned.firstOrNull { !it.isWhitespace() }
            ?: return TextDirectionHeuristics.LTR
        return when (Character.getDirectionality(firstChar)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC ->
                TextDirectionHeuristics.RTL
            else -> TextDirectionHeuristics.LTR
        }
    }
}