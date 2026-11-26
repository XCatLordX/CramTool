package com.cramtool.app.data

import androidx.room.withTransaction
import com.cramtool.app.data.BackupCodec.dto
import com.cramtool.app.data.BackupCodec.entity

/** 匯出／還原所有資料（題庫、題目、作答紀錄、資料夾、筆記、設定） */
class BackupManager(
    private val context: android.content.Context,
    private val db: AppDatabase,
    private val repo: Repository,
    private val notes: NoteRepository,
    private val settings: AppSettings,
) {
    private val cram = db.dao()
    private val noteDao = db.noteDao()
    private val statsDao = db.statsDao()

    suspend fun export(appVersion: String): BackupFile = BackupFile(
        exportedAt = System.currentTimeMillis(),
        appVersion = appVersion,
        banks = cram.getBanks().map { it.dto() },
        questions = cram.getAllQuestions().map { it.dto() },
        folders = noteDao.getAllFolders().map { it.dto() },
        notes = noteDao.getAllNotes().map { it.dto() },
        answerLog = statsDao.getAllLogs().map { it.dto() },
        exams = statsDao.getAllExams().map { it.dto() },
        settings = exportSettings(),
    )

    /**
     * @param replace true = 先清空現有資料再還原（並套用備份中的設定）；false = 合併（加入現有資料）
     */
    suspend fun restore(file: BackupFile, replace: Boolean): BackupSummary {
        db.withTransaction {
            if (replace) {
                cram.deleteAllBanks()      // 題目隨外鍵一起刪除
                noteDao.deleteAllNotes()
                noteDao.deleteAllFolders()
            }
            // 題庫：舊 id → 新 id
            if (replace) { statsDao.deleteAllLogs(); statsDao.deleteAllExams() }
            val bankMap = file.banks.associate { it.id to cram.insertBank(BankEntity(name = it.name, description = it.description, createdAt = it.createdAt)) }
            // 題目逐題插入以取得新 id（作答紀錄要對應到新題目）
            val questionMap = HashMap<Long, Long>()
            file.questions.forEach { q -> bankMap[q.bankId]?.let { b -> questionMap[q.id] = cram.insertQuestion(q.entity(b)) } }
            statsDao.insertLogs(file.answerLog.map { l ->
                AnswerLogEntity(
                    questionId = questionMap[l.questionId] ?: -1, bankId = bankMap[l.bankId] ?: -1,
                    chapter = l.chapter, correct = l.correct, answeredAt = l.answeredAt, mode = l.mode,
                )
            })
            file.exams.forEach { e ->
                statsDao.insertExam(ExamResultEntity(
                    bankId = bankMap[e.bankId] ?: -1, bankName = e.bankName, startedAt = e.startedAt, finishedAt = e.finishedAt,
                    timeLimitSec = e.timeLimitSec, durationSec = e.durationSec, total = e.total, correct = e.correct, unanswered = e.unanswered,
                ))
            }

            // 資料夾：先全部建在最上層，再依對照表接回父資料夾
            val folderMap = file.folders.associate { it.id to noteDao.insertFolder(FolderEntity(name = it.name, createdAt = it.createdAt)) }
            file.folders.forEach { f ->
                val parent = f.parentId?.let { folderMap[it] }
                if (parent != null) noteDao.moveFolder(folderMap.getValue(f.id), parent)
            }
            file.notes.forEach { n ->
                // 匯入的文件：若原始檔還在這支手機上，複製一份給新筆記（各自擁有檔案，刪除時互不影響）
                val newPath = n.sourcePath?.let { DocumentFiles.duplicate(context, it) }
                val content = if (n.sourcePath != null && newPath != null)
                    n.content.replace(DocumentFiles.mediaDir(n.sourcePath).path, DocumentFiles.mediaDir(newPath).path) else n.content
                noteDao.insertNote(n.entity(n.folderId?.let { folderMap[it] }, newPath, content))
            }
        }
        if (replace) importSettings(file.settings)
        return BackupSummary.of(file)
    }

    private fun exportSettings(): Map<String, String> = mapOf(
        "themeMode" to settings.themeMode.value.name,
        "dynamicColor" to settings.dynamicColor.value.toString(),
        "soundEnabled" to settings.soundEnabled.value.toString(),
        "soundStyle" to settings.soundStyle.value.name,
        "soundVolume" to settings.soundVolume.value.toString(),
        "hapticsEnabled" to settings.hapticsEnabled.value.toString(),
        "masteryThreshold" to repo.masteryThreshold.value.toString(),
        "fontScale" to notes.fontScale.value.toString(),
    )

    private fun importSettings(m: Map<String, String>) {
        m["themeMode"]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v }?.let(settings::setThemeMode) }
        m["dynamicColor"]?.toBooleanStrictOrNull()?.let(settings::setDynamicColor)
        m["soundEnabled"]?.toBooleanStrictOrNull()?.let(settings::setSoundEnabled)
        m["soundStyle"]?.let { v -> SoundStyle.entries.firstOrNull { it.name == v }?.let(settings::setSoundStyle) }
        m["soundVolume"]?.toFloatOrNull()?.let(settings::setSoundVolume)
        m["hapticsEnabled"]?.toBooleanStrictOrNull()?.let(settings::setHapticsEnabled)
        m["masteryThreshold"]?.toIntOrNull()?.takeIf { it in 1..10 }?.let(repo::setMasteryThreshold)
        m["fontScale"]?.toFloatOrNull()?.let(notes::setFontScale)
    }
}
