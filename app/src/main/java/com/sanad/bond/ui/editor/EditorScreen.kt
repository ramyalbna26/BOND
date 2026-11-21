package com.sanad.bond.ui.editor

import android.app.Activity
import android.content.Intent
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanad.bond.R
import com.sanad.bond.core.logging.LogManager
import org.json.JSONTokener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorState,
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onFileSaved: () -> Unit,
    webViewRef: (WebView?) -> Unit
) {
    val context = LocalContext.current
    var webViewInternal by remember { mutableStateOf<WebView?>(null) }
    var showSaveFailure by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val base64 = uriToBase64(context, uri)
                if (base64 != null) {
                    webViewInternal?.evaluateJavascript(
                        "window.insertImageFromBase64('${base64.replace("'", "\\'")}')",
                        null
                    )
                }
            }
        }
    }

    LaunchedEffect(webViewInternal) { webViewRef(webViewInternal) }

    // Track save success → notify the host activity via onFileSaved().
    var wasSaving by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (wasSaving && state is EditorState.Ready) {
            onFileSaved()
        }
        wasSaving = state is EditorState.Saving
    }

    // Migration notice
    val migration by viewModel.migrationNotice.collectAsState()
    LaunchedEffect(migration) {
        if (migration) {
            Toast.makeText(
                context,
                context.getString(R.string.editor_migration_notice),
                Toast.LENGTH_LONG
            ).show()
            viewModel.acknowledgeMigration()
        }
    }

    when (state) {
        is EditorState.Loading -> LoadingScreen()
        is EditorState.Error -> ErrorScreen(state.message)
        else -> {
            val doc = when (state) {
                is EditorState.Ready -> state.doc
                is EditorState.Saving -> state.doc
                is EditorState.SaveError -> state.doc
                else -> return
            }
            val isSaving = state is EditorState.Saving
            val errorMessage = (state as? EditorState.SaveError)?.message

            LaunchedEffect(errorMessage) {
                if (errorMessage != null) showSaveFailure = true
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                doc.meta.fileName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back)
                                )
                            }
                        },
                        actions = {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = {
                                    val wv = webViewInternal ?: return@IconButton
                                    LogManager.i("EditorScreen", "Save tapped — reading HTML")
                                    wv.evaluateJavascript("window.getHTML()") { raw ->
                                        val html = parseJsString(raw) ?: doc.content.html
                                        if (html.isBlank()) {
                                            LogManager.w("EditorScreen", "Empty HTML — abort save")
                                            return@evaluateJavascript
                                        }
                                        viewModel.saveWithHtml(html)
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = stringResource(R.string.cd_save)
                                    )
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    if (showSaveFailure && errorMessage != null) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(
                                        R.string.editor_save_error_prefix,
                                        errorMessage
                                    ),
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp
                                )
                                TextButton(onClick = { showSaveFailure = false }) {
                                    Text(stringResource(R.string.editor_hide))
                                }
                            }
                        }
                    }

                    TiptapWebView(
                        modifier = Modifier.fillMaxSize(),
                        initialHtml = doc.content.html,
                        fontSize = viewModel.defaultFontSize,
                        darkMode = viewModel.isDarkMode,
                        language = viewModel.language,
                        editorZoom = viewModel.editorZoom,
                        showLineNumbers = viewModel.showLineNumbers,
                        showRuler = viewModel.showRuler,
                        showStatusBar = viewModel.showStatusBar,
                        wordWrap = viewModel.wordWrap,
                        spellCheck = viewModel.spellCheck,
                        autosaveEnabled = viewModel.autosaveEnabled,
                        autosaveIntervalSec = viewModel.autosaveIntervalSec,
                        imagePickerLauncher = imagePicker,
                        onContentChanged = { html ->
                            val plain = html.replace(Regex("<[^>]*>"), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                            viewModel.onContentChanged(html, plain)
                        },
                        onAutoSave = { html ->
                            LogManager.d("EditorScreen", "Auto-save requested (${html.length} chars)")
                            viewModel.autoSaveWithHtml(html)
                        },
                        onEditorReady = { LogManager.i("EditorScreen", "Editor ready") },
                        onImageRequested = {
                            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                            imagePicker.launch(intent)
                        },
                        webViewRef = { wv -> webViewInternal = wv }
                    )
                }
            }
        }
    }
}

/**
 * evaluateJavascript returns a JSON-encoded result. window.getHTML() returns
 * a plain string, so the raw result is a JSON string literal like "\"<p>..\""
 * — we unwrap it here.
 */
private fun parseJsString(raw: String?): String? {
    if (raw == null || raw == "null" || raw.isEmpty()) return null
    return try {
        JSONTokener(raw).nextValue() as? String
    } catch (_: Throwable) {
        raw
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Box(
        Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
    }
}