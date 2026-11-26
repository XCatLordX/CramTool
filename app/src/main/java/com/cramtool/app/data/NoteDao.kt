package com.cramtool.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    // ---------- 資料夾 ----------
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAllFolders(): List<FolderEntity>

    @Query(
        """
        SELECT f.id, f.parentId, f.name,
          (SELECT COUNT(*) FROM notes n WHERE n.folderId = f.id) AS noteCount,
          (SELECT COUNT(*) FROM folders c WHERE c.parentId = f.id) AS folderCount
        FROM folders f
        WHERE (:parentId IS NULL AND f.parentId IS NULL) OR f.parentId = :parentId
        ORDER BY f.name COLLATE NOCASE
        """
    )
    fun observeSubfolders(parentId: Long?): Flow<List<FolderSummary>>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeFolder(id: Long): Flow<FolderEntity?>

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    suspend fun moveFolder(id: Long, parentId: Long?)

    @Query("UPDATE folders SET parentId = :newParent WHERE parentId = :oldParent")
    suspend fun reparentFolders(oldParent: Long, newParent: Long?)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    // ---------- 筆記 ----------
    @Query(
        """
        SELECT id, folderId, title, updatedAt, lastOpenedAt, LENGTH(content) AS length,
          SUBSTR(content, 1, 300) AS preview, pageCount, completedPages
        FROM notes
        WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId
        """
    )
    fun observeNotesIn(folderId: Long?): Flow<List<NoteSummary>>

    @Query(
        """
        SELECT id, folderId, title, updatedAt, lastOpenedAt, LENGTH(content) AS length,
          SUBSTR(content, 1, 300) AS preview, pageCount, completedPages
        FROM notes
        WHERE title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%'
        ORDER BY updatedAt DESC LIMIT 200
        """
    )
    fun searchNotes(q: String): Flow<List<NoteSummary>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): NoteEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun renameNote(id: Long, title: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :id")
    suspend fun moveNote(id: Long, folderId: Long?)

    @Query("UPDATE notes SET folderId = :newFolder WHERE folderId = :oldFolder")
    suspend fun moveNotesOutOf(oldFolder: Long, newFolder: Long?)

    @Query("UPDATE notes SET readPosition = :pos, lastOpenedAt = :now WHERE id = :id")
    suspend fun saveReadPosition(id: Long, pos: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET lessonPage = :page, completedPages = :completed, lastOpenedAt = :now WHERE id = :id")
    suspend fun saveLessonState(id: Long, page: Int, completed: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET pageCount = :count WHERE id = :id")
    suspend fun setPageCount(id: Long, count: Int)

    @Query("UPDATE notes SET viewMode = :mode WHERE id = :id")
    suspend fun setViewMode(id: Long, mode: Int)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()
}
