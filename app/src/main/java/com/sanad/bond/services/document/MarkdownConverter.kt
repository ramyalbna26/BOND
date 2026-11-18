package com.sanad.bond.services.document

import com.sanad.bond.data.model.DocContent

object MarkdownConverter {

    fun toMarkdown(html: String): String {
        return html
            .replace("<h1>", "# ").replace("</h1>", "\n")
            .replace("<h2>", "## ").replace("</h2>", "\n")
            .replace("<h3>", "### ").replace("</h3>", "\n")
            .replace("<strong>", "**").replace("</strong>", "**")
            .replace("<em>", "*").replace("</em>", "*")
            .replace("<p>", "").replace("</p>", "\n\n")
            .replace("<br>", "\n")
            .replace(Regex("<[^>]*>"), "")
            .trim()
    }

    fun fromMarkdown(markdown: String): DocContent {
        var html = markdown
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace(Regex("^# (.*?)$", RegexOption.MULTILINE), "<h1>$1</h1>")
            .replace(Regex("^## (.*?)$", RegexOption.MULTILINE), "<h2>$1</h2>")
            .replace(Regex("^### (.*?)$", RegexOption.MULTILINE), "<h3>$1</h3>")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.*?)\\*"), "<em>$1</em>")
            .replace("\n\n", "</p><p>")
            
        if (!html.startsWith("<")) html = "<p>$html</p>"

        return DocContent(
            html = html,
            plainText = markdown
        )
    }
}