package com.cramtool.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    /** 上次閱讀到的區塊索引 */
    val readPosition: Int = 0,
)

/** 列表用：不載入整篇內容 */
data class NoteSummary(
    val id: Long,
    val folderId: Long?,
    val title: String,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val length: Int,
    val preview: String,
)

data class FolderSummary(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val noteCount: Int,
    val folderCount: Int,
)
