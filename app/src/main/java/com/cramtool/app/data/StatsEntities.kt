package com.cramtool.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val MODE_PRACTICE = 0
const val MODE_EXAM = 1

/** 每一次作答的紀錄（學習統計用：每日作答量、連續學習天數） */
@Entity(tableName = "answer_log", indices = [Index("answeredAt"), Index("bankId")])
data class AnswerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val bankId: Long,
    val chapter: String,
    val correct: Boolean,
    val answeredAt: Long = System.currentTimeMillis(),
    /** [MODE_PRACTICE] 或 [MODE_EXAM] */
    val mode: Int = MODE_PRACTICE,
)

/** 一次模擬考的成績 */
@Entity(tableName = "exam_results", indices = [Index("finishedAt")])
data class ExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankId: Long,
    val bankName: String,
    val startedAt: Long,
    val finishedAt: Long,
    /** 0 = 不限時 */
    val timeLimitSec: Int,
    val durationSec: Int,
    val total: Int,
    val correct: Int,
    val unanswered: Int,
) {
    val score: Int get() = if (total == 0) 0 else Math.round(correct * 100f / total)
}

data class DailyCount(val day: String, val total: Int, val correct: Int)

data class ChapterStat(val bankId: Long, val bankName: String, val chapter: String, val attempts: Int, val correct: Int, val questions: Int) {
    val accuracy: Float get() = if (attempts == 0) 0f else correct.toFloat() / attempts
}

data class BankStat(
    val bankId: Long, val name: String, val total: Int, val answered: Int, val wrong: Int, val attempts: Int, val correct: Int,
) {
    val accuracy: Float get() = if (attempts == 0) 0f else correct.toFloat() / attempts
}

data class LessonProgressRow(val pageCount: Int, val completedPages: String)
