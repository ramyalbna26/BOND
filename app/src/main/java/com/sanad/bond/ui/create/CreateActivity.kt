package com.sanad.bond.ui.create

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sanad.bond.R
import com.sanad.bond.data.model.DocContent
import com.sanad.bond.data.model.SettingsKeys
import com.sanad.bond.data.repository.FilesRepository
import com.sanad.bond.data.repository.SettingsRepository
import com.sanad.bond.databinding.ActivityCreateBinding
import com.sanad.bond.core.logging.LogManager
import com.sanad.bond.services.document.DocxExporter
import com.sanad.bond.services.document.PdfExporter
import com.sanad.bond.ui.editor.EditorActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class CreateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateBinding

    private var pendingType: DocumentType? = null
    private var pendingName: String? = null

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            LogManager.i(TAG, "CreateDocument cancelled")
            pendingType = null
            pendingName = null
            return@registerForActivityResult
        }

        val uri = result.data?.data
        val type = pendingType
        val suggestedName = pendingName
        pendingType = null
        pendingName = null

        if (uri == null || type == null) {
            LogManager.w(TAG, "CreateDocument returned null uri or type")
            return@registerForActivityResult
        }

        LogManager.i(TAG, "CreateDocument returned: $uri (type=${type.id})")

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            LogManager.i(TAG, "Persistable URI permission taken successfully")
        } catch (e: SecurityException) {
            LogManager.w(
                TAG,
                "Provider doesn't support persistable permission — DocumentRepository will fallback if needed",
                e
            )
        }

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                writeEmptyDocument(uri, type)
            }
            if (!ok) {
                Toast.makeText(
                    this@CreateActivity,
                    R.string.create_failed,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val resolvedName = queryFileName(uri) ?: suggestedName ?: "Untitled"
            try {
                FilesRepository.recordRecent(
                    uri = uri.toString(),
                    displayName = resolvedName,
                    mimeType = type.mimeType,
                    sizeBytes = 0L
                )
            } catch (t: Throwable) {
                LogManager.e(TAG, "recordRecent failed", t)
            }

            Toast.makeText(
                this@CreateActivity,
                getString(R.string.create_success, resolvedName),
                Toast.LENGTH_SHORT
            ).show()

            startActivity(
                EditorActivity.intentFor(
                    context = this@CreateActivity,
                    uri = uri.toString(),
                    displayName = resolvedName,
                    mimeType = type.mimeType
                )
            )
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        LogManager.i(TAG, "CreateActivity opened")
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        val adapter = DocumentTypeAdapter { type -> promptForName(type) }
        binding.recyclerTypes.layoutManager = LinearLayoutManager(this)
        binding.recyclerTypes.adapter = adapter

        val wordEnabled = SettingsRepository.getBoolean(SettingsKeys.LIB_WORD_ENABLED, true)
        val pdfEnabled = SettingsRepository.getBoolean(SettingsKeys.LIB_PDF_ENABLED, true)
        // TEXT and MARKDOWN are gated by LIB_TEXT_ENABLED (markdown is a
        // text format). Previously these two types were declared in the
        // enum and handled in writeEmptyDocument but never shown in the
        // list, so users could not create .txt / .md files.
        val textEnabled = SettingsRepository.getBoolean(SettingsKeys.LIB_TEXT_ENABLED, true)

        val allowedTypes = buildList {
            if (wordEnabled) add(DocumentType.WORD)
            if (pdfEnabled) add(DocumentType.PDF)
            if (textEnabled) {
                add(DocumentType.TEXT)
                add(DocumentType.MARKDOWN)
            }
        }
        adapter.submitList(allowedTypes)

        val empty = allowedTypes.isEmpty()
        binding.recyclerTypes.visibility = if (empty) View.GONE else View.VISIBLE
        binding.textEmptyTypes.visibility = if (empty) View.VISIBLE else View.GONE

        LogManager.i(TAG, "Available types: ${allowedTypes.map { it.id }}")
    }

    private fun promptForName(type: DocumentType) {
        val input = EditText(this).apply {
            hint = getString(R.string.create_dialog_name_hint)
            setText(defaultName())
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_dialog_title)
            .setMessage(getString(type.subtitleRes))
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create_dialog_create, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = input.text?.isNotBlank() == true
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    positive.isEnabled = !s.isNullOrBlank()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            positive.setOnClickListener {
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setOnClickListener
                dialog.dismiss()

                pendingType = type
                pendingName = name
                launchCreateDocument("$name${type.extension}", type)
            }
        }
        dialog.show()
    }

    private fun launchCreateDocument(suggestedName: String, type: DocumentType) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type.mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedName)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        createDocumentLauncher.launch(intent)
    }

    private fun writeEmptyDocument(uri: Uri, type: DocumentType): Boolean {
        return try {
            val empty = DocContent(html = "<p></p>", plainText = "")
            when (type) {
                DocumentType.WORD -> DocxExporter.export(this, uri, empty)
                DocumentType.PDF -> PdfExporter.export(this, uri, empty)
                DocumentType.TEXT ->
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(ByteArray(0)) }
                        ?: return false
                DocumentType.MARKDOWN ->
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(ByteArray(0)) }
                        ?: return false
            }
            LogManager.i(TAG, "Wrote empty ${type.id} to $uri")
            true
        } catch (t: Throwable) {
            LogManager.e(TAG, "Failed to write empty document", t)
            false
        }
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (t: Throwable) {
            LogManager.w(TAG, "queryFileName failed", t)
            null
        }
    }

    private fun defaultName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "Document_$stamp"
    }

    companion object {
        private const val TAG = "CreateActivity"
    }
}