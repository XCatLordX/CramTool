package com.cramtool.app.data

import android.content.Context
import java.io.File
import java.util.UUID

/** 匯入文件的檔案管理：filesDir/docs/{uuid}.{ext}，圖片放在同名的 _media 資料夾 */
object DocumentFiles {
    fun root(context: Context): File = File(context.filesDir, "docs").apply { mkdirs() }
    fun newFile(context: Context, ext: String): File = File(root(context), "${UUID.randomUUID()}.$ext")
    fun mediaDir(path: String): File = File(path + "_media")

    fun delete(path: String) {
        File(path).delete()
        mediaDir(path).deleteRecursively()
    }

    /** 複製一份（含圖片），回傳新路徑；原檔不存在則回傳 null */
    fun duplicate(context: Context, path: String): String? {
        val src = File(path)
        if (!src.exists()) return null
        val dst = newFile(context, src.extension.ifBlank { "bin" })
        src.copyTo(dst)
        val media = mediaDir(path)
        if (media.exists()) media.copyRecursively(mediaDir(dst.path))
        return dst.path
    }
}
