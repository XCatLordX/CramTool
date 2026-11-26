package com.cramtool.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CramDao {
    // ---------- 題庫 ----------
    @Query(
        """
        SELECT b.*,
          (SELECT COUNT(*) FROM questions q WHERE q.bankId = b.id) AS total,
          (SELECT COUNT(*) FROM questions q WHERE q.bankId = b.id AND q.attempts > 0) AS answered,
          (SELECT COUNT(*) FROM questions q WHERE q.bankId = b.id AND q.inWrongBook = 1) AS wrong
        FROM banks b ORDER BY b.createdAt DESC
        """
    )
    fun observeBankSummaries(): Flow<List<BankSummary>>

    @Query("SELECT * FROM banks ORDER BY createdAt DESC")
    suspend fun getBanks(): List<BankEntity>

    @Query("SELECT * FROM banks WHERE id = :id")
    fun observeBank(id: Long): Flow<BankEntity?>

    @Insert
    suspend fun insertBank(bank: BankEntity): Long

    @Query("UPDATE banks SET name = :name WHERE id = :id")
    suspend fun renameBank(id: Long, name: String)

    @Query("DELETE FROM banks WHERE id = :id")
    suspend fun deleteBank(id: Long)

    @Query("DELETE FROM banks")
    suspend fun deleteAllBanks()

    @Query("SELECT * FROM questions ORDER BY bankId, orderInBank")
    suspend fun getAllQuestions(): List<QuestionEntity>

    // ---------- 題目 ----------
    @Insert
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Query("SELECT COALESCE(MAX(orderInBank), -1) FROM questions WHERE bankId = :bankId")
    suspend fun maxOrder(bankId: Long): Int

    @Query(
        """
        SELECT chapter,
          COUNT(*) AS total,
          SUM(CASE WHEN attempts > 0 THEN 1 ELSE 0 END) AS answered,
          SUM(CASE WHEN inWrongBook = 1 THEN 1 ELSE 0 END) AS wrong,
          MIN(orderInBank) AS firstOrder
        FROM questions WHERE bankId = :bankId
        GROUP BY chapter ORDER BY firstOrder
        """
    )
    fun observeChapters(bankId: Long): Flow<List<ChapterSummary>>

    @Query("SELECT * FROM questions WHERE bankId = :bankId ORDER BY orderInBank")
    fun observeQuestions(bankId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE bankId = :bankId ORDER BY orderInBank")
    suspend fun getQuestions(bankId: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getQuestionsByIds(ids: List<Long>): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestion(id: Long): QuestionEntity?

    @Query(
        """
        UPDATE questions SET attempts = 0, correctCount = 0, wrongCount = 0,
          lastAnsweredAt = NULL, lastResultCorrect = NULL, inWrongBook = 0, reviewStreak = 0
        WHERE bankId = :bankId
        """
    )
    suspend fun resetBankStats(bankId: Long)

    @Query(
        """
        UPDATE questions SET attempts = 0, correctCount = 0, wrongCount = 0,
          lastAnsweredAt = NULL, lastResultCorrect = NULL, inWrongBook = 0, reviewStreak = 0
        """
    )
    suspend fun resetAllStats()

    // ---------- 錯題本 ----------
    @Query(
        """
        SELECT q.*, b.name AS bankName FROM questions q
        JOIN banks b ON q.bankId = b.id
        WHERE q.inWrongBook = 1
        ORDER BY q.lastAnsweredAt DESC
        """
    )
    fun observeWrongItems(): Flow<List<WrongItem>>

    @Query("SELECT COUNT(*) FROM questions WHERE inWrongBook = 1")
    fun observeWrongCount(): Flow<Int>

    @Query("UPDATE questions SET inWrongBook = 0, reviewStreak = 0 WHERE id = :id")
    suspend fun removeFromWrongBook(id: Long)

    @Query("UPDATE questions SET inWrongBook = 1, reviewStreak = 0 WHERE id = :id")
    suspend fun addToWrongBook(id: Long)

    @Query("UPDATE questions SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)
}
