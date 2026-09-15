package dev.clipkeyboard.data

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ClipboardWatcher {
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        cm.addPrimaryClipChangedListener {
            syncPrimaryClip(appContext)
        }
        registered = true
    }

    /**
     * OSクリップボードの最新データを安全に取得・保存する（同期用）
     */
    fun syncPrimaryClip(context: Context) {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return

            val description = clip.description
            val item = clip.getItemAt(0)

            // ① 画像の場合（image/*）
            if (description.hasMimeType("image/*") || isImageUri(appContext, item.uri)) {
                val uri = item.uri ?: return
                val mime = description.getMimeType(0) ?: appContext.contentResolver.getType(uri) ?: "image/png"
                saveFileClip(appContext, uri, mime)
                return
            }

            // ② テキストの場合
            if (description.hasMimeType("text/*") || description.hasMimeType("text/plain") || description.hasMimeType("text/html")) {
                val text = item.text?.toString() ?: item.coerceToText(appContext)?.toString()
                if (!text.isNullOrBlank()) {
                    ClipStore.addOrTouchText(appContext, text)
                }
                return
            }

            // ③ その他のファイル・独自形式
            if (item.uri != null) {
                val uri = item.uri!!
                val mime = description.getMimeType(0) ?: appContext.contentResolver.getType(uri) ?: "application/octet-stream"
                saveFileClip(appContext, uri, mime)
            }
        } catch (_: Exception) {
        }
    }

    private fun isImageUri(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        val type = context.contentResolver.getType(uri)
        return type?.startsWith("image/") == true
    }

    private fun saveFileClip(context: Context, uri: Uri, mimeType: String) {
        try {
            val clipsDir = File(context.filesDir, "clips").apply { if (!exists()) mkdirs() }
            val ext = mimeType.substringAfterLast("/", "bin")
            val fileName = queryFileName(context, uri) ?: "clip_${System.currentTimeMillis()}.$ext"
            val targetFile = File(clipsDir, "${UUID.randomUUID()}_$fileName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                ClipStore.addImageOrFile(
                    context = context,
                    mimeType = mimeType,
                    filePath = targetFile.absolutePath,
                    fileName = fileName,
                    fileSize = targetFile.length()
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
