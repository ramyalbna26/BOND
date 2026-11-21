package com.sanad.bond.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sanad.bond.core.logging.LogManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TiptapWebView(
    modifier: Modifier = Modifier,
    initialHtml: String = "",
    fontSize: String = "16",
    darkMode: Boolean = false,
    language: String = "system",
    editorZoom: Int = 100,
    showLineNumbers: Boolean = false,
    showRuler: Boolean = false,
    showStatusBar: Boolean = true,
    wordWrap: Boolean = true,
    spellCheck: Boolean = true,
    autosaveEnabled: Boolean = true,
    autosaveIntervalSec: Int = 30,
    imagePickerLauncher: ActivityResultLauncher<Intent>,
    onContentChanged: (String) -> Unit,
    onAutoSave: (String) -> Unit,
    onEditorReady: () -> Unit,
    onImageRequested: () -> Unit,
    webViewRef: (WebView) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                clearCache(true)
                clearHistory()
                clearFormData()

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus(View.FOCUS_DOWN)

                // Only request focus + show keyboard on a fresh ACTION_DOWN
                // when the WebView isn't already focused. The old listener
                // re-opened the keyboard on every touch (including taps to
                // position the cursor / select text) and interfered with
                // long-press selection handles.
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN && !v.isFocused) {
                        v.requestFocus(View.FOCUS_DOWN)
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                            as InputMethodManager
                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                    }
                    false
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Build the settings JSON with JSONObject so that
                        // initialHtml's quotes, backslashes, and control
                        // characters are escaped correctly.
                        val settingsJson = JSONObject().apply {
                            put("fontSize", fontSize)
                            put("darkMode", darkMode)
                            put("language", language)
                            put("html", initialHtml)
                            put("zoom", editorZoom)
                            put("showLineNumbers", showLineNumbers)
                            put("showRuler", showRuler)
                            put("showStatusBar", showStatusBar)
                            put("wordWrap", wordWrap)
                            put("spellCheck", spellCheck)
                            put("autosaveEnabled", autosaveEnabled)
                            put("autosaveIntervalSec", autosaveIntervalSec)
                        }.toString()
                        // applySettings is defined synchronously in the HTML,
                        // so it is available by onPageFinished. The old code
                        // postDelayed(300) the call, capturing `view` — if
                        // the WebView was destroyed within 300ms (fast back
                        // press) the runnable still ran evaluateJavascript on
                        // a destroyed WebView → IllegalStateException.
                        view?.evaluateJavascript(
                            "window.applySettings($settingsJson)", null
                        )
                    }
                }

                // FOUNDATION FIX: the HTML relies on prompt() (emoji picker)
                // and confirm() ("clear document?"). Without a WebChromeClient
                // these return undefined/false and the features silently break.
                // Also route JS console.* to LogManager so editor bugs are
                // debuggable.
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        LogManager.d(
                            "TiptapJS",
                            "${cm.message()} (${cm.sourceId()}:${cm.lineNumber()})"
                        )
                        return true
                    }

                    override fun onJsPrompt(
                        view: WebView?, url: String?, message: String?,
                        defaultValue: String?, result: JsPromptResult?
                    ): Boolean {
                        // The HTML uses prompt('اختر إيموجي:…') purely as a
                        // cheap input dialog. We can't show a real Material
                        // dialog synchronously from here, so cancel the
                        // prompt — the emoji feature will be replaced by a
                        // proper Compose picker in a follow-up. Returning
                        // true means "we handled it" so the JS doesn't hang.
                        result?.cancel()
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView?, url: String?, message: String?,
                        result: JsResult?
                    ): Boolean {
                        // "Clear document?" confirm — default to false (don't
                        // clear) so we never destroy user content without an
                        // explicit Android-side dialog. Returning true
                        // prevents the default browser behaviour.
                        result?.cancel()
                        return true
                    }

                    override fun onJsAlert(
                        view: WebView?, url: String?, message: String?,
                        result: JsResult?
                    ): Boolean {
                        LogManager.i("TiptapJS", "alert: $message")
                        result?.confirm()
                        return true
                    }
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postMessage(message: String) {
                        try {
                            val json = JSONObject(message)
                            val type = json.optString("type")
                            val payload = json.optJSONObject("payload")

                            when (type) {
                                "contentChanged" -> {
                                    val html = payload?.optString("html") ?: ""
                                    onContentChanged(html)
                                }
                                "triggerAutoSave" -> {
                                    val html = payload?.optString("html") ?: ""
                                    if (html.isNotBlank()) {
                                        LogManager.d(
                                            "TiptapWebView",
                                            "Auto-save trigger (${html.length} chars)"
                                        )
                                        onAutoSave(html)
                                    }
                                }
                                "editorReady" -> onEditorReady()
                                "jsError" -> LogManager.e(
                                    "TiptapWebView",
                                    "JS Error: ${payload?.optString("message")}"
                                )
                                "saveFile", "saveHtmlAsWord", "saveHtmlAsPdf" -> {
                                    LogManager.d(
                                        "TiptapWebView",
                                        "$type ignored — save is handled by EditorScreen"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            LogManager.e("TiptapWebView", "Error parsing message", e)
                        }
                    }

                    @JavascriptInterface
                    fun pickImage() {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onImageRequested()
                        }
                    }
                }, "AndroidBridge")

                loadUrl("file:///android_asset/tiptap_editor.html")
                webViewRef(this)
            }
        },
        // FOUNDATION FIX: release the WebView when the composition leaves.
        // Without this the WebView and its JS bridge leak, holding the
        // Activity context. The bridge captures Compose state and the
        // EditorViewModel, so the leak is significant.
        onRelease = { wv ->
            try {
                wv.removeJavascriptInterface("AndroidBridge")
                wv.stopLoading()
                wv.webChromeClient = null
                wv.webViewClient = null
                wv.destroy()
            } catch (t: Throwable) {
                LogManager.w("TiptapWebView", "onRelease destroy failed: ${t.message}")
            }
        }
    )
}

fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: run {
            inputStream.close()
            return null
        }
        inputStream.close()

        val outputStream = ByteArrayOutputStream()
        val maxSize = 1200
        val scaledBitmap = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
            val ratio = minOf(
                maxSize.toFloat() / originalBitmap.width,
                maxSize.toFloat() / originalBitmap.height
            )
            Bitmap.createScaledBitmap(
                originalBitmap,
                (originalBitmap.width * ratio).toInt(),
                (originalBitmap.height * ratio).toInt(),
                true
            )
        } else originalBitmap

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        LogManager.e("uriToBase64", "Failed to convert image", e)
        null
    }
}