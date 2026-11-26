package com.cramtool.app.ui.quiz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cramtool.app.CramApp
import com.cramtool.app.audio.Cue
import com.cramtool.app.QuizRequest
import com.cramtool.app.SessionStore
import com.cramtool.app.data.QuestionEntity
import com.cramtool.app.data.QuestionType
import com.cramtool.app.importer.Grader
import kotlinx.coroutines.launch

data class AnswerRecord(val question: QuestionEntity, val correct: Boolean)

class QuizViewModel(private val app: CramApp) : ViewModel() {
    private val repo = app.repository
    val request: QuizRequest? = SessionStore.quiz

    var loading by mutableStateOf(true); private set
    var questions by mutableStateOf<List<QuestionEntity>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set

    // 目前這題的作答狀態
    var selected by mutableStateOf(setOf<Int>()); private set
    var input by mutableStateOf(""); private set
    var revealed by mutableStateOf(false); private set
    /** null = 簡答題尚未自評 */
    var correct by mutableStateOf<Boolean?>(null); private set

    val records = mutableStateListOf<AnswerRecord>()
    var finished by mutableStateOf(false); private set
    /** 本次練習中被移出錯題本的題數 */
    var masteredCount by mutableIntStateOf(0); private set

    val starred = mutableStateMapOf<Long, Boolean>()
    val inWrongBook = mutableStateMapOf<Long, Boolean>()

    private var committed = false

    init {
        viewModelScope.launch {
            val ids = request?.questionIds.orEmpty()
            questions = repo.getQuestionsByIds(ids)
            questions.forEach {
                starred[it.id] = it.starred
                inWrongBook[it.id] = it.inWrongBook
            }
            loading = false
        }
    }

    val current: QuestionEntity? get() = questions.getOrNull(index)

    fun toggleOption(i: Int) {
        val q = current ?: return
        if (revealed) return
        selected = when (q.type) {
            QuestionType.MULTIPLE -> if (i in selected) selected - i else selected + i
            else -> setOf(i)
        }
    }

    fun updateInput(s: String) { if (!revealed) input = s }

    fun canSubmit(): Boolean {
        val q = current ?: return false
        return when (q.type) {
            QuestionType.FILL -> input.isNotBlank()
            QuestionType.SHORT -> true
            else -> selected.isNotEmpty()
        }
    }

    fun submit() {
        val q = current ?: return
        if (revealed) return
        correct = when (q.type) {
            QuestionType.FILL -> Grader.gradeFill(q, input)
            QuestionType.SHORT -> null
            else -> Grader.gradeChoice(q, selected)
        }
        revealed = true
        correct?.let { app.sounds.play(if (it) Cue.CORRECT else Cue.WRONG) }
    }

    /** 簡答自評，或填空題自動批改太嚴格時手動改判 */
    fun judge(isCorrect: Boolean) {
        if (!revealed || correct == isCorrect) return
        correct = isCorrect
        app.sounds.play(if (isCorrect) Cue.CORRECT else Cue.WRONG)
    }

    fun next() {
        commitCurrent()
        if (index >= questions.lastIndex) {
            finish()
        } else {
            index++
            resetAnswer()
        }
    }

    fun finishEarly() {
        commitCurrent()
        finish()
    }

    private fun finish() {
        finished = true
        if (records.isNotEmpty()) app.sounds.play(Cue.FINISH)
    }

    private fun resetAnswer() {
        selected = emptySet(); input = ""; revealed = false; correct = null; committed = false
    }

    private fun commitCurrent() {
        val q = current ?: return
        val c = correct
        if (!revealed || c == null || committed) return
        committed = true
        records += AnswerRecord(q, c)
        val wasWrong = inWrongBook[q.id] == true
        // 本地同步錯題本狀態（與 Repository.recordAnswer 規則一致）
        if (!c) inWrongBook[q.id] = true
        app.appScope.launch {
            repo.recordAnswer(q.id, c)
            val now = repo.getQuestionsByIds(listOf(q.id)).firstOrNull()
            if (now != null) {
                inWrongBook[q.id] = now.inWrongBook
                if (wasWrong && !now.inWrongBook) masteredCount++
            }
        }
    }

    fun toggleStar() {
        val q = current ?: return
        val v = !(starred[q.id] ?: false)
        starred[q.id] = v
        app.appScope.launch { repo.setStarred(q.id, v) }
    }

    fun toggleWrongBook() {
        val q = current ?: return
        val v = !(inWrongBook[q.id] ?: false)
        inWrongBook[q.id] = v
        app.appScope.launch { if (v) repo.addToWrongBook(q.id) else repo.removeFromWrongBook(q.id) }
    }

    override fun onCleared() {
        // 使用者直接離開時，已看過答案的那題仍要記錄
        if (!finished) commitCurrent()
    }
}
