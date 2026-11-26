package com.cramtool.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 備份檔格式（JSON）。欄位新增時給預設值即可相容舊備份。 */
@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val format: Int = FORMAT,
    val exportedAt: Long,
    val appVersion: String = "",
    val banks: List<BankDto> = emptyList(),
    val questions: List<QuestionDto> = emptyList(),
    val folders: List<FolderDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
    val answerLog: List<AnswerLogDto> = emptyList(),
    val exams: List<ExamDto> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
) {
    companion object {
        const val APP_ID = "CramTool"
        const val FORMAT = 1
    }
}

@Serializable
data class BankDto(val id: Long, val name: String, val description: String = "", val createdAt: Long = 0)

@Serializable
data class QuestionDto(
    val id: Long,
    val bankId: Long,
    val chapter: String,
    val type: String,
    val stem: String,
    val options: List<String> = emptyList(),
    val answers: List<String> = emptyList(),
    val explanation: String = "",
    val orderInBank: Int = 0,
    val attempts: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastAnsweredAt: Long? = null,
    val lastResultCorrect: Boolean? = null,
    val inWrongBook: Boolean = false,
    val reviewStreak: Int = 0,
    val starred: Boolean = false,
)

@Serializable
data class FolderDto(val id: Long, val parentId: Long? = null, val name: String, val createdAt: Long = 0)

@Serializable
data class NoteDto(
    val id: Long,
    val folderId: Long? = null,
    val title: String,
    val content: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastOpenedAt: Long? = null,
    val readPosition: Int = 0,
    val viewMode: Int = 0,
    val lessonPage: Int = 0,
    val completedPages: String = "",
    val pageCount: Int = 0,
    val sourceType: Int = 0,
    val sourcePath: String? = null,
)

@Serializable
data class AnswerLogDto(val questionId: Long, val bankId: Long, val chapter: String, val correct: Boolean, val answeredAt: Long, val mode: Int = 0)

@Serializable
data class ExamDto(
    val bankId: Long, val bankName: String, val startedAt: Long, val finishedAt: Long, val timeLimitSec: Int = 0,
    val durationSec: Int = 0, val total: Int, val correct: Int, val unanswered: Int = 0,
)

class BackupException(message: String) : Exception(message)

object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val f = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("這不是 CramTool 備份檔，或檔案已損壞")
        }
        if (f.app != BackupFile.APP_ID) throw BackupException("這不是 CramTool 備份檔")
        if (f.format > BackupFile.FORMAT) throw BackupException("備份檔來自較新版本的 App，請先更新 App")
        return f
    }

    // ---- entity <-> dto ----
    fun BankEntity.dto() = BankDto(id, name, description, createdAt)
    fun QuestionEntity.dto() = QuestionDto(
        id, bankId, chapter, type.name, stem, options, answers, explanation, orderInBank,
        attempts, correctCount, wrongCount, lastAnsweredAt, lastResultCorrect, inWrongBook, reviewStreak, starred,
    )
    fun FolderEntity.dto() = FolderDto(id, parentId, name, createdAt)
    fun NoteEntity.dto() = NoteDto(
        id, folderId, title, content, createdAt, updatedAt, lastOpenedAt, readPosition,
        viewMode, lessonPage, completedPages, pageCount,
    )

    fun AnswerLogEntity.dto() = AnswerLogDto(questionId, bankId, chapter, correct, answeredAt, mode)
    fun ExamResultEntity.dto() = ExamDto(bankId, bankName, startedAt, finishedAt, timeLimitSec, durationSec, total, correct, unanswered)

    fun QuestionDto.entity(newBankId: Long) = QuestionEntity(
        bankId = newBankId, chapter = chapter,
        type = QuestionType.entries.firstOrNull { it.name == type } ?: QuestionType.SINGLE,
        stem = stem, options = options, answers = answers, explanation = explanation, orderInBank = orderInBank,
        attempts = attempts, correctCount = correctCount, wrongCount = wrongCount, lastAnsweredAt = lastAnsweredAt,
        lastResultCorrect = lastResultCorrect, inWrongBook = inWrongBook, reviewStreak = reviewStreak, starred = starred,
    )
    fun NoteDto.entity(newFolderId: Long?, newSourcePath: String? = null, newContent: String = content) = NoteEntity(
        folderId = newFolderId, title = title, content = newContent, createdAt = createdAt, updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt, readPosition = readPosition, viewMode = viewMode, lessonPage = lessonPage,
        completedPages = completedPages, pageCount = pageCount, sourceType = sourceType, sourcePath = newSourcePath,
    )
}

data class BackupSummary(val banks: Int, val questions: Int, val folders: Int, val notes: Int, val exams: Int = 0) {
    override fun toString() = "$banks 個題庫（$questions 題）、$folders 個資料夾、$notes 篇筆記" + if (exams > 0) "、$exams 次模擬考" else ""
    companion object {
        fun of(f: BackupFile) = BackupSummary(f.banks.size, f.questions.size, f.folders.size, f.notes.size, f.exams.size)
    }
}
