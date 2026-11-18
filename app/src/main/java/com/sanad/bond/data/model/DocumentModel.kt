package com.sanad.bond.data.model

/**
 * النموذج الموحد لكل الفئات الأربعة (Word, PDF, Text, Markdown).
 * المحرر يتعامل مع DocumentModel فقط — لا POI، لا PDFBox، لا Compose.
 * HTML هي الصيغة الداخلية (الوحيدة القابلة للتحويل بلا فقدان تنسيق).
 */
data class DocumentModel(
    val meta: DocMeta,
    val content: DocContent
)

data class DocMeta(
    val fileName: String,
    val fileUri: String?,           // null = مستند جديد غير محفوظ
    val docType: DocType,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val pageSize: PageSize = PageSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val language: String = "system"
)

data class DocContent(
    val html: String = "<p></p>",
    val plainText: String = ""
) {
    fun isEmpty(): Boolean = plainText.isBlank()

    fun wordCount(): Int =
        if (plainText.isBlank()) 0
        else plainText.trim().split(Regex("\\s+")).size

    fun charCount(): Int = plainText.length

    companion object {
        fun fromPlainText(text: String): DocContent {
            val escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>")
            return DocContent("<p>$escaped</p>", text)
        }
    }
}

enum class DocType(val extension: String, val mimeType: String) {
    WORD(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF(".pdf", "application/pdf"),
    TEXT(".txt", "text/plain"),
    MARKDOWN(".md", "text/markdown");

    companion object {
        fun fromMimeType(mime: String): DocType = when {
            mime.contains("wordprocessingml", true) -> WORD
            mime == "application/pdf" -> PDF
            mime == "text/markdown" -> MARKDOWN
            mime.startsWith("text/") -> TEXT
            else -> TEXT
        }
    }
}

enum class PageSize(val widthPt: Int, val heightPt: Int) {
    A4(595, 842), A5(420, 595), A3(842, 1191),
    LETTER(612, 792), LEGAL(612, 1008)
}

enum class Orientation { PORTRAIT, LANDSCAPE }