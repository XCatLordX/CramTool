package com.cramtool.app.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NoteRepository(context: Context, private val db: AppDatabase) {
    private val dao = db.noteDao()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- 閱讀設定 ----------
    private val _fontScale = MutableStateFlow(prefs.getFloat(KEY_FONT, 1f))
    val fontScale: StateFlow<Float> = _fontScale
    fun setFontScale(v: Float) {
        val c = v.coerceIn(0.8f, 1.6f)
        prefs.edit().putFloat(KEY_FONT, c).apply()
        _fontScale.value = c
    }

    // ---------- 查詢 ----------
    fun observeAllFolders() = dao.observeAllFolders()
    fun observeSubfolders(parentId: Long?) = dao.observeSubfolders(parentId)
    fun observeFolder(id: Long) = dao.observeFolder(id)
    fun observeNotesIn(folderId: Long?) = dao.observeNotesIn(folderId)
    fun searchNotes(q: String) = dao.searchNotes(q)
    fun observeNote(id: Long) = dao.observeNote(id)
    suspend fun getNote(id: Long) = dao.getNote(id)

    // ---------- 資料夾 ----------
    suspend fun createFolder(name: String, parentId: Long?) = dao.insertFolder(FolderEntity(name = name, parentId = parentId))
    suspend fun renameFolder(id: Long, name: String) = dao.renameFolder(id, name)

    /** 刪除資料夾：裡面的筆記與子資料夾移到上一層，不會被刪掉 */
    suspend fun deleteFolder(id: Long) = db.withTransaction {
        val parent = dao.getAllFolders().firstOrNull { it.id == id }?.parentId
        dao.moveNotesOutOf(id, parent)
        dao.reparentFolders(id, parent)
        dao.deleteFolder(id)
    }

    /** @return false 表示會形成循環（移到自己或自己的子資料夾內），未執行 */
    suspend fun moveFolder(id: Long, newParent: Long?): Boolean {
        if (newParent != null && newParent in descendantsOf(id, dao.getAllFolders()) + id) return false
        dao.moveFolder(id, newParent)
        return true
    }

    // ---------- 筆記 ----------
    suspend fun createNote(title: String, content: String, folderId: Long?): Long =
        dao.insertNote(NoteEntity(title = title, content = content, folderId = folderId))

    suspend fun updateNoteContent(id: Long, title: String, content: String) {
        val n = dao.getNote(id) ?: return
        dao.updateNote(n.copy(title = title, content = content, updatedAt = System.currentTimeMillis()))
    }

    suspend fun renameNote(id: Long, title: String) = dao.renameNote(id, title)
    suspend fun moveNote(id: Long, folderId: Long?) = dao.moveNote(id, folderId)
    suspend fun deleteNote(id: Long) = dao.deleteNote(id)
    suspend fun saveReadPosition(id: Long, pos: Int) = dao.saveReadPosition(id, pos)
    suspend fun saveLessonState(id: Long, page: Int, completed: Set<String>) =
        dao.saveLessonState(id, page, completed.joinToString("\n"))
    suspend fun setPageCount(id: Long, count: Int) = dao.setPageCount(id, count)
    suspend fun setViewMode(id: Long, mode: Int) = dao.setViewMode(id, mode)

    companion object {
        private const val KEY_FONT = "reader_font_scale"

        fun descendantsOf(id: Long, all: List<FolderEntity>): Set<Long> {
            val children = all.groupBy { it.parentId }
            val out = mutableSetOf<Long>()
            val stack = ArrayDeque(listOf(id))
            while (stack.isNotEmpty()) {
                children[stack.removeLast()].orEmpty().forEach { if (out.add(it.id)) stack.add(it.id) }
            }
            return out
        }
    }
}
