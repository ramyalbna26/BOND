package com.sanad.bond.core.logging

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

internal class LogFileWriter(private val context: Context) {

    companion object {
        private const val TAG = "LogFileWriter"
        private const val LOG_FOLDER = "log"
        private const val LOG_FILE_NAME = "log.txt"
        private const val RELATIVE_PATH = "Download/$LOG_FOLDER/"
        private const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024
        private const val SIZE_CHECK_EVERY_BYTES = 64L * 1024
    }

    private var mediaStoreUri: Uri? = null
    private var directFile: File? = null
    private var bytesSinceSizeCheck: Long = 0L
    @Volatile private var lastError: String? = null

    init {
        retryInit()
    }

    fun retryInit() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaStoreUri = findOrCreateMediaStoreUri()
                if (mediaStoreUri == null) lastError = "MediaStore URI null"
            } else {
                directFile = openOrCreateDirectFile()
                if (directFile == null) lastError = "Direct file null"
            }
        } catch (e: Exception) {
            lastError = "Init failed: ${e.message}"
            Log.e(TAG, "Storage init failed", e)
        }
    }

    fun getLastError(): String? = lastError

    fun isAvailable(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        mediaStoreUri != null
    } else {
        directFile?.exists() == true
    }

    fun displayPath(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${RELATIVE_PATH}${LOG_FILE_NAME}"
        } else {
            directFile?.absolutePath ?: "unavailable"
        }

    fun getShareableUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreUri
        } else {
            directFile?.takeIf { it.exists() }?.let { file ->
                try {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } catch (e: Exception) {
                    Log.w(TAG, "FileProvider failed", e)
                    null
                }
            }
        }
    }

    fun sizeBytes(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = mediaStoreUri ?: return 0L
            val projection = arrayOf(MediaStore.Downloads.SIZE)
            var size = 0L
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) size = c.getLong(0)
            }
            size
        } else {
            directFile?.length() ?: 0L
        }
    }

    fun append(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        val text = buildString {
            for (e in entries) {
                append(e.format())
                append('\n')
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appendViaMediaStore(text)
            } else {
                appendDirect(text)
            }
            lastError = null
        } catch (e: Exception) {
            lastError = "append failed: ${e.message}"
            Log.e(TAG, "append failed", e)
            retryInit()
        }
    }

    fun clear() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaStoreUri?.let { uri ->
                    context.contentResolver.openOutputStream(uri, "wt")?.use { }
                }
            } else {
                directFile?.writeText("")
            }
            bytesSinceSizeCheck = 0L
        } catch (e: Exception) {
            Log.e(TAG, "clear failed", e)
        }
    }

    private fun findOrCreateMediaStoreUri(): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?"
        val args = arrayOf(RELATIVE_PATH, LOG_FILE_NAME)
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, LOG_FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        return context.contentResolver.insert(collection, values)
    }

    private fun appendViaMediaStore(text: String) {
        val uri = mediaStoreUri ?: findOrCreateMediaStoreUri()?.also { mediaStoreUri = it } ?: return
        val newBytes = text.toByteArray(Charsets.UTF_8)
        try {
            context.contentResolver.openOutputStream(uri, "wa")?.use { os ->
                os.write(newBytes)
                os.flush()
                bytesSinceSizeCheck += newBytes.size
                maybeRotate(uri)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "append 'wa' failed", e)
        }
        val existing = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        } catch (e: Exception) { ByteArray(0) }
        
        val combined = if (existing.size + newBytes.size > MAX_FILE_SIZE_BYTES) newBytes else existing + newBytes
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            os.write(combined)
            os.flush()
        }
        bytesSinceSizeCheck = 0L
    }

    private fun maybeRotate(uri: Uri) {
        if (bytesSinceSizeCheck < SIZE_CHECK_EVERY_BYTES) return
        bytesSinceSizeCheck = 0L
        val projection = arrayOf(MediaStore.Downloads.SIZE)
        var size = 0L
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) size = c.getLong(0)
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { }
        }
    }

    @Suppress("DEPRECATION")
    private fun openOrCreateDirectFile(): File? {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return null
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LOG_FOLDER)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, LOG_FILE_NAME)
        if (!file.exists()) file.createNewFile()
        return file
    }

    private fun appendDirect(text: String) {
        val f = directFile ?: openOrCreateDirectFile()?.also { directFile = it } ?: return
        val newBytes = text.toByteArray(Charsets.UTF_8)
        if (f.length() + newBytes.size > MAX_FILE_SIZE_BYTES) f.writeText("")
        f.appendText(text)
    }
}