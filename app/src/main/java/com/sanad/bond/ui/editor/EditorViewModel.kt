package com.sanad.bond.ui.editor

import android.app.Application
import android.content.res.Configuration
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanad.bond.data.model.DocContent
import com.sanad.bond.data.model.DocumentModel
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.DocumentRepository
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.core.logging.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<EditorState>(EditorState.Loading)
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // One-shot flag: set true when the last save migrated the file to
    // app storage. EditorScreen reads it and shows a toast, then resets.
    private val _migrationNotice = MutableStateFlow(false)
    val migrationNotice: StateFlow<Boolean> = _migrationNotice.asStateFlow()

    // ---- Read all editor-related settings at construction ----
    // These are read once. The user can change them mid-edit via Settings,
    // but in practice that's rare and re-reading would complicate state.

    val defaultFontSize: String =
        SettingsRepository.getString(SettingsKeys.FONTS_DEFAULT_SIZE_SP, "16")

    val isDarkMode: Boolean = resolveDarkMode()

    val language: String =
        SettingsRepository.getString(SettingsKeys.GENERAL_LANGUAGE, "system")

    val editorZoom: Int =
        SettingsRepository.getString(SettingsKeys.EDITOR_DEFAULT_ZOOM, "100")
            .toIntOrNull()?.coerceIn(50, 200) ?: 100

    val showLineNumbers: Boolean =
        SettingsRepository.getBoolean(SettingsKeys.EDITOR_SHOW_LINE_NUMBERS, false)

    val showRuler: Boolean =
        SettingsRepository.getBoolean(SettingsKeys.EDITOR_SHOW_RULER, false)

    val showStatusBar: Boolean =
        SettingsRepository.getBoolean(SettingsKeys.EDITOR_SHOW_STATUS_BAR, true)

    val wordWrap: Boolean =
        SettingsRepository.getBoolean(SettingsKeys.EDITOR_WORD_WRAP, true)

    val spellCheck: Boolean =
        SettingsRepository.getBoolean(SettingsKeys.EDITOR_SPELL_CHECK, true)

    val autosaveEnabled: Boolean =
        SettingsRepository.getBoolean(SettingsKeys.EDITOR_AUTOSAVE_ENABLED, true)

    val autosaveIntervalSec: Int =
        SettingsRepository.getString(SettingsKeys.EDITOR_AUTOSAVE_INTERVAL_SEC, "30")
            .toIntOrNull()?.coerceIn(15, 300) ?: 30

    private var currentDoc: DocumentModel? = null
    private var initialHtml: String = ""

    // Prevents manual Save and auto-save from running simultaneously,
    // which would race on DocumentRepository.save() and double-write.
    private val saveInProgress = AtomicBoolean(false)

    private fun resolveDarkMode(): Boolean =
        when (SettingsRepository.getString(SettingsKeys.GENERAL_THEME, "system")) {
            "dark" -> true
            "light" -> false
            else -> {
                val uiMode = getApplication<Application>().resources.configuration.uiMode
                (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }

    fun load(uri: Uri, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _state.value = EditorState.Loading
            LogManager.i(TAG, "Load started: $fileName ($mimeType)")
            try {
                val doc = DocumentRepository.load(getApplication(), uri, fileName)
                currentDoc = doc
                initialHtml = doc.content.html
                _state.value = EditorState.Ready(doc)
                LogManager.i(
                    TAG,
                    "Load succeeded: $fileName (html=${doc.content.html.length} chars)"
                )
            } catch (t: Throwable) {
                LogManager.e(TAG, "Load failed: $fileName", t)
                _state.value = EditorState.Error(
                    t.message ?: "Failed to open document"
                )
            }
        }
    }

    fun onContentChanged(html: String, plainText: String) {
        val current = currentDoc ?: return
        val updated = current.copy(
            meta = current.meta.copy(modifiedAt = System.currentTimeMillis()),
            content = DocContent(html = html, plainText = plainText)
        )
        currentDoc = updated
        _state.value = EditorState.Ready(updated)
    }

    fun saveWithHtml(html: String) {
        val current = currentDoc ?: return
        if (html.isBlank()) {
            LogManager.w(TAG, "saveWithHtml: empty html — refusing to overwrite")
            return
        }
        val plain = stripHtml(html)
        currentDoc = current.copy(
            content = DocContent(html = html, plainText = plain)
        )
        save()
    }

    /**
     * Silent auto-save triggered by the WebView timer (EDITOR_AUTOSAVE_*).
     *
     * Behaviour:
     *   - Skips if a manual save is in progress
     *   - Skips if the incoming HTML is blank
     *   - Skips if the data-loss guard in DocumentRepository rejects it
     *   - Does NOT flash a "Saving" state — stays on Ready so the user
     *     doesn't see spurious progress indicators while typing
     */
    fun autoSaveWithHtml(html: String) {
        if (html.isBlank()) {
            LogManager.d(TAG, "Auto-save skipped: blank html")
            return
        }
        if (!saveInProgress.compareAndSet(false, true)) {
            LogManager.d(TAG, "Auto-save skipped: another save in progress")
            return
        }
        viewModelScope.launch {
            try {
                val current = currentDoc ?: return@launch
                val plain = stripHtml(html)
                val updated = current.copy(
                    meta = current.meta.copy(modifiedAt = System.currentTimeMillis()),
                    content = DocContent(html = html, plainText = plain)
                )
                currentDoc = updated

                val saved = DocumentRepository.save(getApplication(), updated)
                val uriString = saved.meta.fileUri ?: return@launch

                if (saved.meta.fileUri != current.meta.fileUri) {
                    LogManager.i(
                        TAG,
                        "Auto-save migrated: ${current.meta.fileUri} → ${saved.meta.fileUri}"
                    )
                    _migrationNotice.value = true
                }

                val reloaded = DocumentRepository.load(
                    getApplication(),
                    Uri.parse(uriString),
                    saved.meta.fileName
                )
                initialHtml = reloaded.content.html
                currentDoc = reloaded
                _state.value = EditorState.Ready(reloaded)
                LogManager.i(
                    TAG,
                    "Auto-save succeeded: ${saved.meta.fileName} " +
                            "(${reloaded.content.html.length} chars)"
                )
            } catch (e: IllegalStateException) {
                // Data-loss guard triggered — skip silently
                LogManager.w(TAG, "Auto-save skipped by guard: ${e.message}")
            } catch (t: Throwable) {
                // Don't surface to user — auto-save failures shouldn't
                // interrupt typing. Next manual save will surface the error.
                LogManager.e(TAG, "Auto-save failed (silent)", t)
            } finally {
                saveInProgress.set(false)
            }
        }
    }

    fun save() {
        if (!saveInProgress.compareAndSet(false, true)) {
            LogManager.d(TAG, "Save skipped: another save in progress")
            return
        }
        viewModelScope.launch {
            try {
                val doc = currentDoc ?: return@launch
                val oldUri = doc.meta.fileUri
                _state.value = EditorState.Saving(doc)
                LogManager.i(TAG, "Save started: ${doc.meta.fileName}")

                val saved = DocumentRepository.save(getApplication(), doc)

                if (saved.meta.fileUri != oldUri) {
                    LogManager.i(
                        TAG,
                        "Save migrated file: $oldUri → ${saved.meta.fileUri}"
                    )
                    _migrationNotice.value = true
                }

                val uriString = saved.meta.fileUri ?: error("Saved doc has no URI")
                val reloaded = DocumentRepository.load(
                    getApplication(),
                    Uri.parse(uriString),
                    saved.meta.fileName
                )
                initialHtml = reloaded.content.html
                currentDoc = reloaded
                _state.value = EditorState.Ready(reloaded)

                LogManager.i(
                    TAG,
                    "Save succeeded: ${saved.meta.fileName} " +
                            "(${reloaded.content.html.length} chars)"
                )
            } catch (t: Throwable) {
                LogManager.e(TAG, "Save failed", t)
                val doc = currentDoc
                if (doc != null) {
                    _state.value = EditorState.SaveError(
                        t.message ?: "Save failed", doc
                    )
                }
            } finally {
                saveInProgress.set(false)
            }
        }
    }

    fun acknowledgeMigration() {
        _migrationNotice.value = false
    }

    fun isDirty(): Boolean = currentDoc?.content?.html != initialHtml

    private fun stripHtml(html: String): String = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("&nbsp;"), " ")
        .replace(Regex("&amp;"), "&")
        .replace(Regex("&lt;"), "<")
        .replace(Regex("&gt;"), ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val TAG = "EditorViewModel"
    }
}

sealed class EditorState {
    data object Loading : EditorState()
    data class Ready(val doc: DocumentModel) : EditorState()
    data class Saving(val doc: DocumentModel) : EditorState()
    data class SaveError(val message: String, val doc: DocumentModel) : EditorState()
    data class Error(val message: String) : EditorState()
}