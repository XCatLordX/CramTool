package com.cramtool.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {
    @Insert
    suspend fun insertLog(log: AnswerLogEntity)

    @Insert
    suspend fun insertLogs(logs: List<AnswerLogEntity>)

    @Query("SELECT * FROM answer_log ORDER BY answeredAt")
    suspend fun getAllLogs(): List<AnswerLogEntity>

    /** 依本地日期彙總（day = yyyy-MM-dd） */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', answeredAt / 1000, 'unixepoch', 'localtime') AS day,
          COUNT(*) AS total, SUM(CASE WHEN correct THEN 1 ELSE 0 END) AS correct
        FROM answer_log WHERE answeredAt >= :since
        GROUP BY day ORDER BY day
        """
    )
    fun observeDaily(since: Long): Flow<List<DailyCount>>

    @Query(
        """
        SELECT DISTINCT strftime('%Y-%m-%d', answeredAt / 1000, 'unixepoch', 'localtime') AS day
        FROM answer_log ORDER BY day DESC LIMIT 400
        """
    )
    fun observeActiveDays(): Flow<List<String>>

    @Query(
        """
        SELECT q.bankId AS bankId, b.name AS bankName, q.chapter AS chapter,
          SUM(q.attempts) AS attempts, SUM(q.correctCount) AS correct, COUNT(*) AS questions
        FROM questions q JOIN banks b ON q.bankId = b.id
        GROUP BY q.bankId, q.chapter
        """
    )
    fun observeChapterStats(): Flow<List<ChapterStat>>

    @Query(
        """
        SELECT b.id AS bankId, b.name AS name,
          COUNT(q.id) AS total,
          SUM(CASE WHEN q.attempts > 0 THEN 1 ELSE 0 END) AS answered,
          SUM(CASE WHEN q.inWrongBook = 1 THEN 1 ELSE 0 END) AS wrong,
          COALESCE(SUM(q.attempts), 0) AS attempts, COALESCE(SUM(q.correctCount), 0) AS correct
        FROM banks b LEFT JOIN questions q ON q.bankId = b.id
        GROUP BY b.id ORDER BY b.createdAt DESC
        """
    )
    fun observeBankStats(): Flow<List<BankStat>>

    @Query("SELECT id FROM questions WHERE bankId = :bankId AND chapter = :chapter")
    suspend fun questionIdsIn(bankId: Long, chapter: String): List<Long>

    // ---------- 模擬考 ----------
    @Insert
    suspend fun insertExam(r: ExamResultEntity): Long

    @Update
    suspend fun updateExam(r: ExamResultEntity)

    @Query("SELECT * FROM exam_results ORDER BY finishedAt DESC")
    fun observeExams(): Flow<List<ExamResultEntity>>

    @Query("SELECT * FROM exam_results ORDER BY finishedAt")
    suspend fun getAllExams(): List<ExamResultEntity>

    @Query("DELETE FROM exam_results")
    suspend fun deleteAllExams()

    @Query("DELETE FROM answer_log")
    suspend fun deleteAllLogs()

    // ---------- 筆記 ----------
    @Query("SELECT pageCount, completedPages FROM notes WHERE pageCount >= 2")
    fun observeLessonProgress(): Flow<List<LessonProgressRow>>
}
