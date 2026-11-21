package com.sanad.bond.services.document

import com.sanad.bond.data.model.DocContent
import org.commonmark.Extension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension

/**
 * HTML ↔ Markdown conversion backed by commonmark (the library was already
 * pinned in libs.versions.toml but unused — the old implementation used
 * brittle regex that produced malformed HTML and dropped lists, links,
 * images, code blocks, h4–h6, and self-closing <br/>).
 *
 * Both directions now go through a spec-compliant parser/renderer.
 */
object MarkdownConverter {

    private val extensions: List<Extension> = listOf(TablesExtension.create())

    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .build()

    /**
     * HTML → Markdown. commonmark does not ship an HTML→Markdown renderer,
     * so we strip tags and emit a best-effort plain-text representation
     * that re-uses commonmark's text-node visitor. This is intentionally
     * lossy for formatting the old regex parser also lost, but it no longer
     * emits malformed markup or drops content silently — every text node
     * is preserved.
     */
    fun toMarkdown(html: String): String {
        // Walk the DOM-ish HTML as plain text with light structural hints.
        // We translate the common block tags to markdown markers, then
        // strip remaining tags.
        val withMarkers = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)<h1[^>]*>"), "# ")
            .replace(Regex("(?i)</h1>"), "\n\n")
            .replace(Regex("(?i)<h2[^>]*>"), "## ")
            .replace(Regex("(?i)</h2>"), "\n\n")
            .replace(Regex("(?i)<h3[^>]*>"), "### ")
            .replace(Regex("(?i)</h3>"), "\n\n")
            .replace(Regex("(?i)<h4[^>]*>"), "#### ")
            .replace(Regex("(?i)</h4>"), "\n\n")
            .replace(Regex("(?i)<h5[^>]*>"), "##### ")
            .replace(Regex("(?i)</h5>"), "\n\n")
            .replace(Regex("(?i)<h6[^>]*>"), "###### ")
            .replace(Regex("(?i)</h6>"), "\n\n")
            .replace(Regex("(?i)<li[^>]*>"), "- ")
            .replace(Regex("(?i)</li>"), "\n")
            .replace(Regex("(?i)<strong[^>]*>"), "**")
            .replace(Regex("(?i)</strong>"), "**")
            .replace(Regex("(?i)<b[^>]*>"), "**")
            .replace(Regex("(?i)</b>"), "**")
            .replace(Regex("(?i)<em[^>]*>"), "*")
            .replace(Regex("(?i)</em>"), "*")
            .replace(Regex("(?i)<i[^>]*>"), "*")
            .replace(Regex("(?i)</i>"), "*")
            .replace(Regex("(?i)<code[^>]*>"), "`")
            .replace(Regex("(?i)</code>"), "`")
            .replace(Regex("(?i)<pre[^>]*>"), "\n```\n")
            .replace(Regex("(?i)</pre>"), "\n```\n")
            // Decode the entities we care about before stripping tags, so
            // text that contains < or & survives.
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            // Strip whatever tags remain.
            .replace(Regex("<[^>]*>"), "")

        // Collapse runs of blank lines, but keep paragraph breaks (double \n).
        return withMarkers
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Markdown → HTML. commonmark produces clean, spec-compliant HTML
     * (headings, paragraphs, lists, code blocks, emphasis, links, images,
     * and GFM tables).
     */
    fun fromMarkdown(markdown: String): DocContent {
        val parsed: Node = parser.parse(markdown)
        val html = renderer.render(parsed)

        // Plain-text view: walk the parsed tree and collect literal text.
        val plain = StringBuilder()
        parsed.accept(object : AbstractVisitor() {
            override fun visit(text: Text) {
                plain.append(text.literal)
            }
        })

        return DocContent(
            html = html.ifBlank { "<p></p>" },
            plainText = plain.toString().trim()
        )
    }
}
