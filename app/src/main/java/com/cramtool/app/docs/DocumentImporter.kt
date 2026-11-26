package com.cramtool.app.docs

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.cramtool.app.data.DocumentFiles
import com.cramtool.app.data.NoteRepository
import com.cramtool.app.data.SOURCE_DOCX
import com.cramtool.app.data.SOURCE_PDF
import com.cramtool.app.data.SOURCE_PPTX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class ImportException(message: String) : Exception(message)

data class ImportedDoc(val noteId: Long, val title: String, val type: Int, val pages: Int)

/** 匯入 PDF／Word／PowerPoint，建立成一篇筆記 */
object DocumentImporter {
    const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    val PICKER_MIMES = arrayOf("application/pdf", MIME_DOCX, MIME_PPTX, "application/msword", "application/vnd.ms-powerpoint", "*/*")

    /** 這個 mime／檔名看起來是可匯入的文件嗎（用於分享、開啟方式的分流） */
    fun looksLikeDocument(mime: String?, name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        return mime == "application/pdf" || mime == MIME_DOCX || mime == MIME_PPTX ||
            ext in setOf("pdf", "docx", "pptx", "doc", "ppt")
    }

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    suspend fun import(context: Context, uri: Uri, folderId: Long?, repo: NoteRepository): ImportedDoc = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "文件"
        val mime = context.contentResolver.getType(uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        when {
            ext == "doc" || mime == "application/msword" ->
                throw ImportException("不支援舊版 Word（.doc）格式。請在 Word 中「另存新檔」為 .docx 後再匯入。")
            ext == "ppt" || mime == "application/vnd.ms-powerpoint" ->
                throw ImportException("不支援舊版 PowerPoint（.ppt）格式。請另存為 .pptx，或匯出成 PDF 後再匯入。")
        }

        // 先複製到暫存檔，再依內容判斷格式
        val tmp = File.createTempFile("import", ".bin", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw ImportException("無法讀取檔案")
            val type = detect(tmp) ?: throw ImportException("無法辨識的檔案格式。支援 PDF、Word（.docx）、PowerPoint（.pptx）。")
            val fileExt = when (type) { SOURCE_PDF -> "pdf"; SOURCE_DOCX -> "docx"; else -> "pptx" }
            val dest = DocumentFiles.newFile(context, fileExt)
            tmp.copyTo(dest)
            val fallbackTitle = name.substringBeforeLast('.').ifBlank { "文件" }
            try {
                when (type) {
                    SOURCE_PDF -> {
                        val pages = pdfPageCount(dest)
                        val id = repo.createDocumentNote(fallbackTitle, "", folderId, SOURCE_PDF, dest.path, pages)
                        ImportedDoc(id, fallbackTitle, type, pages)
                    }
                    else -> {
                        val r = if (type == SOURCE_DOCX) OfficeConverter.docx(dest, DocumentFiles.mediaDir(dest.path))
                        else OfficeConverter.pptx(dest, DocumentFiles.mediaDir(dest.path))
                        if (r.markdown.isBlank()) throw ImportException("檔案裡沒有可顯示的文字或圖片。")
                        // 標題用檔名（文件屬性裡的標題常是範本預設值）
                        val id = repo.createDocumentNote(fallbackTitle, r.markdown, folderId, type, dest.path, 0)
                        ImportedDoc(id, fallbackTitle, type, r.pages)
                    }
                }
            } catch (e: Exception) {
                DocumentFiles.delete(dest.path)
                throw if (e is ImportException) e else ImportException("檔案可能已損壞或格式不支援：${e.message ?: e.javaClass.simpleName}")
            }
        } finally {
            tmp.delete()
        }
    }

    /** 依檔案內容判斷：%PDF 開頭為 PDF；ZIP 內有 word/ 或 ppt/ 為 Office */
    fun detect(f: File): Int? {
        val head = f.inputStream().use { s -> ByteArray(5).also { s.read(it) } }
        if (String(head, Charsets.ISO_8859_1).startsWith("%PDF")) return SOURCE_PDF
        if (head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            return runCatching {
                ZipFile(f).use { z ->
                    when {
                        z.getEntry("word/document.xml") != null -> SOURCE_DOCX
                        z.getEntry("ppt/presentation.xml") != null -> SOURCE_PPTX
                        else -> null
                    }
                }
            }.getOrNull()
        }
        return null
    }

    private fun pdfPageCount(f: File): Int = try {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    } catch (e: SecurityException) {
        throw ImportException("這個 PDF 有密碼保護，無法開啟。")
    } catch (e: Exception) {
        throw ImportException("無法開啟這個 PDF：${e.message ?: "檔案可能已損壞"}")
    }
}
