package com.sanad.bond.data.model

import android.net.Uri

/**
 * Result of loading a document for the editor.
 *
 * The editor picks the correct UI mode based on the concrete subtype:
 *   - [Editable]   → EditText that can save
 *   - [ReadOnly]   → TextView with an info banner (e.g. DOCX preview)
 *   - [External]   → open with another app (e.g. PDF)
 *   - [Error]      → centered error message
 */
sealed class EditorContent {

    data class Editable(val text: String) : EditorContent()

    data class ReadOnly(val text: String, val reason: String) : EditorContent()

    data class External(val uri: Uri) : EditorContent()

    data class Error(val message: String) : EditorContent()
}