package com.sanad.bond.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
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

                setOnTouchListener { v, _ ->
                    v.requestFocus(View.FOCUS_DOWN)
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
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
                        view?.postDelayed({
                            view.evaluateJavascript("window.applySettings($settingsJson)", null)
                        }, 300)
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