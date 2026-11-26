package com.cramtool.app

import kotlinx.coroutines.flow.MutableStateFlow

/** 一次刷題的設定：要做哪些題目（已排序） */
data class QuizRequest(
    val title: String,
    val questionIds: List<Long>,
    /** 錯題複習模式：結果頁會顯示移出錯題本的題數 */
    val isReview: Boolean = false,
)

/** 畫面間傳遞較大資料用的簡單記憶體存放區 */
object SessionStore {
    var quiz: QuizRequest? = null
    /** 由分享／開啟檔案帶進來、等待匯入的文字 */
    val pendingImport = MutableStateFlow<String?>(null)
    /** 已判定為筆記、等待新增筆記畫面接收的文字 */
    val pendingNote = MutableStateFlow<String?>(null)

    /** 分享進來的文字看起來像題庫 JSON 嗎？否則當作筆記 */
    fun looksLikeQuestionBank(text: String): Boolean {
        if (runCatching { com.cramtool.app.importer.QuestionParser.parse(text) }.isSuccess) return true
        val t = text.trimStart()
        return (t.startsWith("{") || t.startsWith("[") || t.startsWith("```json")) && text.contains("\"question")
    }
}
