package com.cramtool.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val SOURCE_MARKDOWN = 0
const val SOURCE_PDF = 1
const val SOURCE_DOCX = 2
const val SOURCE_PPTX = 3

/** 筆記資料夾，可巢狀（parentId = null 表示最上層） */
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("parentId")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 一篇筆記（Markdown 原文） */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("folderId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    /** 上次閱讀到的區塊索引（捲動模式） */
    val readPosition: Int = 0,
    /** 閱讀模式：0 = 自動（有多個小節就分頁）、1 = 捲動、2 = 分頁 */
    @ColumnInfo(defaultValue = "0") val viewMode: Int = 0,
    /** 分頁模式目前所在頁 */
    @ColumnInfo(defaultValue = "0") val lessonPage: Int = 0,
    /** 已完成的小節標題，以換行分隔（用標題而非頁碼，編輯筆記後仍對得上） */
    @ColumnInfo(defaultValue = "") val completedPages: String = "",
    /** 分頁後的總頁數（開啟時更新，用於列表顯示學習進度） */
    @ColumnInfo(defaultValue = "0") val pageCount: Int = 0,
    /** 來源：[SOURCE_MARKDOWN] 一般筆記，或匯入的 PDF／Word／PowerPoint */
    @ColumnInfo(defaultValue = "0") val sourceType: Int = SOURCE_MARKDOWN,
    /** 匯入檔案在 App 內部的路徑（PDF 閱讀、用其他 App 開啟原始檔） */
    val sourcePath: String? = null,
) {
    val completedSet: Set<String> get() = completedPages.split('\n').filter { it.isNotBlank() }.toSet()
}

/** 列表用：不載入整篇內容 */
data class NoteSummary(
    val id: Long,
    val folderId: Long?,
    val title: String,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val length: Int,
    val preview: String,
    val pageCount: Int = 0,
    val completedPages: String = "",
    val sourceType: Int = SOURCE_MARKDOWN,
) {
    val completedCount: Int get() = completedPages.split('\n').count { it.isNotBlank() }.coerceAtMost(pageCount)
}

data class FolderSummary(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val noteCount: Int,
    val folderCount: Int,
)
