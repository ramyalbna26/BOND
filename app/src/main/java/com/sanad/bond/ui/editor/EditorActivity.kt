package com.sanad.bond.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sanad.bond.R
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.core.logging.LogManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditorActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra(EXTRA_URI)
        val name = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        val mime = intent.getStringExtra(EXTRA_MIME_TYPE)

        if (uriString == null || name == null || mime == null) {
            LogManager.e(TAG, "Missing intent extras")
            finish(); return
        }

        if (!SettingsRepository.isFeatureEnabledFor(mime)) {
            LogManager.w(TAG, "Feature disabled for mime=$mime")
            Toast.makeText(this, R.string.feature_disabled_for_type, Toast.LENGTH_LONG).show()
            finish(); return
        }

        viewModel.load(Uri.parse(uriString), name, mime)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val state by viewModel.state.collectAsState()

                EditorScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = { finish() },
                    onFileSaved = {
                        runOnUiThread {
                            Toast.makeText(
                                this@EditorActivity,
                                getString(R.string.editor_saved_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    // The WebView reference is no longer needed here:
                    // TiptapWebView now owns its lifecycle via onRelease
                    // (destroy + removeJavascriptInterface) when the
                    // composition leaves. Previously this member was
                    // assigned but never read and never destroyed → leak.
                    webViewRef = { }
                )
            }
        }
    }

    companion object {
        private const val TAG = "EditorActivity"
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_DISPLAY_NAME = "extra_display_name"
        private const val EXTRA_MIME_TYPE = "extra_mime_type"

        fun intentFor(
            context: Context,
            uri: String,
            displayName: String,
            mimeType: String
        ): Intent = Intent(context, EditorActivity::class.java).apply {
            putExtra(EXTRA_URI, uri)
            putExtra(EXTRA_DISPLAY_NAME, displayName)
            putExtra(EXTRA_MIME_TYPE, mimeType)
        }
    }
}