package com.cramtool.app.ui.exam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramtool.app.CramApp
import com.cramtool.app.ExamRequest
import com.cramtool.app.SessionStore
import com.cramtool.app.audio.Cue
import com.cramtool.app.data.ExamResultEntity
import com.cramtool.app.data.MODE_EXAM
import com.cramtool.app.data.QuestionEntity
import com.cramtool.app.data.QuestionType
import com.cramtool.app.importer.Grader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 交卷後每一題的結果；correct = null 表示簡答題待自評 */
data class ExamItem(val q: QuestionEntity, val answered: Boolean, val correct: Boolean?, val flagged: Boolean)

class ExamViewModel(private val app: CramApp) : ViewModel() {
    private val repo = app.repository
    val request: ExamRequest? = SessionStore.exam

    var loading by mutableStateOf(true); private set
    var questions by mutableStateOf<List<QuestionEntity>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set

    /** 選項顯示順序（打亂選項時使用）：displayIndex -> 原始索引 */
    val optionOrder = mutableStateMapOf<Long, List<Int>>()
    /** 已選的「原始」選項索引 */
    val selected = mutableStateMapOf<Long, Set<Int>>()
    val texts = mutableStateMapOf<Long, String>()
    val flagged = mutableStateMapOf<Long, Boolean>()

    private val startedAt = System.currentTimeMillis()
    var remainingSec by mutableIntStateOf(request?.timeLimitSec ?: 0); private set
    var elapsedSec by mutableIntStateOf(0); private set

    var finished by mutableStateOf(false); private set
    var autoSubmitted by mutableStateOf(false); private set
    var items by mutableStateOf<List<ExamItem>>(emptyList()); private set
    /** 簡答題自評結果 */
    val selfGrades = mutableStateMapOf<Long, Boolean>()
    private var resultId: Long? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val req = request
            questions = if (req == null) emptyList() else repo.getQuestionsByIds(req.questionIds)
            questions.forEach { q ->
                val n = q.options.size
                optionOrder[q.id] = if (req?.shuffleOptions == true && (q.type == QuestionType.SINGLE || q.type == QuestionType.MULTIPLE))
                    (0 until n).shuffled() else (0 until n).toList()
            }
            loading = false
            // 計時器（用實際時間計算，切到背景再回來也準確）
            while (!finished) {
                elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                val limit = req?.timeLimitSec ?: 0
                if (limit > 0) {
                    remainingSec = (limit - elapsedSec).coerceAtLeast(0)
                    if (remainingSec == 0) { submit(auto = true); break }
                }
                delay(1000)
            }
        }
    }

    val current: QuestionEntity? get() = questions.getOrNull(index)

    fun isAnswered(q: QuestionEntity): Boolean = when (q.type) {
        QuestionType.FILL, QuestionType.SHORT -> !texts[q.id].isNullOrBlank()
        else -> !selected[q.id].isNullOrEmpty()
    }
    val answeredCount: Int get() = questions.count { isAnswered(it) }
    val flaggedCount: Int get() = flagged.count { it.value }

    fun toggleOption(q: QuestionEntity, original: Int) {
        if (finished) return
        val cur = selected[q.id].orEmpty()
        selected[q.id] = when (q.type) {
            QuestionType.MULTIPLE -> if (original in cur) cur - original else cur + original
            else -> if (original in cur) emptySet() else setOf(original)
        }
    }

    fun setText(q: QuestionEntity, s: String) { if (!finished) texts[q.id] = s }
    fun toggleFlag(q: QuestionEntity) { flagged[q.id] = !(flagged[q.id] ?: false) }
    fun goTo(i: Int) { index = i.coerceIn(0, maxOf(0, questions.lastIndex)) }

    fun submit(auto: Boolean = false) {
        if (finished) return
        finished = true
        autoSubmitted = auto
        elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        items = questions.map { q ->
            val answered = isAnswered(q)
            val correct: Boolean? = when {
                !answered -> false
                q.type == QuestionType.SHORT -> null // 交給使用者自評
                q.type == QuestionType.FILL -> Grader.gradeFill(q, texts[q.id].orEmpty())
                else -> Grader.gradeChoice(q, selected[q.id].orEmpty())
            }
            ExamItem(q, answered, correct, flagged[q.id] == true)
        }
        val snapshot = items
        app.sounds.play(Cue.FINISH)
        saveJob = app.appScope.launch {
            // 未作答也算答錯，收進錯題本
            snapshot.filter { it.correct != null }.forEach { repo.recordAnswer(it.q.id, it.correct!!, MODE_EXAM) }
            resultId = repo.insertExam(resultEntity())
        }
    }

    /** 簡答題自評（每題可改判，作答紀錄只記第一次） */
    fun gradeShort(q: QuestionEntity, ok: Boolean) {
        val first = q.id !in selfGrades
        selfGrades[q.id] = ok
        val pending = saveJob
        app.appScope.launch {
            pending?.join() // 等成績先存好
            if (first) repo.recordAnswer(q.id, ok, MODE_EXAM)
            resultId?.let { repo.updateExam(resultEntity().copy(id = it)) }
        }
    }

    fun correctOf(item: ExamItem): Boolean? = item.correct ?: selfGrades[item.q.id]
    val correctCount: Int get() = items.count { correctOf(it) == true }
    val pendingShort: List<ExamItem> get() = items.filter { it.correct == null && it.q.id !in selfGrades }
    val score: Int get() = if (items.isEmpty()) 0 else Math.round(correctCount * 100f / items.size)

    private fun resultEntity() = ExamResultEntity(
        bankId = request?.bankId ?: -1,
        bankName = request?.bankName.orEmpty(),
        startedAt = startedAt,
        finishedAt = System.currentTimeMillis(),
        timeLimitSec = request?.timeLimitSec ?: 0,
        durationSec = elapsedSec,
        total = items.size,
        correct = correctCount,
        unanswered = items.count { !it.answered },
    )
}

fun formatClock(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
