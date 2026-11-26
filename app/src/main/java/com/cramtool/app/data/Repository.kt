package com.cramtool.app.data

import android.content.Context
import androidx.room.withTransaction
import com.cramtool.app.importer.ParsedQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Repository(context: Context, private val db: AppDatabase) {
    private val dao = db.dao()
    private val stats = db.statsDao()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- 設定 ----------
    private val _masteryThreshold = MutableStateFlow(prefs.getInt(KEY_MASTERY, 2))
    /** 錯題連續答對幾次後自動移出錯題本 */
    val masteryThreshold: StateFlow<Int> = _masteryThreshold

    fun setMasteryThreshold(value: Int) {
        prefs.edit().putInt(KEY_MASTERY, value).apply()
        _masteryThreshold.value = value
    }

    // ---------- 查詢 ----------
    fun observeBankSummaries(): Flow<List<BankSummary>> = dao.observeBankSummaries()
    fun observeBank(id: Long) = dao.observeBank(id)
    fun observeChapters(bankId: Long) = dao.observeChapters(bankId)
    fun observeQuestions(bankId: Long) = dao.observeQuestions(bankId)
    fun observeWrongItems() = dao.observeWrongItems()
    fun observeWrongCount() = dao.observeWrongCount()
    suspend fun getBanks() = dao.getBanks()
    suspend fun getQuestions(bankId: Long) = dao.getQuestions(bankId)

    /** 依傳入順序回傳題目 */
    suspend fun getQuestionsByIds(ids: List<Long>): List<QuestionEntity> {
        val map = ids.chunked(500).flatMap { dao.getQuestionsByIds(it) }.associateBy { it.id }
        return ids.mapNotNull { map[it] }
    }

    // ---------- 匯入 ----------
    /** @param targetBankId 為 null 時建立新題庫；否則附加到既有題庫 */
    suspend fun importQuestions(
        bankName: String,
        description: String,
        questions: List<ParsedQuestion>,
        targetBankId: Long?,
    ): Long = db.withTransaction {
        val bankId = targetBankId ?: dao.insertBank(BankEntity(name = bankName, description = description))
        val start = dao.maxOrder(bankId) + 1
        dao.insertQuestions(questions.mapIndexed { i, p ->
            QuestionEntity(
                bankId = bankId,
                chapter = p.chapter,
                type = p.type,
                stem = p.stem,
                options = p.options,
                answers = p.answers,
                explanation = p.explanation,
                orderInBank = start + i,
            )
        })
        bankId
    }

    // ---------- 作答 ----------
    suspend fun recordAnswer(questionId: Long, correct: Boolean, mode: Int = MODE_PRACTICE) {
        val q = dao.getQuestion(questionId) ?: return
        stats.insertLog(AnswerLogEntity(questionId = q.id, bankId = q.bankId, chapter = q.chapter, correct = correct, mode = mode))
        val threshold = masteryThreshold.value
        var inWrong = q.inWrongBook
        var streak = q.reviewStreak
        if (!correct) {
            inWrong = true
            streak = 0
        } else if (inWrong) {
            streak += 1
            if (streak >= threshold) {
                inWrong = false
                streak = 0
            }
        }
        dao.updateQuestion(
            q.copy(
                attempts = q.attempts + 1,
                correctCount = q.correctCount + if (correct) 1 else 0,
                wrongCount = q.wrongCount + if (correct) 0 else 1,
                lastAnsweredAt = System.currentTimeMillis(),
                lastResultCorrect = correct,
                inWrongBook = inWrong,
                reviewStreak = streak,
            )
        )
    }

    suspend fun setStarred(id: Long, starred: Boolean) = dao.setStarred(id, starred)
    suspend fun removeFromWrongBook(id: Long) = dao.removeFromWrongBook(id)
    suspend fun addToWrongBook(id: Long) = dao.addToWrongBook(id)
    suspend fun renameBank(id: Long, name: String) = dao.renameBank(id, name)
    suspend fun deleteBank(id: Long) = dao.deleteBank(id)
    suspend fun resetBankStats(id: Long) = dao.resetBankStats(id)
    suspend fun resetAllStats() = db.withTransaction {
        dao.resetAllStats(); stats.deleteAllLogs(); stats.deleteAllExams()
    }
    suspend fun deleteBanks(ids: Collection<Long>) = db.withTransaction { ids.forEach { dao.deleteBank(it) } }
    suspend fun resetBanksStats(ids: Collection<Long>) = db.withTransaction { ids.forEach { dao.resetBankStats(it) } }

    // ---------- 學習統計 ----------
    fun observeDaily(since: Long) = stats.observeDaily(since)
    fun observeActiveDays() = stats.observeActiveDays()
    fun observeChapterStats() = stats.observeChapterStats()
    fun observeBankStats() = stats.observeBankStats()
    fun observeExams() = stats.observeExams()
    fun observeLessonProgress() = stats.observeLessonProgress()
    suspend fun questionIdsIn(bankId: Long, chapter: String) = stats.questionIdsIn(bankId, chapter)
    suspend fun insertExam(r: ExamResultEntity) = stats.insertExam(r)
    suspend fun updateExam(r: ExamResultEntity) = stats.updateExam(r)

    companion object {
        private const val KEY_MASTERY = "mastery_threshold"
    }
}
